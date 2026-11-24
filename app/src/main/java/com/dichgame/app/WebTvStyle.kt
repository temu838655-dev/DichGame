package com.dichgame.app

import android.content.SharedPreferences
import org.json.JSONObject

/** Appearance shared by paired LG/Tizen receivers. */
internal data class WebTvStyle(val size: Int = 42, val color: String = "#ffffff",
    val background: Int = 75, val top: Boolean = false, val speak: Boolean = false) {
    fun json() = JSONObject().put("size", size).put("color", color)
        .put("background", background).put("top", top).put("speak", speak)
    companion object {
        fun read(p: SharedPreferences) = WebTvStyle(
            p.getInt("web_tv_size", 42).coerceIn(24, 80),
            p.getString("web_tv_color", "#ffffff").orEmpty().takeIf {
                it in listOf("#ffffff", "#ffff00", "#00ffff")
            } ?: "#ffffff",
            p.getInt("web_tv_background", 75).coerceIn(0, 100),
            p.getBoolean("web_tv_top", false), p.getBoolean("web_tv_speak", false)
        )
    }
}
