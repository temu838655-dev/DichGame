package com.dichgame.app

/** Compare the captured 64x32 luminance grid, excluding our translated rectangles. */
internal class MenuSceneGuard private constructor(private val baseline: IntArray, private val mask: BooleanArray) {
    private var changedFrames = 0
    fun changed(frame: IntArray): Boolean {
        if (frame.size != baseline.size) return true
        var count = 0; var changed = 0; var sum = 0L
        for (i in frame.indices) if (!mask[i]) {
            val delta = kotlin.math.abs(frame[i] - baseline[i])
            count++
            sum += delta
            if (delta >= 28) changed++
        }
        // Too little game image remains visible: rely on the existing hold timeout.
        if (count < baseline.size / 5) return false
        val substantial = sum.toDouble()/count >= 14.0 && changed.toDouble()/count >= 0.26
        changedFrames = if (substantial) changedFrames + 1 else 0
        return changedFrames >= 2
    }

    companion object {
        private const val W = 64
        private const val H = 32
        fun create(baseline: IntArray, width: Int, height: Int, boxes: List<IntArray>): MenuSceneGuard? {
            if (baseline.size != W*H || width <= 0 || height <= 0) return null
            val mask = BooleanArray(W*H)
            for (b in boxes) {
                if (b.size != 4) continue
                val left = (b[0].toLong()*W/width-1).toInt().coerceIn(0,W)
                val right = (b[2].toLong()*W/width+2).toInt().coerceIn(0,W)
                val top = (b[1].toLong()*H/height-1).toInt().coerceIn(0,H)
                val bottom = (b[3].toLong()*H/height+2).toInt().coerceIn(0,H)
                for (y in top until bottom) for (x in left until right) mask[y*W+x] = true
            }
            return MenuSceneGuard(baseline.copyOf(),mask)
        }
    }
}
