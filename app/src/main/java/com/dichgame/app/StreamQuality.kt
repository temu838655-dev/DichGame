package com.dichgame.app

import android.content.Context
import android.media.MediaCodecList
import android.os.Build

/**
 * Chọn cấu hình luồng hình Remote Play, thuần Kotlin để có unit test. Chỉ chọn những gì engine và máy thật sự làm được:
 * engine chiaki-ng dừng ở 1080p, tối đa 60 FPS; không có 4K hay 120 FPS.
 */
object StreamPolicy {
    const val RES_AUTO = 0
    const val RES_720 = 1
    const val RES_1080 = 2
    const val RES_540 = 3
    const val RES_360 = 4

    const val CODEC_H264 = 0
    const val CODEC_H265 = 1

    /** Màn hình có cạnh dài dưới mức này thì 1080p không thêm được chi tiết nào đáng kể. */
    const val MIN_LONG_EDGE_FOR_1080 = 1600

    /** Đổi lựa chọn trong giao diện thành mức độ phân giải cụ thể (1..4). Tự động: 1080p khi máy giải mã được và màn hình đủ lớn, không thì 720p. */
    fun resolveRes(selection: Int, canDecode1080p60: Boolean, displayLongEdgePx: Int): Int = when (selection) {
        RES_720, RES_1080, RES_540, RES_360 -> selection
        else -> if (canDecode1080p60 && displayLongEdgePx >= MIN_LONG_EDGE_FOR_1080) RES_1080 else RES_720
    }

    fun height(res: Int): Int = when (res) { RES_1080 -> 1080; RES_540 -> 540; RES_360 -> 360; else -> 720 }

    /** Bitrate mặc định (kbps) của từng mức trong engine chiaki-ng; dùng để hiển thị khi chọn bitrate "Tự động". */
    fun defaultKbps(res: Int): Int = when (res) { RES_1080 -> 15_000; RES_540 -> 6_000; RES_360 -> 2_000; else -> 10_000 }

    fun fps(stored: Int): Int = if (stored == 30) 30 else 60
    fun codec(stored: Int): Int = if (stored == CODEC_H265) CODEC_H265 else CODEC_H264

    fun summary(res: Int, fps: Int, codec: Int, mbps: Int): String {
        val rate = if (mbps > 0) mbps else defaultKbps(res) / 1000
        return "${height(res)}p · $fps FPS · ${if (codec == CODEC_H265) "H.265" else "H.264"} · $rate Mbps"
    }
}

/** Hỏi bộ giải mã của máy xem giải mã được những gì. Chỉ tính bộ giải mã phần cứng: bộ giải mã phần mềm không theo kịp 1080p60. */
object DecoderCaps {
    val avc1080p60: Boolean by lazy { can("video/avc", 1920, 1080, 60) }
    val hevc1080p60: Boolean by lazy { can("video/hevc", 1920, 1080, 60) }

    private fun can(mime: String, w: Int, h: Int, fps: Int): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { ci ->
            !ci.isEncoder && ci.supportedTypes.any { it.equals(mime, true) } && hardware(ci) &&
                ci.getCapabilitiesForType(mime).videoCapabilities?.areSizeAndRateSupported(w, h, fps.toDouble()) == true
        }
    } catch (_: Throwable) { false }

    private fun hardware(ci: android.media.MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) ci.isHardwareAccelerated
        else !ci.name.startsWith("OMX.google.", true) && !ci.name.startsWith("c2.android.", true)

    fun displayLongEdge(ctx: Context): Int = ctx.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
}
