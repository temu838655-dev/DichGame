package com.dichgame.app

/**
 * Chuẩn hóa cò analog Android về [0, 1], loại bỏ giá trị NaN/ngoài biên
 * và vùng chết nhỏ do driver báo cáo.
 */
internal object TriggerAxis {
    fun normalize(value: Float, flat: Float): Float {
        val v = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        val deadZone = flat.takeIf { it.isFinite() }?.coerceIn(0f, 0.95f) ?: 0f
        if (v <= deadZone) return 0f
        return ((v - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
    }
}
