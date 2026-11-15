package br.leve.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Desenha o ponteiro do mouse (MouseTrack) sobre qualquer aplicativo do projetor.
 *
 * A movimentação roda direto na UI thread do serviço (60fps, sem custo de processo),
 * enquanto o clique/rolagem usa as coordenadas atuais (x, y) no KeyInjector.
 */
object CursorOverlay {

    private const val SCREEN_W = 1280
    private const val SCREEN_H = 720
    private const val AUTO_HIDE_MS = 5000L

    @Volatile var x: Int = SCREEN_W / 2
        private set
    @Volatile var y: Int = SCREEN_H / 2
        private set

    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var wm: WindowManager? = null
    private var view: CursorView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var attached = false

    private val hideTask = Runnable {
        view?.visibility = View.GONE
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        wm = appCtx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    fun moveBy(dx: Float, dy: Float) {
        val nx = (x + dx.roundToInt()).coerceIn(0, SCREEN_W - 1)
        val ny = (y + dy.roundToInt()).coerceIn(0, SCREEN_H - 1)
        moveTo(nx, ny)
    }

    fun moveTo(nx: Int, ny: Int) {
        x = nx.coerceIn(0, SCREEN_W - 1)
        y = ny.coerceIn(0, SCREEN_H - 1)
        main.post {
            if (!ensureAttached()) return@post
            val p = lp ?: return@post
            val v = view ?: return@post
            p.x = x
            p.y = y
            v.visibility = View.VISIBLE
            try { wm?.updateViewLayout(v, p) } catch (_: Exception) {}
            scheduleHide()
        }
    }

    fun pulseClick() {
        main.post {
            if (!ensureAttached()) return@post
            val v = view ?: return@post
            v.visibility = View.VISIBLE
            v.triggerClickEffect()
            scheduleHide()
        }
    }

    fun hide() {
        main.post {
            main.removeCallbacks(hideTask)
            view?.visibility = View.GONE
        }
    }

    fun destroy() {
        main.post {
            main.removeCallbacks(hideTask)
            if (attached && view != null) {
                try { wm?.removeView(view) } catch (_: Exception) {}
            }
            attached = false
            view = null
            lp = null
        }
    }

    private fun scheduleHide() {
        main.removeCallbacks(hideTask)
        main.postDelayed(hideTask, AUTO_HIDE_MS)
    }

    private fun ensureAttached(): Boolean {
        if (attached && view != null) return true
        val c = appCtx ?: return false
        val w = wm ?: return false
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) {
            KeyInjector.grantOverlayPermission()
            return false
        }
        return try {
            val size = (38 * c.resources.displayMetrics.density).roundToInt().coerceAtLeast(38)
            val type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            val params = WindowManager.LayoutParams(size, size, type, flags, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = this@CursorOverlay.x
                y = this@CursorOverlay.y
            }
            val cv = CursorView(c)
            w.addView(cv, params)
            view = cv
            lp = params
            attached = true
            true
        } catch (_: Exception) {
            false
        }
    }

    private class CursorView(ctx: Context) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private var clickRadius = 0f

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F2A93B")
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * d
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#0B0D12")
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * d
            color = Color.parseColor("#4ADE80")
        }
        private val arrow = Path().apply {
            moveTo(2f * d, 2f * d)
            lineTo(2f * d, 26f * d)
            lineTo(9f * d, 20f * d)
            lineTo(14f * d, 30f * d)
            lineTo(18f * d, 28f * d)
            lineTo(13f * d, 18f * d)
            lineTo(22f * d, 18f * d)
            close()
        }

        fun triggerClickEffect() {
            clickRadius = 14f * d
            invalidate()
            postDelayed({
                clickRadius = 0f
                invalidate()
            }, 180)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (clickRadius > 0f) {
                canvas.drawCircle(6f * d, 6f * d, clickRadius, ringPaint)
            }
            canvas.drawPath(arrow, strokePaint)
            canvas.drawPath(arrow, fillPaint)
        }
    }
}
