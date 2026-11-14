package com.dichgame.app

/**
 * Chuẩn hóa cò analog Android về [0, 1] dựa trên miền giá trị thực tế
 * mà driver công bố; hỗ trợ cả driver 0..1 và -1..1.
 */
internal object TriggerAxis {
    fun normalize(
        value: Float,
        flat: Float,
        min: Float = 0f,
        max: Float = 1f,
    ): Float {
        val validRange = min.isFinite() && max.isFinite() && max > min
        val low = if (validRange) min else 0f
        val span = if (validRange) max - min else 1f
        if (!value.isFinite()) return 0f

        val normalized = ((value.coerceIn(low, low + span) - low) / span).coerceIn(0f, 1f)
        val deadZone = (flat.takeIf { it.isFinite() && it >= 0f }?.div(span) ?: 0f)
            .coerceIn(0f, 0.95f)
        if (normalized <= deadZone) return 0f
        return ((normalized - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
    }
}
