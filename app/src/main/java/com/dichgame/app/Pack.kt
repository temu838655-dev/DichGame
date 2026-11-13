package com.dichgame.app

import java.text.Normalizer
import java.util.Locale

/*
 * Phần lõi của gói Việt hoá, không dùng gì của Android nên kiểm tra được trên máy tính:
 *  - Canon: khoá so khớp câu chịu được lỗi đọc chữ (OCR).
 *  - TransMemory: "bộ nhớ câu thoại", câu nào khớp thì dùng luôn bản Việt hoá chuẩn, khỏi dịch.
 *  - TermMap: bảng thuật ngữ, ép đúng tên riêng cả khi dịch bằng AI lẫn dịch trên máy.
 *  - PackText: đọc và ghi file gói (nhập từ file hoặc link, xuất để chia sẻ).
 */

private fun isCjk(c: Char): Boolean {
    val s = java.lang.Character.UnicodeScript.of(c.code)
    return s == java.lang.Character.UnicodeScript.HAN || s == java.lang.Character.UnicodeScript.HIRAGANA ||
        s == java.lang.Character.UnicodeScript.KATAKANA || s == java.lang.Character.UnicodeScript.HANGUL
}

object Canon {
    /**
     * Rút câu về dạng so khớp: viết thường, bỏ dấu câu và khoảng trắng, gộp các ký tự hay bị đọc nhầm
     * (0 và O, 1 và I và l, 5 và S, rn và m, vv và w). Hai câu giống nhau sau khi đọc lệch vài ký tự sẽ ra cùng khoá.
     */
    fun key(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val sb = StringBuilder(n.length)
        for (ch in n) {
            when {
                ch == '0' -> sb.append('o')
                ch == '1' || ch == 'i' || ch == '|' || ch == 'ı' -> sb.append('l')
                ch == '5' -> sb.append('s')
                ch.isLetterOrDigit() -> sb.append(ch)
            }
        }
        return sb.toString().replace("rn", "m").replace("vv", "w")
    }

    /** Khoá quá ngắn thì không đưa vào bộ nhớ câu (tránh khớp nhầm câu một chữ). */
    fun usable(key: String): Boolean = key.length >= 3 || (key.length >= 2 && key.any { isCjk(it) })
}

/** Bộ nhớ câu thoại: "câu gốc -> câu Việt hoá chuẩn". Mục thêm sau đè mục thêm trước. */
class TransMemory(pairs: Iterable<Pair<String, String>>) {
    private val map = HashMap<String, String>()

    init {
        for ((s, v) in pairs) {
            val k = Canon.key(s)
            if (Canon.usable(k) && v.isNotBlank()) map[k] = v.trim()
        }
    }

    val size: Int get() = map.size

    /** Câu gốc toàn chữ hoa thì bản dịch cũng viết hoa (tiêu đề, thông báo trên màn hình). */
    private fun matchCase(src: String, vi: String): String {
        val letters = src.filter { it.isLetter() }
        return if (letters.isNotEmpty() && letters.none { it.isLowerCase() } && letters.any { it.isUpperCase() }) vi.uppercase(Locale.ROOT) else vi
    }

    private fun part(text: String): String? {
        map[Canon.key(text)]?.let { return matchCase(text, it) }
        // nhiều thông báo dính liền không dấu câu ("YOU DIED Press any button"): cắt theo từ
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2 || words.size > 16) return null
        val memo = HashMap<Int, List<String>?>()
        fun cover(i: Int): List<String>? {
            if (i == words.size) return emptyList()
            if (memo.containsKey(i)) return memo[i]
            var res: List<String>? = null
            var j = minOf(words.size, i + 8)
            while (j > i && res == null) {
                val chunk = words.subList(i, j).joinToString(" ")
                val v = map[Canon.key(chunk)]
                if (v != null) cover(j)?.let { res = listOf(matchCase(chunk, v)) + it }
                j--
            }
            memo[i] = res
            return res
        }
        return cover(0)?.joinToString(" ")
    }

    /** Bản Việt hoá nếu cả đoạn chữ (hoặc từng câu trong đó) đều có trong bộ nhớ, ngược lại null. */
    fun lookup(text: String): String? {
        if (map.isEmpty() || text.isBlank()) return null
        part(text)?.let { return it }
        val parts = text.split(Regex("[\\r\\n]+|(?<=[.!?。！？])\\s*")).map { it.trim() }.filter { Canon.key(it).isNotEmpty() }
        if (parts.size < 2) return null
        val out = ArrayList<String>(parts.size)
        for (pt in parts) out.add(part(pt) ?: return null)
        return out.joinToString(" ")
    }
}

