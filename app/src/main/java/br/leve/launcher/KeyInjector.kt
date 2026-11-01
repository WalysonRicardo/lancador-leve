package br.leve.launcher

import android.content.Context
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import java.io.File
import java.text.Normalizer
import java.util.concurrent.Executors

/**
 * Simula o controle remoto.
 *
 * Um app comum não pode apertar teclas por outros apps. O jeito sem root
 * é falar com o ADB do próprio projetor (127.0.0.1:5555), que roda com
 * permissão de "shell". Na primeira vez o projetor mostra
 * "Permitir depuração?": marque "Sempre permitir" e confirme no controle físico.
 *
 * Um único shell fica aberto para cada tecla sair rápido.
 */
object KeyInjector {

    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var dir: File
    private var dadb: Dadb? = null
    private var shell: AdbShellStream? = null

    @Volatile var connected = false
        private set
    @Volatile var lastError: String? = null
        private set

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

    fun text(t: String) {
        // O comando input só digita ASCII: tira acentos (manhã -> manha).
        // Espaço vira %s e aspas simples protegem o resto.
        val ascii = Normalizer.normalize(t, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .filter { it.code in 32..126 }
        val safe = ascii.replace("'", "'\\''").replace(" ", "%s")
        run("input text '$safe'")
    }

    fun launch(component: String) = run("am start -n $component")

    /** Tenta conectar já, para a pergunta de permissão aparecer na tela. */
    fun warmUp() = exec.execute {
        try { ensureShell(); connected = true; lastError = null }
        catch (e: Exception) { lastError = e.message; reset() }
    }

    fun close() = exec.execute { reset() }

    private fun ensureShell(): AdbShellStream {
        shell?.let { return it }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        val d = Dadb.create(Config.ADB_HOST, Config.ADB_PORT, AdbKeyPair.read(priv, pub))
        val s = d.openShell()
        // Lê e descarta a saída do shell para o buffer nunca encher.
        Thread({
            try { while (true) s.read() } catch (_: Exception) {}
        }, "adb-drain").apply { isDaemon = true }.start()
        dadb = d
        shell = s
        return s
    }

    private fun reset() {
        connected = false
        try { shell?.close() } catch (_: Exception) {}
        try { dadb?.close() } catch (_: Exception) {}
        shell = null
        dadb = null
    }
}
