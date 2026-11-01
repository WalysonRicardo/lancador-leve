package br.leve.launcher

/**
 * Tudo que você vai querer ajustar fica aqui.
 *
 * Cada favorito tenta os pacotes na ordem. Se nenhum estiver instalado,
 * procura um app cujo nome contenha uma das palavras-chave. Assim a home
 * funciona com a versão TV ou com a versão celular do app, a que existir.
 */
data class Favorite(
    val id: String,
    val label: String,
    val mono: String,
    val tint: Int,
    val hint: String,
    val packages: List<String>,
    val keywords: List<String> = emptyList(),
)

object Config {
    const val REMOTE_PORT = 8080
    const val ADB_HOST = "127.0.0.1"
    const val ADB_PORT = 5555

    val favorites = listOf(
        Favorite(
            "netflix", "Netflix", "N", 0xFF7A0F14.toInt(),
            "OK para abrir",
            // ninja = versão TV; mediaclient = versão celular
            listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            listOf("netflix"),
        ),
        Favorite(
            "youtube", "YouTube", "YT", 0xFF6B1220.toInt(),
            "OK para abrir",
            // SmartTube primeiro (mais leve, feito para TV), depois YouTube TV e o de celular
            listOf(
                "com.liskovsoft.smarttubetv.beta",
                "com.liskovsoft.smarttubetv",
                "com.google.android.youtube.tv",
                "com.google.android.youtube",
            ),
            listOf("smarttube", "youtube"),
        ),
        Favorite(
            "pluto", "Pluto TV", "P", 0xFF5A4A0E.toInt(),
            "Canais ao vivo gratuitos",
            listOf("tv.pluto.android"),
            listOf("pluto"),
        ),
        Favorite(
            "globoplay", "Globoplay", "G", 0xFF6A1430.toInt(),
            "Novelas, jornalismo e Globo ao vivo",
            listOf("com.globo.globotv"),
            listOf("globoplay", "globo"),
        ),
        Favorite(
            "sbt", "SBT+", "S", 0xFF123A6B.toInt(),
            "Programação do SBT e ao vivo",
            emptyList(), // pacote varia: acha pelo nome
            listOf("sbt"),
        ),
        Favorite(
            "stremio", "Stremio", "St", 0xFF3B2366.toInt(),
            "Filmes e séries por add-ons",
            listOf("com.stremio.one"),
            listOf("stremio"),
        ),
    )

    /**
     * Apps do fabricante para os atalhos. Os nomes de pacote mudam de
     * firmware para firmware: rode `otimizacao/listar-pacotes` e preencha.
     * Lista vazia = abre as Configurações do Android.
     */
    val hdmiPackages = listOf<String>()          // ex.: app "Fonte"/"Source" do fabricante
    val focusPackages = listOf<String>()         // ex.: app de foco/trapézio do fabricante
    val mirrorPackages = listOf("com.ecloud.eshare.server") // EShare costuma vir nesses projetores
}