/** Bảng thuật ngữ "gốc = tiếng Việt". Không phân biệt hoa thường; tên Latin khớp theo cả từ. */
class TermMap(pairs: List<Pair<String, String>>) {
    class Protected(val text: String, val tokens: List<Pair<String, String>>)

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private val entries: List<Pair<String, String>> = pairs
        .map { it.first.trim() to it.second.trim() }
        .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        .distinctBy { norm(it.first) }
        .sortedByDescending { it.first.length }

    private val byKey: Map<String, Pair<String, String>> = entries.associateBy { norm(it.first) }

    val size: Int get() = entries.size
    val all: List<Pair<String, String>> get() = entries

    private fun pattern(src: String): String {
        val body = src.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        val lead = if (src.first().let { it.isLetterOrDigit() && !isCjk(it) }) "(?<![\\p{L}\\p{N}])" else ""
        val trail = if (src.last().let { it.isLetterOrDigit() && !isCjk(it) }) "(?![\\p{L}\\p{N}])" else ""
        return "(?:$lead$body$trail)"
    }

    private val regex: Regex? by lazy {
        if (entries.isEmpty()) null
        else Regex(entries.joinToString("|") { pattern(it.first) }, setOf(RegexOption.IGNORE_CASE))
    }

    /** Thuật ngữ nằm trong [text] đứng trước (cái dài nhất trước), rồi thêm vài thuật ngữ khác cho nhất quán; tối đa [max]. */
    fun relevant(text: String, max: Int = 30, extra: Int = 6): List<Pair<String, String>> {
        val rx = regex ?: return emptyList()
        val hit = LinkedHashMap<String, Pair<String, String>>()
        for (m in rx.findAll(text)) byKey[norm(m.value)]?.let { hit.putIfAbsent(norm(m.value), it) }
        val out = ArrayList<Pair<String, String>>(hit.values.take(max))
        if (out.size < max) {
            for (e in entries.sortedBy { it.first.length }) {
                if (out.size >= minOf(max, hit.size + extra)) break
                if (norm(e.first) !in hit) out.add(e)
            }
        }
        return out
    }

    /** Thay các thuật ngữ trong câu gốc bằng mã giữ chỗ để bộ dịch trên máy không dịch bậy; xong dùng [restore]. */
    fun protect(text: String): Protected {
        val rx = regex ?: return Protected(text, emptyList())
        val toks = ArrayList<Pair<String, String>>()
        val out = rx.replace(text) { m ->
            val e = byKey[norm(m.value)]
            if (e == null) m.value else {
                val i = toks.size
                val t = "Qzx" + ('a' + (i / 26 % 26)) + ('a' + (i % 26))
                toks.add(t to e.second)
                t
            }
        }
        return Protected(out, toks)
    }

    /** Đưa tiếng Việt vào chỗ các mã giữ chỗ. Trả về câu và số mã bị bộ dịch làm mất (0 là ổn). */
    fun restore(translated: String, p: Protected): Pair<String, Int> {
        var s = translated
        var lost = 0
        for ((t, vi) in p.tokens) {
            val r = Regex(Regex.escape(t), RegexOption.IGNORE_CASE)
            if (r.containsMatchIn(s)) s = r.replace(s) { vi } else lost++
        }
        return s to lost
    }

    private val fixRegex: Regex? by lazy {
        val l = entries.filter { it.first.length >= 3 && it.first.any { c -> c.isLetter() && !isCjk(c) } && norm(it.first) != norm(it.second) }
        if (l.isEmpty()) null else Regex(l.joinToString("|") { pattern(it.first) }, setOf(RegexOption.IGNORE_CASE))
    }

