package br.leve.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var remoteUrl: TextView
    private lateinit var hero: View
    private lateinit var heroTitle: TextView
    private lateinit var heroHint: TextView
    private lateinit var appsRow: LinearLayout
    private lateinit var toolsRow: LinearLayout

    private val heroBg = GradientDrawable()
    private val ptBR = Locale("pt", "BR")
    private val clockFmt = SimpleDateFormat("HH:mm", ptBR)
    private val dateFmt = SimpleDateFormat("EEEE, d 'de' MMMM", ptBR)

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        remoteUrl = findViewById(R.id.remoteUrl)
        hero = findViewById(R.id.hero)
        heroTitle = findViewById(R.id.heroTitle)
        heroHint = findViewById(R.id.heroHint)
        appsRow = findViewById(R.id.appsRow)
        toolsRow = findViewById(R.id.toolsRow)

        heroBg.cornerRadius = dp(24).toFloat()
        hero.background = heroBg

        KeyInjector.init(this)
        RemoteService.start(this)
        buildTools()
        HomeStore.prefs(this).registerOnSharedPreferenceChangeListener(orderListener)
    }

    // O celular reordenou a home: redesenha na hora, sem reiniciar.
    private val orderListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { buildFavorites() }
    }

    override fun onDestroy() {
        HomeStore.prefs(this).unregisterOnSharedPreferenceChangeListener(orderListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        val ip = Net.localIp()
        remoteUrl.text = if (ip != null) "Controle pelo celular: $ip:${Config.REMOTE_PORT}"
        else "Sem rede: conecte o Wi-Fi para usar o celular"
        // Reconstrói a fileira a cada volta: pega apps recém-instalados.
        buildFavorites()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(tick) } catch (_: Exception) {}
    }

    @Deprecated("Na home, Voltar não faz nada")
    override fun onBackPressed() { /* já estamos na home */ }

    private fun updateClock() {
        val now = Date()
        clock.text = clockFmt.format(now)
        date.text = dateFmt.format(now)
    }

    // ---------- Fileira de apps ----------

    private fun buildFavorites() {
        val focusedIndex = (0 until appsRow.childCount).firstOrNull { appsRow.getChildAt(it).hasFocus() } ?: 0
        appsRow.removeAllViews()
        val pm = packageManager

        // Ordem definida pelo celular (aba Organizar); padrão = Config.favorites
        HomeStore.items(this).forEach { fav ->
            val pkg = fav.pkg
            val tile = newTile(appsRow)
            val icon = tile.findViewById<ImageView>(R.id.icon)
            val mono = tile.findViewById<TextView>(R.id.mono)
            tile.findViewById<TextView>(R.id.label).text = fav.label

            if (pkg != null) {
                icon.setImageDrawable(try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null })
            } else {
                icon.visibility = View.GONE
                mono.visibility = View.VISIBLE
                mono.text = fav.mono
                mono.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(fav.tint) }
                tile.alpha = 0.55f
            }
            val hint = if (pkg != null) fav.hint else "Não instalado · instale o APK pela pasta de otimização"
            tile.setOnFocusChangeListener { v, has -> onTileFocus(v, has, fav.label, hint, fav.tint) }
            tile.setOnClickListener {
                if (pkg == null) toast("${fav.label} não está instalado") else open(pkg)
            }
            appsRow.addView(tile)
        }

        val allTile = newTile(appsRow)
        allTile.findViewById<ImageView>(R.id.icon).visibility = View.GONE
        allTile.findViewById<TextView>(R.id.mono).apply { visibility = View.VISIBLE; text = "···" }
        allTile.findViewById<TextView>(R.id.label).text = "Todos os apps"
        allTile.setOnFocusChangeListener { v, has -> onTileFocus(v, has, "Todos os apps", "Lista completa dos apps instalados", 0xFF1E2430.toInt()) }
        allTile.setOnClickListener { startActivity(Intent(this, AllAppsActivity::class.java)) }
        appsRow.addView(allTile)

        spaceOut(appsRow, 14)
        appsRow.getChildAt(focusedIndex.coerceAtMost(appsRow.childCount - 1))?.requestFocus()
    }

    // ---------- Atalhos do projetor ----------

    private data class Tool(val label: String, val hint: String, val action: () -> Unit)

    private fun buildTools() {
        val tools = listOf(
            Tool("Entrada HDMI", "Troca para a fonte HDMI") { openVendor(Config.hdmiPackages, Settings.ACTION_SETTINGS) },
            Tool("Foco e trapézio", "Ajuste de imagem do projetor") { openVendor(Config.focusPackages, Settings.ACTION_DISPLAY_SETTINGS) },
            Tool("Wi-Fi", "Configurações de rede") { openSettings(Settings.ACTION_WIFI_SETTINGS) },
            Tool("Bluetooth", "Caixa de som, fone ou controle") { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Tool("Espelhar tela", "Recebe a tela do celular") { openVendor(Config.mirrorPackages, Settings.ACTION_CAST_SETTINGS) },
            Tool("Liberar memória", "Fecha apps em segundo plano") { freeMemory() },
        )
        tools.forEach { t ->
            val b = TextView(this).apply {
                text = t.label
                textSize = 14f
                setTextColor(0xFFC9CED8.toInt())
                gravity = android.view.Gravity.CENTER
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.tile_bg)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnFocusChangeListener { v, has -> onTileFocus(v, has, t.label, t.hint, 0xFF1E2430.toInt()) }
                setOnClickListener { t.action() }
            }
            toolsRow.addView(b)
        }
        spaceOut(toolsRow, 10)
    }

    // ---------- Ações ----------

    private fun open(pkg: String) {
        val i = AppRepo.launchIntent(packageManager, pkg) ?: return toast("Não foi possível abrir")
        try { startActivity(i) } catch (e: Exception) { toast("Não foi possível abrir") }
    }

    private fun openVendor(pkgs: List<String>, fallback: String) {
        val pkg = AppRepo.firstInstalled(packageManager, pkgs)
        if (pkg != null) open(pkg) else openSettings(fallback)
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { toast("Configurações indisponíveis") }
        }
    }

    private fun freeMemory() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = memMb(am)
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName == packageName) continue
            val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (!isSystem) am.killBackgroundProcesses(ai.packageName)
        }
        val after = memMb(am)
        toast("Memória livre: $before MB → $after MB")
    }

    private fun memMb(am: ActivityManager): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }

    // ---------- Visual ----------

    private fun onTileFocus(v: View, has: Boolean, title: String, hint: String, tint: Int) {
        v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(110).start()
        if (has) {
            heroTitle.text = title
            heroHint.text = hint
            heroBg.setColor(tint)
        }
    }

    private fun newTile(parent: LinearLayout): View =
        LayoutInflater.from(this).inflate(R.layout.item_tile, parent, false)

    private fun spaceOut(row: LinearLayout, gapDp: Int) {
        for (i in 0 until row.childCount - 1) {
            (row.getChildAt(i).layoutParams as LinearLayout.LayoutParams).marginEnd = dp(gapDp)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
