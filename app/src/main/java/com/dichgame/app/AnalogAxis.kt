package com.dichgame.app

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Chuẩn hóa một trục analog theo dead zone do driver cung cấp.
 * Đầu vào/đầu ra nằm trong [-1, 1], giữ nguyên dấu và toàn biên độ.
 */
internal object AnalogAxis {
    fun normalize(value: Float, flat: Float): Float {
        // Một số tay cầm/driver có thể phát NaN hoặc vô cực khi vừa kết nối.
        val v = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
        val deadZone = flat.takeIf { it.isFinite() }?.coerceIn(0f, 0.95f) ?: 0.1f
        val magnitude = abs(v)
        if (magnitude <= deadZone) return 0f
        val adjusted = ((magnitude - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        return if (v < 0f) -adjusted else adjusted
    }

    /**
     * Dead zone tròn cho cả cần, tránh vùng chết hình vuông và giữ hướng chéo tự nhiên.
     * Kết quả không vượt quá bán kính 1.
     */
    fun normalizePair(x: Float, y: Float, flatX: Float, flatY: Float): Pair<Float, Float> {
        val safeX = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f
        val safeY = if (y.isFinite()) y.coerceIn(-1f, 1f) else 0f
        val magnitude = sqrt(safeX * safeX + safeY * safeY)
        val deadZone = maxOf(
            flatX.takeIf { it.isFinite() }?.coerceIn(0f, 0.95f) ?: 0.1f,
            flatY.takeIf { it.isFinite() }?.coerceIn(0f, 0.95f) ?: 0.1f
        )
        if (magnitude <= deadZone || magnitude == 0f) return 0f to 0f
        val scaledMagnitude = ((magnitude - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        val scale = scaledMagnitude / magnitude
        return (safeX * scale).coerceIn(-1f, 1f) to (safeY * scale).coerceIn(-1f, 1f)
    }
}
