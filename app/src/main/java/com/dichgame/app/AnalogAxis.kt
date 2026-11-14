package com.dichgame.app

import kotlin.math.abs

/**
 * Chuẩn hóa trục analog theo dead zone do driver cung cấp.
 * Đầu vào/đầu ra nằm trong [-1, 1], giữ nguyên dấu và toàn biên độ.
 */
internal object AnalogAxis {
    fun normalize(value: Float, flat: Float): Float {
        val v = value.coerceIn(-1f, 1f)
        val deadZone = flat.takeIf { it.isFinite() }?.coerceIn(0f, 0.95f) ?: 0.1f
        val magnitude = abs(v)
        if (magnitude <= deadZone) return 0f
        val adjusted = ((magnitude - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        return if (v < 0f) -adjusted else adjusted
    }
}
