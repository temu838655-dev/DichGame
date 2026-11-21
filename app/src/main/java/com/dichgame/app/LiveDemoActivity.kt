package com.dichgame.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout

/**
 * Chỉ bản debug, dùng trong ci/smoke.sh: thử chuỗi "lấy hình từ SurfaceView, đọc chữ, dịch, hiện phụ đề" của dịch phụ đề
 * trong màn chơi mà không cần máy PS5. SurfaceView ở đây vẽ sẵn một câu phụ đề tiếng Anh như video game thật.
 * Từng bước ghi vào nhật ký DG_LIVE.
 */
class LiveDemoActivity : Activity(), SurfaceHolder.Callback {
    private var sv: SurfaceView? = null
    private var live: LiveSubs? = null
    // mode=ui: thử dịch chữ giao diện (menu game) thay vì phụ đề
    private var uiMode = false
    // mode=narrate: thuyết minh (phụ đề tiếng Việt có sẵn, chỉ đọc to, không dịch)
    private var narrateMode = false
    private var uiTr: UiTranslate? = null
    private val ui = Handler(Looper.getMainLooper())
    private val redraw = object : Runnable {
        override fun run() {
            draw()
            ui.postDelayed(this, 700)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (!BuildConfig.DEBUG) { finish(); return }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val v = SurfaceView(this)
        sv = v
        v.holder.addCallback(this)
        val vid = FrameLayout(this)
        vid.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val h = (240 * resources.displayMetrics.density).toInt()
        root.addView(vid, FrameLayout.LayoutParams(MATCH_PARENT, h, Gravity.CENTER))
        setContentView(root)
        Prefs(this).lang = "en"
        uiMode = intent.getStringExtra("mode") == "ui"
        narrateMode = intent.getStringExtra("mode") == "narrate"
        Prefs(this).narrate = narrateMode
        if (uiMode) {
            val u = UiTranslate(this, { sv }, { null })
            uiTr = u
            u.attach(vid)
            ui.postDelayed({ u.start() }, 1500)
            return
        }
        val l = LiveSubs(this) { sv }
        live = l
        l.attach(vid)
        ui.postDelayed({ l.start() }, 1500)
    }

    /** Màn menu giả như trong game: tiêu đề, mục, mô tả (phải dịch) và chỉ số viết tắt, mã ID (phải bỏ qua). */
    private fun drawMenu(c: android.graphics.Canvas) {
        c.drawColor(Color.rgb(24, 30, 44))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        p.textSize = h * 0.09f
        c.drawText("Inventory", w * 0.06f, h * 0.15f, p)
        c.drawText("Skill Tree", w * 0.06f, h * 0.33f, p)
        c.drawText("Iron Sword", w * 0.06f, h * 0.51f, p)
        c.drawText("Health Potion", w * 0.06f, h * 0.69f, p)
        p.textSize = h * 0.075f
        c.drawText("ATK 42", w * 0.62f, h * 0.33f, p)
        c.drawText("ID4521", w * 0.62f, h * 0.60f, p)
        c.drawText("Open the map", w * 0.06f, h * 0.88f, p)
    }

    private fun draw() {
        val v = sv ?: return
        val holder = v.holder
        if (!holder.surface.isValid) return
        try {
            val c = holder.lockCanvas() ?: return
            try {
                if (uiMode) { drawMenu(c); return }
                c.drawColor(Color.rgb(24, 30, 44))
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                    textSize = c.height * 0.085f
                }
                if (narrateMode) {
                    c.drawText("Bạn định đi đâu tối nay?", c.width / 2f, c.height * 0.80f, p)
                    c.drawText("Hãy ở gần tôi.", c.width / 2f, c.height * 0.92f, p)
                } else {
                    c.drawText("Where are you going tonight?", c.width / 2f, c.height * 0.80f, p)
                    c.drawText("Stay close to me.", c.width / 2f, c.height * 0.92f, p)
                }
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        } catch (_: Throwable) {
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) { ui.removeCallbacks(redraw); ui.post(redraw) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { ui.removeCallbacks(redraw) }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        runCatching { live?.release() }
        runCatching { uiTr?.release() }
        super.onDestroy()
    }
}
