package br.leve.launcher

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Servidor HTTP mínimo (sem bibliotecas) que entrega a página do controle
 * e recebe os comandos do celular.
 *
 *   GET /                 página do controle
 *   GET /key?c=up         tecla (up, down, left, right, ok, back, home, menu,
 *                         volup, voldown, mute, play, power)
 *   GET /text?t=ola       digita texto no campo em foco
 *   GET /launch?app=netflix   abre um app da home (id do favorito ou "pkg:com.x")
 *   GET /apps             JSON com a home (em ordem) e os outros apps instalados
 *   GET /order?ids=a,b,c  salva a nova ordem da home (a home redesenha na hora)
 *   GET /order?reset=1    volta a ordem padrão
 *   GET /status           {"adb":true|false,"erro":"..."}
 */
class RemoteServer(private val ctx: Context, private val port: Int) {

    private val pool = Executors.newFixedThreadPool(3)
    @Volatile private var socket: ServerSocket? = null
    private val page: ByteArray by lazy { ctx.assets.open("remote.html").use { it.readBytes() } }

    private val keys = mapOf(
        "up" to 19, "down" to 20, "left" to 21, "right" to 22, "ok" to 23,
        "back" to 4, "home" to 3, "menu" to 82,
        "volup" to 24, "voldown" to 25, "mute" to 164,
        "play" to 85, "power" to 26, "enter" to 66,
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
            s.soTimeout = 5000
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
                "/text" -> {
                    val t = query["t"].orEmpty().take(200)
                    if (t.isNotEmpty()) KeyInjector.text(t)
                    if (query["enter"] == "1") KeyInjector.key(66) // confirma a busca
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
                    send(out, 200, "application/json", """{"adb":${KeyInjector.connected},"erro":"$err"}""")
                }
                else -> send(out, 404, "text/plain", "não encontrado")
            }
        }
    }

    private fun componentFor(id: String): String? {
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

    /** String JSON com escape. */
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
