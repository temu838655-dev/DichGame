package com.dichgame.app

import android.graphics.Bitmap
import android.graphics.Color

/** Estimate the actual text-block palette from pixels inside its oriented quadrilateral. */
internal object NativeTextStyle {
    fun sample(bmp: Bitmap, block: Blk): Pair<Int, Int>? {
        val r = block.box ?: return null
        val q = block.corners?.takeIf { it.size == 8 } ?: floatArrayOf(
            r.left.toFloat(),r.top.toFloat(),r.right.toFloat(),r.top.toFloat(),
            r.right.toFloat(),r.bottom.toFloat(),r.left.toFloat(),r.bottom.toFloat())
        if (q.any { !it.isFinite() }) return null
        val colors = ArrayList<Int>()
        // Interior samples avoid selecting the black border outside a tilted phone/message bubble.
        val nx = 24; val ny = 14
        for (iy in 0 until ny) for (ix in 0 until nx) {
            val u = (ix + .5f)/nx; val v = (iy + .5f)/ny
            val x = ((1-v)*((1-u)*q[0]+u*q[2])+v*((1-u)*q[6]+u*q[4])).toInt()
            val y = ((1-v)*((1-u)*q[1]+u*q[3])+v*((1-u)*q[7]+u*q[5])).toInt()
            if (x in 0 until bmp.width && y in 0 until bmp.height) colors.add(bmp.getPixel(x,y))
        }
        if (colors.size < 40) return null
        fun lum(c: Int) = (Color.red(c)*3+Color.green(c)*6+Color.blue(c))/10
        fun distance(a: Int,b: Int) = maxOf(kotlin.math.abs(Color.red(a)-Color.red(b)),kotlin.math.abs(Color.green(a)-Color.green(b)),kotlin.math.abs(Color.blue(a)-Color.blue(b)))
        fun bucket(c: Int) = (Color.red(c)/32)*64+(Color.green(c)/32)*8+Color.blue(c)/32
        fun median(cs: List<Int>): Int = Color.rgb(cs.map { Color.red(it) }.sorted()[cs.size/2],cs.map { Color.green(it) }.sorted()[cs.size/2],cs.map { Color.blue(it) }.sorted()[cs.size/2])
        val dominant = colors.groupBy { bucket(it) }.values.maxByOrNull { it.size } ?: return null
        val bg = median(dominant)
        // Textured/animated scenery cannot be faithfully reconstructed from one flat color.
        if (colors.count { distance(it,bg) <= 48 } < colors.size*0.48f) return null
        val ink = colors.filter { kotlin.math.abs(lum(it)-lum(bg)) >= 65 }
        val fg = if (ink.size >= 4) median(ink.groupBy { bucket(it) }.values.maxByOrNull { it.size } ?: ink)
            else if (lum(bg)>145) 0xFF151515.toInt() else Color.WHITE
        return bg to fg
    }
}
