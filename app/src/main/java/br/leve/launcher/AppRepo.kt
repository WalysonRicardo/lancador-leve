package br.leve.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val label: String, val pkg: String, val component: ComponentName)

object AppRepo {

    /** Todos os apps abríveis (de TV e de celular), sem repetir pacote. */
    fun launchables(pm: PackageManager, selfPkg: String): List<AppEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == selfPkg || !seen.add(ai.packageName)) continue
                out += AppEntry(
                    ri.loadLabel(pm).toString(),
                    ai.packageName,
                    ComponentName(ai.packageName, ai.name),
                )
            }
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    /** Prefere a tela de TV (leanback), componente explícito (pkg/cls) ou cai para a de celular. */
    fun launchIntent(pm: PackageManager, pkg: String): Intent? {
        if ('/' in pkg) {
            val cn = ComponentName.unflattenFromString(pkg) ?: return null
            val i = Intent().setComponent(cn)
            if (pm.resolveActivity(i, 0) != null) {
                return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            return null
        }
        return (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun firstInstalled(pm: PackageManager, pkgs: List<String>): String? =
        pkgs.firstOrNull { launchIntent(pm, it) != null }

    /** Resolve o pacote de um favorito: por nome de pacote ou por palavra no nome do app. */
    fun resolve(pm: PackageManager, fav: Favorite, all: () -> List<AppEntry>): String? {
        firstInstalled(pm, fav.packages)?.let { return it }
        if (fav.keywords.isEmpty()) return null
        val apps = all()
        for (k in fav.keywords) {
            apps.firstOrNull { it.label.lowercase().contains(k) }?.let { return it.pkg }
        }
        return null
    }
}
