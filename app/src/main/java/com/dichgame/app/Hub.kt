package com.dichgame.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.LinearLayout
import android.view.View
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Bốn mục cài đặt gom trong một thẻ kính: Cách dịch, Giọng đọc, Phụ đề, Hồ sơ game.
 * Dùng chung cho màn hình chính và trang Cài đặt; mỗi hàng hiện giá trị đang chọn và mở một trang con.
 */
object Hub {
    fun engineLabel(p: Prefs): String = when (p.engine) {
        Prefs.ENGINE_CLOUD -> "CloudAPI"
        Prefs.ENGINE_GEMINI -> "Google Gemini"
        else -> "Dịch trên máy"
    }

    fun voiceLabel(a: Activity, p: Prefs): String {
        val v = when {
            !p.speak -> "Tắt tiếng"
            p.voiceEngine == "offline" -> if (Voices.installed(a, p.offlineVoice)) "AI offline" else "Chưa tải giọng"
            p.aiVoiceOn -> "Gemini AI"
            else -> p.voice.ifEmpty { "Giọng cơ bản" }
        }
        return "$v · ${String.format("%.1f", p.rate10 / 10f).replace('.', ',')}x"
    }

    fun subsLabel(p: Prefs): String = "Cỡ ${p.subScale}% · " + Prefs.STYLES.getOrElse(p.style) { "" }

    fun profileLabel(p: Prefs): String {
        val g = PackStore.gameName(p)
        return if (g.isEmpty()) p.profile else g
    }

    private fun open(a: Activity, page: String) {
        a.startActivity(Intent(a, SettingsActivity::class.java).putExtra("page", page))
    }

    /** Home uses separate metallic tiles; Settings retains its original navigation rows. */
    fun homeGrid(a: Activity, p: Prefs): LinearLayout {
        val grid = Ui.vbox(a)
        fun tile(kind: ToolIcon.Kind, title: String, value: String, action: () -> Unit): View {
            val tile = Ui.card(a, 13)
            tile.minimumHeight = Ui.dp(a, 112)
            val head = Ui.hbox(a)
            head.addView(Ui.iconTile(a, kind, Color.parseColor("#B77B31"), Color.parseColor("#4B301B"), 38),
                Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)))
            head.addView(View(a), Ui.lp(0, 1, 1f))
            head.addView(Ui.tv(a, "›", 24f, Ui.GOLD))
            tile.addView(head)
            tile.addView(Ui.tv(a, title, 17f, Ui.TEXT, true), Ui.lpm(a, 6))
            tile.addView(Ui.tv(a, value, 13f, Ui.GOLD).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, Ui.lpm(a, 2))
            tile.isClickable = true
            tile.isFocusable = true
            Ui.ripple(tile, 22)
            tile.setOnClickListener { action() }
            return tile
        }
        fun pair(left: View, right: View) {
            val row = Ui.hbox(a).apply { gravity = Gravity.TOP }
            row.addView(left, Ui.lp(0, MATCH_PARENT, 1f))
            row.addView(right, Ui.lp(0, MATCH_PARENT, 1f).apply { leftMargin = Ui.dp(a, 10) })
            grid.addView(row, Ui.lpm(a, if (grid.childCount == 0) 0 else 10))
        }
        pair(tile(ToolIcon.Kind.TRANSLATE, "Cách dịch", engineLabel(p)) {
            a.startActivity(Intent(a, TranslateActivity::class.java))
        }, tile(ToolIcon.Kind.SPEAKER_ON, "Giọng đọc", voiceLabel(a, p)) { open(a, "voice") })
        pair(tile(ToolIcon.Kind.SUBS, "Phụ đề", subsLabel(p)) { open(a, "subs") },
            tile(ToolIcon.Kind.GAMEPAD, "Hồ sơ game", profileLabel(p)) { open(a, "profile") })
        return grid
    }

    fun card(a: Activity, p: Prefs): LinearLayout {
        val c = Ui.card(a, 0)
        c.addView(Ui.navRow(a, Ui.iconTile(a, ToolIcon.Kind.TRANSLATE, Color.parseColor("#5B9BFF"), Color.parseColor("#2F5FE3")),
            "Cách dịch", engineLabel(p) + " · " + Prefs.langName(p.lang).removePrefix("Tiếng ")) {
            a.startActivity(Intent(a, TranslateActivity::class.java))
        })
        c.divide(66)
        c.addView(Ui.navRow(a, Ui.iconTile(a, ToolIcon.Kind.SPEAKER_ON, Color.parseColor("#FFB04D"), Color.parseColor("#F0661C")),
            "Giọng đọc", voiceLabel(a, p)) { open(a, "voice") })
        c.divide(66)
        c.addView(Ui.navRow(a, Ui.iconTile(a, ToolIcon.Kind.SUBS, Color.parseColor("#4EE08F"), Color.parseColor("#18A35A")),
            "Phụ đề", subsLabel(p)) { open(a, "subs") })
        c.divide(66)
        c.addView(Ui.navRow(a, Ui.iconTile(a, ToolIcon.Kind.GAMEPAD, Color.parseColor("#BB86FF"), Color.parseColor("#7C4DE8")),
            "Hồ sơ game", profileLabel(p)) { open(a, "profile") })
        return c
    }
}
