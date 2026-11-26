package com.dichgame.app

import java.util.Locale

/**
 * Số đo của một lần dịch (từ lúc bấm bắt đầu tới lúc dừng): để người dùng và người phát triển thấy app thật sự làm gì
 * khi chơi: bao nhiêu khung hình được bỏ qua, bao nhiêu chữ rác bị loại, bao nhiêu câu nghe được trùng với phụ đề,
 * độ trễ từ lúc câu xuất hiện tới lúc hiện bản dịch, pin tiêu hao. Chỉ đếm, không lưu nội dung lời thoại. Không phụ thuộc Android.
 */
class SessionStats {
    private val counts = HashMap<String, Long>()
    private val latencies = ArrayList<Long>()
    private var lastTranslationModel = ""
    private var startAt = 0L
    private var startBattery = -1

    @Synchronized fun reset(nowMs: Long, batteryPct: Int) {
        lastTranslationModel = ""
        counts.clear(); latencies.clear(); startAt = nowMs; startBattery = batteryPct
    }

    @Synchronized fun inc(key: String, n: Long = 1) { counts[key] = (counts[key] ?: 0L) + n }

    @Synchronized fun get(key: String): Long = counts[key] ?: 0L

    @Synchronized fun recordTranslationModel(provider: String, requested: String, reported: String?) {
        val actual = reported?.trim()?.takeIf { it.isNotEmpty() }
        lastTranslationModel = if (actual == null) "$provider · $requested (model gửi trong yêu cầu)"
            else "$provider · ${actual.take(160)}"
    }

    @Synchronized fun recordTokens(metric: String, input: Long, output: Long) {
        if (input < 0 || output < 0) return
        inc(metric + "_usage")
        inc(metric + "_input", input)
        inc(metric + "_output", output)
    }

    /** Độ trễ (ms) từ lúc câu xuất hiện (chữ hiện trên màn hình, hoặc nhân vật nói xong) tới lúc bản dịch được hiện. */
    @Synchronized fun latency(ms: Long) {
        if (ms < 0 || ms > 120_000) return
        if (latencies.size >= 2_000) latencies.removeAt(0)
        latencies.add(ms)
    }

    @Synchronized fun percentile(p: Int): Long {
        if (latencies.isEmpty()) return -1
        val s = latencies.sorted()
        return s[((s.size - 1) * p / 100).coerceIn(0, s.size - 1)]
    }

    private fun sec(ms: Long) = String.format(Locale.US, "%.1f", ms / 1000.0)

    /** Lý do các câu phải dịch trên máy dù đã bật AI ("hết lượt 5, mạng 2, ..."); rỗng nếu không có câu nào. Số lần gặp lỗi, một câu có thể tính hơn một lần. */
    @Synchronized fun machineReasons(): String {
        val parts = ArrayList<String>()
        fun add(label: String, key: String) { val n = get(key); if (n > 0) parts.add("$label $n") }
        add("hết lượt", "may_het_luot"); add("giới hạn thanh toán/chi tiêu", "may_billing")
        add("mạng hoặc máy chủ AI quá tải", "may_mang"); add("bộ lọc nội dung chặn", "may_chan")
        add("khóa hoặc model sai", "may_khoa"); add("dữ liệu lạ", "may_khac"); add("bản dịch AI bị loại hai lần", "may_loai")
        return parts.joinToString(", ")
    }

