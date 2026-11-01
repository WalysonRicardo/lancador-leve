package br.leve.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

class AllAppsActivity : Activity() {

    private val icons = HashMap<String, Drawable?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_apps)
        val apps = AppRepo.launchables(packageManager, packageName)
        val grid = findViewById<GridView>(R.id.grid)

        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
                val v = convert ?: layoutInflater.inflate(R.layout.item_app, parent, false)
                val app = apps[p]
                v.findViewById<TextView>(R.id.label).text = app.label
                val icon = icons.getOrPut(app.pkg) {
                    try { packageManager.getApplicationIcon(app.pkg) } catch (e: Exception) { null }
                }
                v.findViewById<ImageView>(R.id.icon).setImageDrawable(icon)
                return v
            }
        }

        grid.setOnItemClickListener { _, _, p, _ ->
            AppRepo.launchIntent(packageManager, apps[p].pkg)?.let { startActivity(it) }
        }
        // Segurar OK: tela de informações do app (forçar parada, limpar cache, desinstalar).
        grid.setOnItemLongClickListener { _, _, p, _ ->
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + apps[p].pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
        grid.requestFocus()
    }
}
