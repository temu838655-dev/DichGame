package com.dichgame.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.LinearLayout

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

    fun card(a: Activity, p: Prefs): LinearLayout {
        val c = Ui.card(a, 0)
        c.addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.TRANSLATE, Color.parseColor("#5B9BFF"), Color.parseColor("#2F5FE3")),
            "Cách dịch", engineLabel(p) + " · " + Prefs.langName(p.lang).removePrefix("Tiếng ")) {
            a.startActivity(Intent(a, TranslateActivity::class.java))
        })
        c.divide(70)
        c.addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.SPEAKER_ON, Color.parseColor("#FFB04D"), Color.parseColor("#F0661C")),
            "Giọng đọc", voiceLabel(a, p)) { open(a, "voice") })
        c.divide(70)
        c.addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.SUBS, Color.parseColor("#4EE08F"), Color.parseColor("#18A35A")),
            "Phụ đề", subsLabel(p)) { open(a, "subs") })
        c.divide(70)
        c.addView(SUi.hubRow(a, Ui.iconTile(a, ToolIcon.Kind.GAMEPAD, Color.parseColor("#BB86FF"), Color.parseColor("#7C4DE8")),
            "Hồ sơ game", profileLabel(p)) { open(a, "profile") })
        return c
    }
}
