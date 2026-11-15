package br.leve.launcher

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Servidor HTTP mínimo (sem bibliotecas) que entrega a página do controle
 * e recebe os comandos do celular (Controle, MouseTrack, Idioma/Legendas,
 * Navegação Rápida por Aplicativo e Gamepad Beta).
 */
class RemoteServer(private val ctx: Context, private val port: Int) {

    private val pool = Executors.newFixedThreadPool(6)
    @Volatile private var socket: ServerSocket? = null
    private val page: ByteArray by lazy { ctx.assets.open("remote.html").use { it.readBytes() } }

    private val keys = mapOf(
        // Navegação e sistema
        "up" to 19, "down" to 20, "left" to 21, "right" to 22, "ok" to 23,
        "back" to 4, "home" to 3, "menu" to 82, "enter" to 66,
        "volup" to 24, "voldown" to 25, "mute" to 164, "power" to 26,
        // Mídia e TV
        "play" to 85, "pause" to 127, "playpause" to 85,
        "rew" to 89, "ff" to 90, "next" to 87, "prev" to 88,
        "chup" to 166, "chdown" to 167, "info" to 165, "guide" to 172,
        "search" to 84, "captions" to 175, "audiotrack" to 222,
        // Foco e Zoom do projetor
        "focusup" to 307, "focusdown" to 308,
        "zoomin" to 130, "zoomout" to 131,
        // Controle de Videogame (Beta) - Keycodes oficiais Android Gamepad
        "btn_a" to 96, "btn_b" to 97, "btn_c" to 98,
        "btn_x" to 99, "btn_y" to 100, "btn_z" to 101,
        "btn_l1" to 102, "btn_r1" to 103, "btn_l2" to 104, "btn_r2" to 105,
        "btn_thumbl" to 106, "btn_thumbr" to 107,
        "btn_start" to 108, "btn_select" to 109, "btn_mode" to 110,
    )

    private val projectorTools = mapOf(
        "trapezio" to "com.softwinner.tcorrection/.projection.ProjectionSettingActivity",
        "projeccao" to "com.softwinner.tcorrection/.projection.ProjectionSettingActivity",
        "4cantos" to "com.softwinner.tcorrection/.projection.ManuelCorrectionActivity",
        "angulo" to "com.softwinner.tcorrection/.projection.AngleActivity",
        "zoom" to "com.softwinner.tcorrection/.projection.ZoomActivity",
    )