    @Synchronized fun report(nowMs: Long, batteryPct: Int): String {
        if (startAt == 0L) return "Chưa có lần dịch nào. Bấm bắt đầu dịch một lần rồi quay lại đây."
        val sb = StringBuilder()
        val mins = ((nowMs - startAt) / 60_000L).coerceAtLeast(0)
        sb.append("Thời lượng: ").append(if (mins < 1) "dưới 1 phút" else "$mins phút").append('\n')
        val scans = get("quet")
        if (scans > 0) {
            val skipped = get("quet_dung_yen")
            sb.append("Quét màn hình: $scans lần, bỏ qua ${skipped} lần vì khung hình đứng yên (${skipped * 100 / scans}%)\n")
        }
        sb.append("Chữ đọc được: ${get("ocr_cau")} câu; loại vì chữ rác hoặc độ tin cậy thấp: ${get("ocr_rac")}\n")
        val src = when {
            get("nguon_he_thong") > 0 && get("nguon_mic") > 0 -> "âm thanh trong của máy, sau đó micro"
            get("nguon_he_thong") > 0 -> "âm thanh trong của máy"
            get("nguon_mic") > 0 -> "micro"
            get("nguon_khong") > 0 -> "không nghe được (xem thông báo lúc bắt đầu)"
            else -> ""
        }
        if (src.isNotEmpty()) sb.append("Nguồn âm thanh trên điện thoại: ").append(src).append('\n')
        val heard = get("asr_nhan")
        sb.append("Nghe âm thanh game: $heard câu; loại vì nhiễu hoặc ảo giác: ${get("asr_loc")}; trùng với phụ đề trên màn hình: ${get("asr_trung")}")
        sb.append("; ghép với chữ đọc dở: ${get("ghep")}\n")
        sb.append("Dịch: AI ${get("dich_ai")}, bộ nhớ đệm ${get("dich_cache")}, gói Việt hoá ${get("dich_goi")}" + (if (get("dich_goi_gan") > 0) " (trong đó ${get("dich_goi_gan")} khớp gần đúng)" else "") + ", dịch trên máy ${get("dich_may")}")
        sb.append("; AI bị loại rồi dịch lại ${get("dich_loai")}; còn nghi ngờ ${get("dich_nghi")}\n")
        if (lastTranslationModel.isNotEmpty()) sb.append("Phản hồi dịch AI gần nhất: ").append(lastTranslationModel).append('\n')
        val gemini = get("api_dich_gemini")
        val cloud = get("api_dich_cloud")
        val voice = get("api_giong_gemini")
        sb.append("Lượt thử gửi API: dịch Gemini $gemini, dịch CloudAPI $cloud, giọng Gemini $voice (gồm thử lại; không phải số lượt tính phí).\n")
        for ((metric, label) in listOf("api_dich_gemini" to "Gemini dịch", "api_dich_cloud" to "CloudAPI dịch", "api_giong_gemini" to "Gemini giọng")) {
            val measured = get(metric + "_usage")
            if (measured > 0) sb.append("Token $label: vào ${get(metric + "_input")}, ra ${get(metric + "_output")} ($measured phản hồi có số liệu).\n")
        }
        val read = get("doc_doc")
        if (read > 0) {
            sb.append("Đọc to: $read câu; phải xếp hàng chờ ${get("doc_cho")}, bị cắt giữa chừng ${get("doc_cat")}, bị bỏ vì dồn hoặc quá cũ ${get("doc_bo")}\n")
        }
        if (get("khoa_doi") > 0) sb.append("Đổi khóa Gemini tự động: ${get("khoa_doi")} lần\n")
        val why = machineReasons()
        if (why.isNotEmpty()) sb.append("Dịch trên máy vì AI không dùng được: ").append(why).append('\n')
        val n = synchronized(this) { latencies.size }
        if (n > 0) sb.append("Từ lúc câu xuất hiện tới lúc hiện bản dịch (${n} câu): giữa ${sec(percentile(50))} giây, 90% dưới ${sec(percentile(90))} giây\n")
        if (startBattery in 0..100 && batteryPct in 0..100 && nowMs > startAt) {
            val drop = startBattery - batteryPct
            val perHour = if (nowMs - startAt >= 10 * 60_000L) " (khoảng ${drop * 3_600_000L / (nowMs - startAt)}% mỗi giờ)" else " (dịch trên 10 phút mới ước lượng được mỗi giờ)"
            sb.append("Pin: ").append(if (drop >= 0) "giảm $drop%" else "tăng ${-drop}% (đang sạc)").append(perHour).append('\n')
        }
        return sb.toString().trimEnd()
    }

    companion object {
        /** Số đo của lần dịch hiện tại, dùng chung trong tiến trình. */
        val live = SessionStats()
        @Volatile var lastReport: String = ""
    }
}

/** Màu chữ riêng cho từng người nói, ổn định theo tên (cùng một tên luôn cùng một màu), dễ đọc trên nền tối. */
object SpeakerColor {
    private val PALETTE = intArrayOf(
        0xFFFFD54F.toInt(), 0xFF80DEEA.toInt(), 0xFFA5D6A7.toInt(), 0xFFF48FB1.toInt(), 0xFFCE93D8.toInt(), 0xFFFFAB91.toInt(), 0xFF9FA8DA.toInt(), 0xFFE6EE9C.toInt(),
    )

    fun of(name: String): Int {
        val k = SpeakerBook.key(name)
        var h = 0
        for (c in k) h = h * 31 + c.code
        return PALETTE[(h and 0x7fffffff) % PALETTE.size]
    }
}
