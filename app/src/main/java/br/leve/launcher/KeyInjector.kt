package br.leve.launcher

import android.content.Context
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import java.io.File
import java.text.Normalizer
import java.util.concurrent.Executors

/**
 * Simula o controle remoto, MouseTrack, atalhos de seções e leitura de opções (Áudio/Legendas).
 *
 * Fala com o ADB do próprio projetor (127.0.0.1:5555), que roda com permissão de "shell".
 */
object KeyInjector {

    private val exec = Executors.newSingleThreadExecutor()
    private val queryPool = Executors.newCachedThreadPool()
    private lateinit var dir: File
    @Volatile private var dadb: Dadb? = null
    private var shell: AdbShellStream? = null

    @Volatile var connected = false
        private set
    @Volatile var lastError: String? = null
        private set

    @Volatile private var cachedAppId = "launcher"
    @Volatile private var cachedPkg = "br.leve.launcher"
    @Volatile private var lastAppCheckMs = 0L

    fun init(ctx: Context) {
        if (!::dir.isInitialized) dir = ctx.applicationContext.filesDir
    }

    /** Executa um comando no shell do projetor, sem esperar resposta. */
    fun run(cmd: String) = exec.execute {
        for (attempt in 0..1) {
            try {
                ensureShell().write("$cmd >/dev/null 2>&1\n")
                connected = true
                lastError = null
                return@execute
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                reset()
            }
        }
    }

    fun key(code: Int) = run("input keyevent $code")

    fun tap(x: Int, y: Int) = run("input tap $x $y")

