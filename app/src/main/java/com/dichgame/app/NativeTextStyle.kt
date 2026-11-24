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
        return NativePalette.estimate(colors)
    }
}
