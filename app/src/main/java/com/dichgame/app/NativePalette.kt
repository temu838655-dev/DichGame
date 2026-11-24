package com.dichgame.app

/** Pixel-only palette estimator, deliberately independent of Android for unit tests. */
internal object NativePalette {
    private fun red(c: Int) = c ushr 16 and 255
    private fun green(c: Int) = c ushr 8 and 255
    private fun blue(c: Int) = c and 255
    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    private fun lum(c: Int) = (red(c)*3+green(c)*6+blue(c))/10
    private fun distance(a: Int,b: Int) = maxOf(kotlin.math.abs(red(a)-red(b)),kotlin.math.abs(green(a)-green(b)),kotlin.math.abs(blue(a)-blue(b)))
    private fun bucket(c: Int) = (red(c)/32)*64+(green(c)/32)*8+blue(c)/32
    private fun median(cs: List<Int>): Int = rgb(cs.map(::red).sorted()[cs.size/2],cs.map(::green).sorted()[cs.size/2],cs.map(::blue).sorted()[cs.size/2])

    // Relative luminance and contrast prevent dark-on-dark or light-on-light text.
    private fun linear(v: Int): Double {
        val x = v/255.0
        return if (x <= .04045) x/12.92 else Math.pow((x+.055)/1.055, 2.4)
    }
    private fun relative(c: Int) = .2126*linear(red(c))+.7152*linear(green(c))+.0722*linear(blue(c))
    fun contrast(a: Int, b: Int): Double {
        val x = relative(a); val y = relative(b)
        return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }

    fun estimate(colors: List<Int>): Pair<Int, Int>? {
        if (colors.size < 40) return null
        val groups = colors.groupBy(::bucket)
        val dominant = groups.values.maxByOrNull { it.size } ?: return null
        val bg = median(dominant)
        val similar = colors.filter { distance(it,bg) <= 48 }
        if (similar.size < colors.size*.48f) return null
        // Refuse a single flat rectangle over a highly varied photo or gradient.
        val background = median(similar)
        val ink = colors.filter { kotlin.math.abs(lum(it)-lum(background)) >= 65 }
        val black = rgb(17,17,17); val white = rgb(255,255,255)
        val fallback = if (contrast(black,background) >= contrast(white,background)) black else white
        val original = if (ink.size >= 4) median(ink.groupBy(::bucket).values.maxByOrNull { it.size } ?: ink) else fallback
        val fg = if (contrast(original,background) >= 4.5) original else fallback
        return background to fg
    }
}