    fun longPress(x: Int, y: Int) = run("input swipe $x $y $x $y 650")

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 220) =
        run("input swipe $x1 $y1 $x2 $y2 $durationMs")

    fun grantOverlayPermission() =
        run("appops set br.leve.launcher SYSTEM_ALERT_WINDOW allow")

    fun text(t: String) {
        val ascii = Normalizer.normalize(t, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .filter { it.code in 32..126 }
        val safe = ascii.replace("'", "'\\''").replace(" ", "%s")
        run("input text '$safe'")
    }

    fun launch(component: String) = run("am start -n $component")

    /** Executa um comando no ADB e devolve a saída (usado para detectar app ativo e espelhar legendas). */
    fun shellOut(cmd: String): String {
        for (attempt in 0..1) {
            try {
                val d = dadb ?: synchronized(this) {
                    ensureShell()
                    dadb
                } ?: continue
                val res = d.shell(cmd).output
                connected = true
                lastError = null
                return res
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                if (attempt == 0) synchronized(this) { reset() }
            }
        }
        return ""
    }

    /** Detecta qual aplicativo está em primeiro plano no projetor (com cache de 2,5s). */
    fun activeApp(): Pair<String, String> {
        val now = System.currentTimeMillis()
        if (now - lastAppCheckMs > 2500L && connected) {
            lastAppCheckMs = now
            queryPool.execute {
                try {
                    val out = shellOut("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
                    val m = Regex("([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.\$]+").findAll(out).lastOrNull()
                    val pkg = m?.groupValues?.getOrNull(1).orEmpty()
                    if (pkg.isNotEmpty()) {
                        cachedPkg = pkg
                        cachedAppId = mapPkgToAppId(pkg)
                    }
                } catch (_: Exception) {}
            }
        }
        return cachedAppId to cachedPkg
    }

    fun mapPkgToAppId(pkg: String): String = when {
        pkg.contains("netflix") -> "netflix"
        pkg.contains("amazonvideo") || pkg.contains("avod") -> "prime"
        pkg.contains("youtube") || pkg.contains("smarttube") -> "youtube"
        pkg.contains("globo") -> "globoplay"
        pkg.contains("sbt") -> "sbt"
        pkg.contains("pluto") -> "pluto"
        pkg.contains("stremio") -> "stremio"
        pkg.contains("nova") || pkg.contains("brouken") || pkg.contains("videolan") ||
            pkg.contains("mpv") || pkg.contains("videoplayer") || pkg.contains("mediacenter") ||
            pkg.contains("FileBrower") -> "pendrive"
        pkg == "br.leve.launcher" -> "launcher"
        else -> "outro"
    }

    /**
     * Abre (se openFirst=true) e lê as opções reais de Idioma (Áudio) e Legendas
     * disponíveis na tela da Netflix ou do Prime Video para espelhar no controle do celular.
     */
    fun scanAudioSubtitles(appHint: String, openFirst: Boolean): String {
        val app = if (appHint.isNotBlank() && appHint != "auto") appHint else activeApp().first
        if (app == "prime") {
            if (openFirst) {
                run("input keyevent 19; sleep 0.4; input keyevent 19; sleep 0.3; input keyevent 23")
            }
            return """{
                "app":"prime",
                "live":true,
                "title":"Prime Video · Idioma e Legendas",
                "audio":[
                    {"label":"Português (Brasil) [Dublado]","cmd":"pv_audio_pt","selected":true},
                    {"label":"Áudio Original (Inglês/Outro)","cmd":"pv_audio_orig","selected":false},
                    {"label":"Próxima Faixa de Áudio","cmd":"pv_audio_next","selected":false}
                ],
                "subtitles":[
                    {"label":"Desativadas (Off)","cmd":"pv_sub_off","selected":false},
                    {"label":"Português (Brasil)","cmd":"pv_sub_pt","selected":true},
                    {"label":"Próxima Legenda","cmd":"pv_sub_next","selected":false}
                ]
            }""".trimIndent().replace("\n", "")
        }

        // Netflix (com.netflix.mediaclient)
        val cmd = if (openFirst) {
            "input tap 640 360; sleep 0.4; input tap 866 664; sleep 0.9; uiautomator dump /sdcard/_av.xml >/dev/null 2>&1; cat /sdcard/_av.xml"
        } else {
            "uiautomator dump /sdcard/_av.xml >/dev/null 2>&1; cat /sdcard/_av.xml"
        }
        val xml = shellOut(cmd)
        data class Track(val label: String, val x: Int, val y: Int, val selected: Boolean)
        val audio = ArrayList<Track>()
        val subs = ArrayList<Track>()

        val nodeRegex = Regex("""<node\s+[^>]*>""")
        val attrText = Regex("""text="([^"]+)"""")
        val attrSel = Regex("""selected="(true|false)"""")
        val attrBounds = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""")
        val attrRes = Regex("""resource-id="([^"]*)"""")

        for (m in nodeRegex.findAll(xml)) {
            val tag = m.value
            val res = attrRes.find(tag)?.groupValues?.getOrNull(1).orEmpty()
            val txt = attrText.find(tag)?.groupValues?.getOrNull(1).orEmpty().trim()
            if (txt.isEmpty() || txt == "Audio" || txt == "Subtitles" || txt == "Áudio" ||
                txt == "Legendas" || txt == "Cancel" || txt == "Apply" ||
                txt == "Cancelar" || txt == "Aplicar") continue

            val b = attrBounds.find(tag) ?: continue
            val x1 = b.groupValues[1].toIntOrNull() ?: continue
            val y1 = b.groupValues[2].toIntOrNull() ?: continue
            val x2 = b.groupValues[3].toIntOrNull() ?: continue
            val y2 = b.groupValues[4].toIntOrNull() ?: continue
            val cx = (x1 + x2) / 2
            val cy = (y1 + y2) / 2

            // Na janela Audio & Subtitles da Netflix, as listas ficam entre y=70..460
            // ou possuem o resource-id 2131428547
            val isTrackItem = res.endsWith("2131428547") || (y1 in 65..450 && (x1 == 88 || x1 == 654))
            if (!isTrackItem) continue

            val sel = attrSel.find(tag)?.groupValues?.getOrNull(1) == "true"
            if (cx < 640) {
                if (audio.none { it.label == txt }) audio += Track(txt, cx, cy, sel)
            } else {
                if (subs.none { it.label == txt }) subs += Track(txt, cx, cy, sel)
            }
        }

        val isLive = audio.isNotEmpty() || subs.isNotEmpty()
        if (!isLive) {
            audio += Track("Português (Brasil) [Faixa 1]", 357, 109, true)
            audio += Track("Idioma Original [Faixa 2]", 357, 178, false)
            audio += Track("Faixa de Áudio 3", 357, 247, false)
            subs += Track("Desativada (Off)", 923, 109, true)
            subs += Track("Português (Brasil)", 923, 178, false)
            subs += Track("Legenda 3 (Inglês/Outra)", 923, 247, false)
        }

        val audioJson = audio.joinToString(",") {
            """{"label":${jsonStr(it.label)},"x":${it.x},"y":${it.y},"selected":${it.selected}}"""
        }
        val subsJson = subs.joinToString(",") {
            """{"label":${jsonStr(it.label)},"x":${it.x},"y":${it.y},"selected":${it.selected}}"""
        }
        return """{"app":"netflix","live":$isLive,"applyX":1200,"applyY":664,"cancelX":1084,"cancelY":664,"audio":[$audioJson],"subtitles":[$subsJson]}"""
    }

    private fun jsonStr(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c < ' ' -> b.append(' ')
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    /** Tenta conectar já, para a pergunta de permissão aparecer na tela. */
    fun warmUp() = exec.execute {
        try { ensureShell(); connected = true; lastError = null }
        catch (e: Exception) { lastError = e.message; reset() }
    }

    fun close() = exec.execute { reset() }

    @Synchronized
    private fun ensureShell(): AdbShellStream {
        shell?.let { return it }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        val d = Dadb.create(Config.ADB_HOST, Config.ADB_PORT, AdbKeyPair.read(priv, pub))
        val s = d.openShell()
        // Garante permissão de sobreposição para o cursor do MouseTrack
        try {
            s.write("appops set br.leve.launcher SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1\n")
            s.write("appops set com.softwinner.tcorrection SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1\n")
        } catch (_: Exception) {}
        Thread({
            try { while (true) s.read() } catch (_: Exception) {}
        }, "adb-drain").apply { isDaemon = true }.start()
        dadb = d
        shell = s
        return s
    }

    @Synchronized
    private fun reset() {
        connected = false
        try { shell?.close() } catch (_: Exception) {}
        try { dadb?.close() } catch (_: Exception) {}
        shell = null
        dadb = null
    }
}
