package com.dichgame.app

/**
 * So khớp tên game khi tìm ảnh bìa (Steam, Wikipedia). Thuần Kotlin để có unit test: nhận nhầm ảnh bìa của game khác
 * (phần 1 thay vì phần 2, phim thay vì game) tệ hơn là không có ảnh.
 */
object CoverMatch {
    private val ROMAN = Regex("^(i{1,3}|iv|v|vi{1,3}|ix|x{1,3}i{0,3}|xiv|xv)$")
    private val SUFFIX = Regex("\\s*\\((?:[^)]*\\s)?(?:video )?game\\)\\s*$", RegexOption.IGNORE_CASE)

    /** Chữ thường, bỏ ™®©, bỏ "the" đứng riêng, chỉ giữ chữ và số. */
    fun norm(s: String): String =
        s.lowercase().replace(Regex("[™®©]"), "").replace(Regex("(^|[^a-z0-9])the(?![a-z0-9])"), " ").replace(Regex("[^a-z0-9]+"), "")

    /** Bỏ phần phân biệt của Wikipedia ở cuối tên bài: "Alan Wake 2 (video game)", "Hades (2020 video game)". */
    fun stripGameSuffix(title: String): String = title.replace(SUFFIX, "")

    /**
     * Hai tên đã chuẩn hoá ([norm]) là cùng một game: giống hệt, hoặc một tên chứa tên kia và độ dài xấp xỉ
     * (bản Remastered, Edition...). Phần thừa là số hoặc số La Mã thì là phần khác của series, không khớp.
     */
    fun same(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val lo = minOf(a.length, b.length)
        if (lo < 6 || lo.toFloat() / maxOf(a.length, b.length) < 0.72f) return false
        val longer = if (a.length >= b.length) a else b
        val shorter = if (a.length >= b.length) b else a
        if (!longer.contains(shorter)) return false
        val extra = longer.replaceFirst(shorter, "")
        return extra.none { it.isDigit() } && !ROMAN.matches(extra)
    }

    /** Ảnh trơn một màu (độ sáng gần như không đổi) thì không phải ảnh bìa. [lum] là độ sáng 0..255 của các điểm ảnh. */
    fun isFlat(lum: IntArray): Boolean {
        if (lum.isEmpty()) return true
        val mean = lum.sum().toDouble() / lum.size
        var v = 0.0
        for (x in lum) v += (x - mean) * (x - mean)
        return Math.sqrt(v / lum.size) < 6.0
    }
}
