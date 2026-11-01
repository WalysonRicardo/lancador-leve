package br.leve.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** Mantém o servidor do controle vivo mesmo com Netflix/YouTube na frente. */
class RemoteService : Service() {

    private var server: RemoteServer? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification())
        KeyInjector.init(this)
        KeyInjector.warmUp()
        server = RemoteServer(this, Config.REMOTE_PORT).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        server?.stop()
        KeyInjector.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("remote", "Controle pelo celular", NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, "remote")
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return b.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Controle pelo celular ativo")
            .setOngoing(true)
            .build()
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, RemoteService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
