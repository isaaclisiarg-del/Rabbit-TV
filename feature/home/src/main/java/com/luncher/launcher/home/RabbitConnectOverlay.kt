package com.luncher.launcher.home

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.rabbit.connect.protocol.RabbitConnectDeduplicator
import java.util.LinkedHashMap

/** Owns notification-only windows. This class never requests or reads window contents. */
internal object RabbitConnectOverlay {
    private data class Shown(val manager: WindowManager, val view: android.view.View, val timeout: Runnable, val type: Int)
    private val handler = Handler(Looper.getMainLooper())
    private val visible = LinkedHashMap<String, Shown>()
    private val fingerprints = RabbitConnectDeduplicator()
    @Volatile private var accessibility: RabbitHomeOverrideService? = null

    fun attach(service: RabbitHomeOverrideService) { accessibility = service }
    fun detach(service: RabbitHomeOverrideService) {
        if (accessibility === service) accessibility = null
        handler.post { clearAll(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) }
    }

    fun show(context: Context, id: String, app: String, title: String, text: String): Boolean {
        val fingerprint = listOf(app, title, text).hashCode()
        val service = accessibility
        val useAccessibility = service != null
        if (!useAccessibility && !Settings.canDrawOverlays(context)) return false
        val windowContext = service ?: context
        val manager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (useAccessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val view = card(windowContext, app, title, text)
        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            (metrics.widthPixels * 0.48f).toInt().coerceAtMost(dp(context, 560)).coerceAtLeast(dp(context, 300)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(context, 28); y = dp(context, 30) }
        val shown = java.util.concurrent.atomic.AtomicBoolean(false)
        val complete = java.util.concurrent.CountDownLatch(1)
        val action = Runnable {
            synchronized(visible) {
                if (fingerprints.isDuplicate(id, fingerprint)) { shown.set(true); complete.countDown(); return@synchronized }
                visible.values.toList().forEach(::remove)
                visible.clear()
                try {
                    manager.addView(view, params)
                    val timeout = Runnable { synchronized(visible) { visible.remove(id)?.let(::remove) } }
                    visible[id] = Shown(manager, view, timeout, type)
                    handler.postDelayed(timeout, DISPLAY_MS)
                    fingerprints.record(id, fingerprint)
                    while (visible.size > 24) visible.entries.firstOrNull()?.key?.let { visible.remove(it)?.let(::remove) }
                    shown.set(true)
                } catch (_: RuntimeException) { }
                finally { complete.countDown() }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action.run()
        else handler.post(action)
        return try { complete.await(2, java.util.concurrent.TimeUnit.SECONDS) && shown.get() } catch (_: InterruptedException) {
            Thread.currentThread().interrupt(); false
        }
    }

    private fun card(context: Context, app: String, title: String, text: String): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 18), dp(context, 22), dp(context, 18))
            background = GradientDrawable().apply {
                setColor(0xEE20242B.toInt())
                cornerRadius = dp(context, 14).toFloat()
                setStroke(dp(context, 1), 0x557FFFFFFF.toInt())
            }
            isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        fun line(value: String, size: Float, bold: Boolean) {
            if (value.isBlank()) return
            root.addView(TextView(context).apply {
                this.text = value.take(512)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = size
                maxLines = if (bold) 1 else 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                isFocusable = false
            })
        }
        line(app, 14f, true)
        line(title, 19f, true)
        line(text, 16f, false)
        return root
    }

    private fun clearAll(type: Int) {
        synchronized(visible) {
            val matches = visible.filterValues { it.type == type }.keys.toList()
            matches.forEach { visible.remove(it)?.let(::remove) }
        }
    }

    private fun remove(shown: Shown) {
        handler.removeCallbacks(shown.timeout)
        runCatching { shown.manager.removeView(shown.view) }
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private const val DISPLAY_MS = 6_000L
}