    fun start() {
        Thread({
            try {
                val ss = ServerSocket(port).also { socket = it }
                while (!ss.isClosed) {
                    val c = ss.accept()
                    pool.execute { handle(c) }
                }
            } catch (_: Exception) { /* parou */ }
        }, "remote-http").apply { isDaemon = true }.start()
    }

    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun handle(c: Socket) {
        c.use { s ->
            s.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val line = reader.readLine() ?: return
            while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }

            val target = line.split(" ").getOrNull(1) ?: "/"
            val path = target.substringBefore("?")
            val query = parseQuery(target.substringAfter("?", ""))
            val out = s.getOutputStream()

            when (path) {
                "/", "/index.html" -> send(out, 200, "text/html; charset=utf-8", page)
                "/key" -> {
                    val code = keys[query["c"]]
                    if (code == null) send(out, 400, "text/plain", "tecla desconhecida")
                    else { KeyInjector.key(code); send(out, 200, "text/plain", "ok") }
                }
                "/mouse" -> handleMouse(query, out)
                "/av" -> handleAudioSubtitles(query, out)
                "/nav" -> handleAppNav(query, out)
                "/text" -> {
                    val t = query["t"].orEmpty().take(200)
                    if (t.isNotEmpty()) KeyInjector.text(t)
                    if (query["enter"] == "1") KeyInjector.key(66)
                    send(out, 200, "text/plain", "ok")
                }
                "/launch" -> {
                    val comp = componentFor(query["app"].orEmpty())
                    if (comp == null) send(out, 404, "text/plain", "app não instalado")
                    else { KeyInjector.launch(comp); send(out, 200, "text/plain", "ok") }
                }
                "/apps" -> send(out, 200, "application/json; charset=utf-8", appsJson())
                "/order" -> {
                    if (query["reset"] == "1") HomeStore.reset(ctx)
                    else HomeStore.save(ctx, query["ids"].orEmpty().split(","))
                    send(out, 200, "text/plain", "ok")
                }
                "/status" -> {
                    val err = (KeyInjector.lastError ?: "").replace("\\", "\\\\").replace("\"", "'")
                    val (activeApp, activePkg) = KeyInjector.activeApp()
                    send(
                        out, 200, "application/json; charset=utf-8",
                        """{"adb":${KeyInjector.connected},"erro":"$err","activeApp":${q(activeApp)},"activePkg":${q(activePkg)},"cx":${CursorOverlay.x},"cy":${CursorOverlay.y}}"""
                    )
                }
                else -> send(out, 404, "text/plain", "não encontrado")
            }
        }
    }

    private fun handleMouse(query: Map<String, String>, out: OutputStream) {
        when (query["op"]) {
            "move" -> {
                val dx = query["dx"]?.toFloatOrNull() ?: 0f
                val dy = query["dy"]?.toFloatOrNull() ?: 0f
                CursorOverlay.moveBy(dx, dy)
                send(out, 200, "application/json", """{"x":${CursorOverlay.x},"y":${CursorOverlay.y}}""")
            }
            "set" -> {
                val nx = query["x"]?.toIntOrNull() ?: CursorOverlay.x
                val ny = query["y"]?.toIntOrNull() ?: CursorOverlay.y
                CursorOverlay.moveTo(nx, ny)
                send(out, 200, "application/json", """{"x":${CursorOverlay.x},"y":${CursorOverlay.y}}""")
            }
            "click" -> {
                val tx = query["x"]?.toIntOrNull() ?: CursorOverlay.x
                val ty = query["y"]?.toIntOrNull() ?: CursorOverlay.y
                if (query["x"] != null && query["y"] != null) CursorOverlay.moveTo(tx, ty)
                CursorOverlay.pulseClick()
                KeyInjector.tap(tx, ty)
                send(out, 200, "text/plain", "ok")
            }
            "longclick" -> {
                val tx = query["x"]?.toIntOrNull() ?: CursorOverlay.x
                val ty = query["y"]?.toIntOrNull() ?: CursorOverlay.y
                CursorOverlay.pulseClick()
                KeyInjector.longPress(tx, ty)
                send(out, 200, "text/plain", "ok")
            }
            "rclick" -> {
                KeyInjector.key(4)
                send(out, 200, "text/plain", "ok")
            }
            "hide" -> {
                CursorOverlay.hide()
                send(out, 200, "text/plain", "ok")
            }
            "scroll" -> {
                val dx = (query["dx"]?.toFloatOrNull() ?: 0f).roundToInt()
                val dy = (query["dy"]?.toFloatOrNull() ?: 0f).roundToInt()
                val sx = (query["x"]?.toIntOrNull() ?: CursorOverlay.x).coerceIn(180, 1100)
                val sy = (query["y"]?.toIntOrNull() ?: CursorOverlay.y).coerceIn(140, 580)
                val ex = (sx + dx).coerceIn(40, 1240)
                val ey = (sy + dy).coerceIn(40, 680)
                KeyInjector.swipe(sx, sy, ex, ey, 210)
                send(out, 200, "text/plain", "ok")
            }
            "dir" -> {
                val d = query["d"].orEmpty()
                val mode = query["mode"].orEmpty() // "cursor" ou "scroll"
                val step = (query["step"]?.toFloatOrNull() ?: 52f)
                if (mode == "cursor") {
                    when (d) {
                        "up" -> CursorOverlay.moveBy(0f, -step)
                        "down" -> CursorOverlay.moveBy(0f, step)
                        "left" -> CursorOverlay.moveBy(-step, 0f)
                        "right" -> CursorOverlay.moveBy(step, 0f)
                        "ok" -> {
                            CursorOverlay.pulseClick()
                            KeyInjector.tap(CursorOverlay.x, CursorOverlay.y)
                        }
                    }
                } else {
                    // Modo Rolagem / Direção Mouse para apps touch como Netflix:
                    // Cima/Baixo rola o catálogo; Esquerda/Direita rola a fileira na altura do cursor
                    val cy = CursorOverlay.y.coerceIn(180, 580)
                    when (d) {
                        "up" -> KeyInjector.swipe(640, 210, 640, 560, 230)
                        "down" -> KeyInjector.swipe(640, 560, 640, 190, 230)
                        "left" -> KeyInjector.swipe(280, cy, 1000, cy, 230)
                        "right" -> KeyInjector.swipe(1000, cy, 280, cy, 230)
                        "ok" -> {
                            CursorOverlay.pulseClick()
                            KeyInjector.tap(CursorOverlay.x, CursorOverlay.y)
                        }
                    }
                }
                send(out, 200, "application/json", """{"x":${CursorOverlay.x},"y":${CursorOverlay.y}}""")
            }
            else -> send(out, 400, "text/plain", "operação de mouse desconhecida")
        }
    }

    private fun handleAudioSubtitles(query: Map<String, String>, out: OutputStream) {
        when (query["op"]) {
            "scan" -> {
                val app = query["app"].orEmpty()
                val open = query["open"] == "1"
                val json = KeyInjector.scanAudioSubtitles(app, open)
                send(out, 200, "application/json; charset=utf-8", json)
            }
            "pick" -> {
                val cmd = query["cmd"].orEmpty()
                if (cmd.isNotEmpty()) {
                    runPrimeAvPreset(cmd)
                } else {
                    val x = query["x"]?.toIntOrNull() ?: return send(out, 400, "text/plain", "x inválido")
                    val y = query["y"]?.toIntOrNull() ?: return send(out, 400, "text/plain", "y inválido")
                    if (query["apply"] == "1") {
                        KeyInjector.run("input tap $x $y; sleep 0.35; input tap 1200 664")
                    } else {
                        KeyInjector.tap(x, y)
                    }
                }
                send(out, 200, "text/plain", "ok")
            }
            "preset" -> {
                when (val p = query["p"].orEmpty()) {
                    // Netflix Presets Mapeados (1280x720)
                    "nf_open" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 866 664")
                    "nf_dub_off" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 866 664; sleep 0.85; input tap 357 109; sleep 0.25; input tap 923 109; sleep 0.25; input tap 1200 664")
                    "nf_orig_pt" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 866 664; sleep 0.85; input tap 357 178; sleep 0.25; input tap 923 178; sleep 0.25; input tap 1200 664")
                    "nf_sub_pt" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 866 664; sleep 0.85; input tap 923 178; sleep 0.25; input tap 1200 664")
                    "nf_sub_off" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 866 664; sleep 0.85; input tap 923 109; sleep 0.25; input tap 1200 664")
                    "nf_apply" -> KeyInjector.tap(1200, 664)
                    "nf_cancel" -> KeyInjector.tap(1084, 664)
                    // Prime Video Presets Mapeados
                    else -> runPrimeAvPreset(p)
                }
                send(out, 200, "text/plain", "ok")
            }
            else -> send(out, 400, "text/plain", "operação av desconhecida")
        }
    }

    private fun runPrimeAvPreset(p: String) {
        when (p) {
            "pv_open" -> KeyInjector.run("input keyevent 19; sleep 0.4; input keyevent 19; sleep 0.3; input keyevent 23")
            "pv_sub_off" -> KeyInjector.run("input keyevent 19; sleep 0.4; input keyevent 19; sleep 0.3; input keyevent 23; sleep 0.5; input keyevent 19; sleep 0.2; input keyevent 19; sleep 0.2; input keyevent 23")
            "pv_sub_pt" -> KeyInjector.run("input keyevent 19; sleep 0.4; input keyevent 19; sleep 0.3; input keyevent 23; sleep 0.5; input keyevent 20; sleep 0.2; input keyevent 23")
            "pv_sub_next" -> KeyInjector.run("input keyevent 20; sleep 0.2; input keyevent 23")
            "pv_audio_pt" -> KeyInjector.run("input keyevent 19; sleep 0.4; input keyevent 19; sleep 0.3; input keyevent 23; sleep 0.5; input keyevent 22; sleep 0.3; input keyevent 19; sleep 0.2; input keyevent 23")
            "pv_audio_orig" -> KeyInjector.run("input keyevent 19; sleep 0.4; input keyevent 19; sleep 0.3; input keyevent 23; sleep 0.5; input keyevent 22; sleep 0.3; input keyevent 20; sleep 0.2; input keyevent 23")
            "pv_audio_next" -> KeyInjector.run("input keyevent 22; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
            "pv_tab_sub" -> KeyInjector.key(21)
            "pv_tab_audio" -> KeyInjector.key(22)
        }
    }

    /**
     * Atalhos de seções específicas mapeadas para cada aplicativo instalado.
     */
    private fun handleAppNav(query: Map<String, String>, out: OutputStream) {
        val app = query["app"].orEmpty()
        val sec = query["sec"].orEmpty()
        when (app) {
            "netflix" -> when (sec) {
                "home" -> KeyInjector.tap(353, 682)
                "series" -> KeyInjector.run("input tap 353 682; sleep 0.45; input tap 66 101")
                "movies" -> KeyInjector.run("input tap 353 682; sleep 0.45; input tap 173 101")
                "categories" -> KeyInjector.run("input tap 353 682; sleep 0.45; input tap 312 101")
                "newhot" -> KeyInjector.tap(735, 682)
                "mynf" -> KeyInjector.tap(926, 682)
                "games" -> KeyInjector.tap(544, 682)
                "search" -> KeyInjector.tap(1248, 35)
                "profiles" -> KeyInjector.tap(1215, 682)
                "prof_walyson" -> KeyInjector.tap(406, 254)
                "prof_lala" -> KeyInjector.tap(638, 254)
                "prof_micaila" -> KeyInjector.tap(870, 254)
                "prof_geral" -> KeyInjector.tap(406, 522)
                "prof_kids" -> KeyInjector.tap(638, 522)
                "play_hero" -> KeyInjector.tap(155, 634)
                "mylist_hero" -> KeyInjector.tap(389, 634)
                "skip_intro" -> KeyInjector.tap(1179, 127)
                "next_ep" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 1111 664")
                "episodes" -> KeyInjector.run("input tap 640 360; sleep 0.4; input tap 620 664")
                "rew10" -> KeyInjector.run("input tap 640 360; sleep 0.35; input tap 272 351")
                "ff10" -> KeyInjector.run("input tap 640 360; sleep 0.35; input tap 1008 351")
                "playpause_nf" -> KeyInjector.run("input tap 640 360; sleep 0.35; input tap 640 351")
            }
            "prime" -> when (sec) {
                "home" -> KeyInjector.tap(118, 70)
                "movies" -> KeyInjector.tap(198, 70)
                "series" -> KeyInjector.tap(252, 70)
                "sports" -> KeyInjector.tap(308, 70)
                "livetv" -> KeyInjector.tap(382, 70)
                "prime_included" -> KeyInjector.tap(455, 70)
                "subscriptions" -> KeyInjector.tap(535, 70)
                "search" -> KeyInjector.run("input keyevent 21; sleep 0.25; input tap 36 445")
                "categories" -> KeyInjector.run("input keyevent 21; sleep 0.25; input tap 36 498")
                "mystuff" -> KeyInjector.run("input keyevent 21; sleep 0.25; input tap 36 550")
                "store" -> KeyInjector.run("input keyevent 21; sleep 0.25; input tap 36 602")
                "sidebar" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21")
                "prof_walyson" -> KeyInjector.tap(120, 120)
                "prof_2" -> KeyInjector.tap(120, 270)
                "prof_lays" -> KeyInjector.tap(120, 390)
                "prof_li" -> KeyInjector.tap(120, 510)
                "prof_kids" -> KeyInjector.tap(120, 630)
            }
            "youtube" -> when (sec) {
                "search" -> KeyInjector.key(84)
                "sidebar" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.2; input keyevent 21")
                "home" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 23")
                "subs" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
                "library" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "cc" -> KeyInjector.key(175)
                "skip_ad" -> KeyInjector.run("input tap 1120 560; input keyevent 23")
                "rew10" -> KeyInjector.key(21)
                "ff10" -> KeyInjector.key(22)
            }
            "globoplay" -> when (sec) {
                "sidebar" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.2; input keyevent 21")
                "live" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
                "home" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 19; sleep 0.2; input keyevent 23")
                "novelas" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "series" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "movies" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "search" -> KeyInjector.key(84)
                "av_menu" -> KeyInjector.run("input keyevent 19; sleep 0.35; input keyevent 19; sleep 0.25; input keyevent 23")
            }
            "sbt" -> when (sec) {
                "menu" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21")
                "live" -> KeyInjector.run("input keyevent 21; sleep 0.25; input keyevent 23")
                "channels" -> KeyInjector.run("input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
                "catalog" -> KeyInjector.run("input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "search" -> KeyInjector.key(84)
                "back_ui" -> KeyInjector.key(4)
            }
            "pluto" -> when (sec) {
                "guide" -> KeyInjector.key(21)
                "chup" -> KeyInjector.key(19)
                "chdown" -> KeyInjector.key(20)
                "livetv" -> KeyInjector.run("input keyevent 21; sleep 0.25; input keyevent 21; sleep 0.25; input keyevent 23")
                "ondemand" -> KeyInjector.run("input keyevent 21; sleep 0.25; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
                "search" -> KeyInjector.key(84)
                "av_menu" -> KeyInjector.run("input keyevent 23; sleep 0.35; input keyevent 20; sleep 0.2; input keyevent 23")
            }
            "stremio" -> when (sec) {
                "sidebar" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.2; input keyevent 21")
                "board" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 23")
                "discover" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
                "library" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "addons" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.15; input keyevent 20; sleep 0.2; input keyevent 23")
                "search" -> KeyInjector.key(84)
                "subs" -> KeyInjector.run("input keyevent 20; sleep 0.3; input keyevent 21; sleep 0.2; input keyevent 23")
                "audio" -> KeyInjector.run("input keyevent 20; sleep 0.3; input keyevent 22; sleep 0.2; input keyevent 23")
            }
            "pendrive" -> when (sec) {
                "folders" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 20; sleep 0.2; input keyevent 23")
                "movies" -> KeyInjector.run("input keyevent 21; sleep 0.2; input keyevent 21; sleep 0.25; input keyevent 19; sleep 0.2; input keyevent 23")
                "subs" -> KeyInjector.run("input keyevent 20; sleep 0.3; input keyevent 22; sleep 0.2; input keyevent 23")
                "audio" -> KeyInjector.run("input keyevent 20; sleep 0.3; input keyevent 22; sleep 0.15; input keyevent 22; sleep 0.2; input keyevent 23")
                "aspect" -> KeyInjector.key(82)
                "justplayer" -> KeyInjector.launch("com.brouken.player/.PlayerActivity")
                "novaplayer" -> KeyInjector.launch("org.courville.nova/com.archos.mediacenter.video.browser.MainActivityLeanback")
            }
        }
        send(out, 200, "text/plain", "ok")
    }

    private fun componentFor(id: String): String? {
        projectorTools[id]?.let { return it }
        val pm = ctx.packageManager
        val item = HomeStore.resolve(ctx, id) { AppRepo.launchables(pm, ctx.packageName) } ?: return null
        val pkg = item.pkg ?: return null
        return AppRepo.launchIntent(pm, pkg)?.component?.flattenToString()
    }

    private fun appsJson(): String {
        val home = HomeStore.items(ctx)
        val homeJson = home.joinToString(",") {
            """{"id":${q(it.id)},"label":${q(it.label)},"installed":${it.pkg != null}}"""
        }
        val othersJson = HomeStore.others(ctx, home).joinToString(",") { (id, label) ->
            """{"id":${q(id)},"label":${q(label)}}"""
        }
        return """{"max":${HomeStore.MAX_ITEMS},"home":[$homeJson],"others":[$othersJson]}"""
    }

    private fun q(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c < ' ' -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun send(out: OutputStream, code: Int, type: String, body: String) =
        send(out, code, type, body.toByteArray(Charsets.UTF_8))

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "OK" }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }
}
