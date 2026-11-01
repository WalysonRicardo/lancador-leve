package br.leve.launcher

import android.content.Context
import android.content.SharedPreferences

/** Um bloco da home: um favorito do Config ou qualquer app ("pkg:com.exemplo"). */
data class HomeItem(
    val id: String,
    val label: String,
    val mono: String,
    val tint: Int,
    val hint: String,
    val pkg: String?,       // null = favorito ainda não instalado
)

/**
 * Guarda quais apps aparecem na home e em que ordem.
 * O celular muda a ordem pelo controle; a home se redesenha na hora.
 */
object HomeStore {
    const val MAX_ITEMS = 12
    private const val PREFS = "home"
    private const val KEY = "order"
    private const val PKG = "pkg:"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun order(ctx: Context): List<String> =
        prefs(ctx).getString(KEY, null)?.split(",")?.filter { it.isNotBlank() }
            ?: Config.favorites.map { it.id }

    fun save(ctx: Context, ids: List<String>) {
        val clean = ids.map { it.trim() }.filter { valid(it) }.distinct().take(MAX_ITEMS)
        prefs(ctx).edit().putString(KEY, clean.joinToString(",")).apply()
    }

    fun reset(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()

    private fun valid(id: String) =
        Config.favorites.any { it.id == id } ||
            (id.startsWith(PKG) && id.length > PKG.length && id.none { it == ',' || it.isWhitespace() })

    fun idFor(pkg: String) = PKG + pkg

    /** Resolve um id. Favorito não instalado volta com pkg = null; app avulso desinstalado volta null. */
    fun resolve(ctx: Context, id: String, all: () -> List<AppEntry>): HomeItem? {
        val pm = ctx.packageManager
        Config.favorites.firstOrNull { it.id == id }?.let { fav ->
            return HomeItem(fav.id, fav.label, fav.mono, fav.tint, fav.hint, AppRepo.resolve(pm, fav, all))
        }
        if (!id.startsWith(PKG)) return null
        val pkg = id.removePrefix(PKG)
        if (AppRepo.launchIntent(pm, pkg) == null) return null
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        return HomeItem(id, label, label.take(2), 0xFF1E2430.toInt(), "OK para abrir", pkg)
    }

    fun items(ctx: Context): List<HomeItem> {
        var cache: List<AppEntry>? = null
        val all = { cache ?: AppRepo.launchables(ctx.packageManager, ctx.packageName).also { cache = it } }
        return order(ctx).mapNotNull { resolve(ctx, it, all) }
    }

    /** Apps instalados que ainda não estão na home (para o celular adicionar). */
    fun others(ctx: Context, home: List<HomeItem>): List<Pair<String, String>> {
        val onHome = home.mapNotNull { it.pkg }.toSet()
        val homeIds = home.map { it.id }.toSet()
        val missingFavs = Config.favorites.filter { it.id !in homeIds }
        val all = AppRepo.launchables(ctx.packageManager, ctx.packageName)
        val favPkgs = missingFavs.mapNotNull { f -> AppRepo.resolve(ctx.packageManager, f) { all } }.toSet()
        return missingFavs.map { it.id to it.label } +
            all.filter { it.pkg !in onHome && it.pkg !in favPkgs }.map { idFor(it.pkg) to it.label }
    }
}