    /** Bản dịch AI còn sót tên gốc chưa Việt hoá thì đổi sang đúng thuật ngữ. */
    fun fixOutput(out: String): String {
        val rx = fixRegex ?: return out
        return rx.replace(out) { m -> byKey[norm(m.value)]?.second ?: m.value }
    }
}

/** Đọc và ghi file gói Việt hoá (văn bản thường, UTF-8, dễ tự soạn hoặc chia sẻ). */
object PackText {
    class Pack(val name: String, val terms: List<Pair<String, String>>, val cast: List<String>, val lines: List<Pair<String, String>>) {
        val isEmpty: Boolean get() = terms.isEmpty() && cast.isEmpty() && lines.isEmpty()
    }

    private fun section(h: String): Char? = when (h.trim().lowercase(Locale.ROOT)) {
        "thuật ngữ", "thuat ngu", "terms", "glossary", "từ điển" -> 'T'
        "nhân vật", "nhan vat", "cast", "characters" -> 'C'
        "câu thoại", "cau thoai", "lines", "câu", "bộ nhớ", "memory" -> 'L'
        else -> null
    }

    const val MAX_LINES = 60000
    const val MAX_TERMS = 4000

    fun parse(raw: String): Pack {
        var name = ""
        val terms = ArrayList<Pair<String, String>>()
        val cast = ArrayList<String>()
        val lines = ArrayList<Pair<String, String>>()
        var sec = 'T'
        for (l0 in raw.removePrefix("﻿").lines()) {
            val l = l0.trim()
            if (l.isEmpty() || l.startsWith("#")) continue
            if (l.startsWith("[") && l.endsWith("]")) { section(l.substring(1, l.length - 1))?.let { sec = it }; continue }
            val low = l.lowercase(Locale.ROOT)
            if (low.startsWith("tên:") || low.startsWith("name:") || low.startsWith("game:")) { name = l.substringAfter(':').trim(); continue }
            when (sec) {
                'T' -> {
                    val i = l.indexOf('=')
                    if (i > 0 && terms.size < MAX_TERMS) {
                        val a = l.substring(0, i).trim(); val b = l.substring(i + 1).trim()
                        if (a.isNotEmpty() && b.isNotEmpty()) terms.add(a to b)
                    }
                }
                'C' -> if (l.indexOf('=') > 0 && cast.size < 500) cast.add(l)
                'L' -> if (lines.size < MAX_LINES) {
                    val tab = l.indexOf('\t')
                    val sp = l.indexOf(" = ")
                    val i = if (tab > 0) tab else if (sp > 0) sp else l.indexOf('=')
                    if (i > 0) {
                        val w = if (i == tab) 1 else if (i == sp) 3 else 1
                        val a = l.substring(0, i).trim(); val b = l.substring(i + w).trim()
                        if (a.isNotEmpty() && b.isNotEmpty()) lines.add(a to b)
                    }
                }
            }
        }
        return Pack(name, terms, cast, lines)
    }

    fun format(name: String, terms: String, cast: String, lines: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("# Gói Việt hoá cho app Dịch game (văn bản thường, UTF-8)\n")
        sb.append("# [thuật ngữ] gốc = tiếng Việt   |   [nhân vật] Tên = mô tả giọng, xưng hô; nhịp: chậm/vừa/nhanh   |   [câu thoại] câu gốc = câu Việt hoá\n")
        if (name.isNotBlank()) sb.append("tên: ").append(name.trim()).append('\n')
        sb.append("\n[thuật ngữ]\n")
        for (l in terms.lines()) if (l.contains('=')) sb.append(l.trim()).append('\n')
        sb.append("\n[nhân vật]\n")
        for (l in cast.lines()) if (l.contains('=')) sb.append(l.trim()).append('\n')
        sb.append("\n[câu thoại]\n")
        for ((a, b) in lines) sb.append(a.replace('\n', ' ')).append(" = ").append(b.replace('\n', ' ')).append('\n')
        return sb.toString()
    }
}
