package com.dichgame.app

import java.io.InputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Đổi chữ tiếng Việt thành chuỗi phiên âm (kiểu eSpeak/IPA) để giọng Piper đọc.
 *
 * Thư viện sherpa-onnx bản mới không còn gói eSpeak, nên app tự làm bước này bằng bảng
 * "âm tiết -> phiên âm" (assets/vi_phon.txt, tạo bằng tools/gen_vi_phon.py). Mỗi tiếng Việt là một âm tiết
 * nên chỉ cần tra bảng; số, ký hiệu và chữ lạ được xử lý riêng bên dưới.
 *
 * Lớp này không dùng gì của Android để dễ kiểm tra trên máy tính.
 */
class ViPhon(table: InputStream) {
    private val map = HashMap<String, String>(40000)
    private val plain = HashSet<String>(8000)
    private var maxPlain = 1

    init {
        table.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val t = line.indexOf('\t')
                if (t <= 0) continue
                val k = line.substring(0, t)
                map[k] = line.substring(t + 1)
                if (k[0] != '#' && k.none { it in TONED }) {
                    plain.add(k)
                    if (k.length > maxPlain) maxPlain = k.length
                }
            }
        }
    }

    val size: Int get() = map.size

    /** Mỗi phần tử là phiên âm của một câu, dạng mã Unicode, sẵn để đưa cho sherpa-onnx (phonemeCodepoints). */
    fun sentences(text: String): List<IntArray> {
        val s = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        val out = ArrayList<IntArray>()
        val sb = StringBuilder()
        var words = 0

        fun flush(end: Char) {
            if (words > 0) {
                var str = sb.toString().trimEnd()
                while (str.isNotEmpty() && str.last() in ",;:") str = str.dropLast(1).trimEnd()
                if (str.isNotEmpty()) out.add((str + end).codePoints().toArray())
            }
            sb.setLength(0)
            words = 0
        }

        fun addWord(ph: String) {
            if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            sb.append(ph)
            words++
        }

        fun addWords(list: List<String>) { for (w in list) for (ph in wordPhonemes(w)) addWord(ph) }

        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c.isDigit() -> {
                    var j = i
                    while (j < n && s[j].isDigit()) j++
                    while (j + 1 < n && (s[j] == '.' || s[j] == ',') && s[j + 1].isDigit()) {
                        j++
                        while (j < n && s[j].isDigit()) j++
                    }
                    addWords(numberWords(s.substring(i, j)))
                    i = j
                    if (i < n && s[i] == '%') { addWords(listOf("phần", "trăm")); i++ }
                }
                isWordChar(c) -> {
                    var j = i
                    while (j < n && (isWordChar(s[j]) || ((s[j] == '\'' || s[j] == '’') && j + 1 < n && isWordChar(s[j + 1])))) j++
                    val w = s.substring(i, j).replace("'", "").replace("’", "")
                    addWords(listOf(w))
                    i = j
                }
                c == '.' || c == '…' -> {
                    var j = i
                    while (j < n && (s[j] == '.' || s[j] == '…')) j++
                    val many = j - i > 1 || c == '…'
                    // "..." giữa câu là chỗ ngừng; cuối câu là dấu chấm
                    val atEnd = s.substring(j).none { it.isLetterOrDigit() }
                    if (many && !atEnd) { if (words > 0) sb.append(", ") } else flush('.')
                    i = j
                }
                c == '!' || c == '?' -> {
                    var j = i
                    while (j < n && (s[j] == '!' || s[j] == '?')) j++
                    flush(if (s.substring(i, j).contains('?')) '?' else '!')
                    i = j
                }
                c == ',' || c == ';' || c == ':' || c == '—' || c == '–' || c == '(' || c == ')' -> {
                    if (words > 0 && sb.isNotEmpty() && sb.last() != ' ') sb.append(if (c == ';') "; " else if (c == ':') ": " else ", ")
                    i++
                }
                c == '&' -> { addWords(listOf("và")); i++ }
                c == '+' -> { addWords(listOf("cộng")); i++ }
                c == '=' -> { addWords(listOf("bằng")); i++ }
                else -> i++
            }
        }
        flush('.')
        return out
    }

    /** Phiên âm của một "từ" viết bằng chữ cái. Lạ thì tách thành các âm tiết quen, còn lại đánh vần. */
    internal fun wordPhonemes(w: String): List<String> {
        if (w.isEmpty()) return emptyList()
        map[w]?.let { return listOf(it) }
        val bare = stripTone(w)
        if (bare != w) map[bare]?.let { return listOf(it) }
        val res = ArrayList<String>()
        val x = bare
        var i = 0
        while (i < x.length) {
            var hit = false
            var len = minOf(maxPlain, x.length - i)
            while (len >= 1) {
                val sub = x.substring(i, i + len)
                if (sub in plain) {
                    res.add(map[sub]!!)
                    i += len
                    hit = true
                    break
                }
                len--
            }
            if (!hit) {
                map["#" + x[i]]?.let { res.add(it) }
                i++
            }
        }
        return res
    }

    companion object {
        private const val TONED = "áàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ"

        private fun isWordChar(c: Char) = c.isLetter() || c in '̀'..'ͯ'

        private fun stripTone(w: String): String {
            val d = Normalizer.normalize(w, Normalizer.Form.NFD)
            val sb = StringBuilder(d.length)
            for (ch in d) if (ch != '̀' && ch != '́' && ch != '̉' && ch != '̃' && ch != '̣') sb.append(ch)
            return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
        }

        private val DIGITS = arrayOf("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")
        private val BIG = arrayOf("", "nghìn", "triệu", "tỷ")

        /** "1.250" -> một nghìn hai trăm năm mươi; "3,5" -> ba phẩy năm; số dài quá thì đọc từng số. */
        fun numberWords(tok: String): List<String> {
            val out = ArrayList<String>()
            val thousands = Regex("^\\d{1,3}([.,]\\d{3})+$")
            val body = if (thousands.matches(tok)) tok.replace(".", "").replace(",", "") else tok
            val sep = body.indexOfFirst { it == '.' || it == ',' }
            if (sep > 0) {
                out.addAll(readInt(body.substring(0, sep)))
                out.add(if (body[sep] == ',') "phẩy" else "chấm")
                for (d in body.substring(sep + 1)) if (d.isDigit()) out.add(DIGITS[d - '0'])
                return out
            }
            return readInt(body)
        }

        private fun readInt(digits: String): List<String> {
            val t = digits.trimStart('0')
            if (t.isEmpty()) return listOf(DIGITS[0])
            // quá lớn hoặc số có số 0 đứng đầu (mã, số điện thoại): đọc từng số
            if (t.length > 12 || (digits.length > 1 && digits[0] == '0')) return digits.map { DIGITS[it - '0'] }
            var v = t.toLong()
            val groups = ArrayList<Int>()
            while (v > 0) { groups.add((v % 1000).toInt()); v /= 1000 }
            val res = ArrayList<String>()
            for (g in groups.indices.reversed()) {
                val x = groups[g]
                if (x == 0) continue
                val full = g < groups.size - 1
                res.addAll(readGroup(x, full))
                // 10^9 = "tỷ", 10^12 = "nghìn tỷ"
                val unit = when (g) { 0 -> ""; 1 -> "nghìn"; 2 -> "triệu"; 3 -> "tỷ"; else -> "" }
                if (unit.isNotEmpty()) res.add(unit)
            }
            return res
        }

        private fun readGroup(v: Int, full: Boolean): List<String> {
            val h = v / 100
            val t = (v / 10) % 10
            val u = v % 10
            val r = ArrayList<String>()
            if (h > 0 || full) { r.add(DIGITS[h]); r.add("trăm") }
            if (t > 1) {
                r.add(DIGITS[t]); r.add("mươi")
                if (u == 1) r.add("mốt") else if (u == 4) r.add("tư") else if (u == 5) r.add("lăm") else if (u > 0) r.add(DIGITS[u])
            } else if (t == 1) {
                r.add("mười")
                if (u == 5) r.add("lăm") else if (u > 0) r.add(DIGITS[u])
            } else if (u > 0) {
                if (h > 0 || full) r.add("linh")
                r.add(DIGITS[u])
            }
            return r
        }
    }
}
