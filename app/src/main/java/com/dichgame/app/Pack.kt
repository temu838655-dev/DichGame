package com.dichgame.app

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
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

    /** Exact text identity: keep punctuation, word boundaries and meaningful letters. */
    fun literal(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    /** Khoá quá ngắn thì không đưa vào bộ nhớ câu (tránh khớp nhầm câu một chữ). */
    fun usable(key: String): Boolean = key.length >= 3 || (key.length >= 2 && key.any { isCjk(it) })
}

/**
 * Gộp câu theo đúng nội dung gốc. Khi nhập kho VERIFIED, dữ liệu có sẵn
 * (bao gồm bản người chơi tự sửa) được ưu tiên hơn dữ liệu nhập mới.
 */
internal object TranslationLineMerge {
    fun merge(
        existing: List<Pair<String, String>>,
        incoming: List<Pair<String, String>>,
        preserveExisting: Boolean
    ): List<Pair<String, String>> {
        val merged = LinkedHashMap<String, Pair<String, String>>()
        val ordered = if (preserveExisting) incoming + existing else existing + incoming
        for ((source, translated) in ordered) {
            if (Canon.usable(Canon.key(source)) && translated.isNotBlank()) {
                merged[Canon.literal(source)] =
                    source.replace('\t', ' ').replace('\n', ' ') to
                        translated.replace('\t', ' ').replace('\n', ' ')
            }
        }
        return merged.values.toList()
    }
}

private data class LineKey(val letters: String, val numbers: String, val cues: String) {
    companion object {
        fun of(source: String) = LineKey(Canon.key(source), digitSig(source), SubtitleMeaning.signature(source))
    }
}

/** Bộ nhớ câu thoại: "câu gốc -> câu Việt hoá chuẩn". Mục thêm sau đè mục thêm trước. */
class TransMemory(pairs: Iterable<Pair<String, String>>) {
    private val exactMap = HashMap<String, String>()
    private val map = HashMap<LineKey, String>()
    private val ambiguous = HashSet<LineKey>()

    init {
        // Last entry wins only when the actual source is identical.
        for ((s, v) in pairs) {
            if (Canon.usable(Canon.key(s)) && v.isNotBlank()) exactMap[Canon.literal(s)] = v.trim()
        }
        // OCR folding can conflate distinct words ("modern" / "modem").
        // Exclude conflicting translations from the fuzzy index entirely.
        for ((s, v) in exactMap) {
            val k = LineKey.of(s)
            if (k in ambiguous) continue
            val prev = map[k]
            if (prev != null && prev != v) {
                map.remove(k)
                ambiguous.add(k)
            } else map[k] = v
        }
    }

    /** Kết quả tra: [fuzzy] = khớp gần đúng (chữ đọc từ màn hình hơi lệch so với câu trong gói), không phải khớp đúng khoá. */
    class Hit(val vi: String, val fuzzy: Boolean)

    private val fuzzyIndex: FuzzyIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { FuzzyIndex(map) }

    /** Dựng sẵn chỉ mục khớp gần đúng (gọi ở luồng nền khi bắt đầu dịch) để lần tra đầu tiên không bị khựng. */
    fun warm() { if (map.size > 1) fuzzyIndex }

    val size: Int get() = exactMap.size

    /** Câu gốc toàn chữ hoa thì bản dịch cũng viết hoa (tiêu đề, thông báo trên màn hình). */
    private fun matchCase(src: String, vi: String): String {
        val letters = src.filter { it.isLetter() }
        return if (letters.isNotEmpty() && letters.none { it.isLowerCase() } && letters.any { it.isUpperCase() }) vi.uppercase(Locale.ROOT) else vi
    }

    private fun part(text: String): String? {
        exactMap[Canon.literal(text)]?.let { return matchCase(text, it) }
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
                val v = exactMap[Canon.literal(chunk)]
                if (v != null) cover(j)?.let { res = listOf(matchCase(chunk, v)) + it }
                j--
            }
            memo[i] = res
            return res
        }
        return cover(0)?.joinToString(" ")
    }

    /**
     * Bản Việt hoá nếu cả đoạn chữ (hoặc từng câu trong đó) có trong bộ nhớ, ngược lại null. Khớp đúng khoá trước; không được thì khớp
     * gần đúng cho câu dài (chịu được chữ đọc lệch vài ký tự, thiếu hoặc thừa chút ít), chỉ nhận khi gần như chắc chắn là cùng một câu.
     */
    fun lookup(text: String): String? = lookupEx(text)?.vi

    fun lookupEx(text: String): Hit? {
        if (exactMap.isEmpty() || text.isBlank()) return null
        exact(text)?.let { return Hit(it, false) }
        val q = LineKey.of(text)
        if (q in ambiguous) return null
        // Even a short OCR-folded match is not exact and must never be narrated as certain.
        map[q]?.let { return Hit(matchCase(text, it), true) }
        if (q.letters.length < FuzzyIndex.minLen(q.letters)) return null
        return fuzzyIndex.find(q)?.let { Hit(matchCase(text, it), true) }
    }

    private fun exact(text: String): String? {
        part(text)?.let { return it }
        val parts = text.split(Regex("[\\r\\n]+|(?<=[.!?。！？])\\s*")).map { it.trim() }.filter { Canon.key(it).isNotEmpty() }
        if (parts.size < 2) return null
        val out = ArrayList<String>(parts.size)
        for (pt in parts) out.add(part(pt) ?: return null)
        return out.joinToString(" ")
    }
}

/**
 * Các số trong câu, dùng để khớp gần đúng không nhầm "Take 5" với "Take 6". Chữ số lọt giữa hai chữ cái ("bef0re", "H2O") bị bỏ qua:
 * đó thường là chữ bị đọc nhầm (0 thành o, 1 thành l, 5 thành s), không phải một con số.
 */
internal fun digitSig(s: String): String {
    val groups = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        if (!s[i].isDigit()) { i++; continue }
        val a = i
        while (i < s.length && s[i].isDigit()) i++
        val left = a > 0 && s[a - 1].isLetter()
        val right = i < s.length && s[i].isLetter()
        if (!(left && right)) groups.add(s.substring(a, i))
    }
    return groups.joinToString("|")
}

/**
 * Chỉ mục khớp gần đúng cho câu dài. Mỗi câu gốc được cắt thành các đoạn 4 ký tự; chỉ giữ khoảng một phần tư số đoạn (chọn theo nội dung,
 * không theo vị trí, nên câu bị cắt đầu hoặc đuôi vẫn chọn đúng các đoạn như cũ) cộng đoạn đầu và đoạn cuối. Tra: các đoạn của câu cần tìm
 * "bỏ phiếu" cho các câu trong gói, rồi chỉ so kỹ (khoảng cách chỉnh sửa) vài câu nhiều phiếu nhất.
 * Nhận khi giống từ 92% (câu dưới 40 ký tự) hoặc 88% (câu dài hơn), cùng các chữ số, và không có câu thứ hai khác nghĩa giống ngang ngửa.
 */
private class FuzzyIndex(map: Map<LineKey, String>) {
    private val keys: Array<LineKey>
    private val vals: Array<String>
    private val post: LongArray

    init {
        val ks = ArrayList<LineKey>()
        val vs = ArrayList<String>()
        for ((k, v) in map) if (k.letters.length >= minLen(k.letters)) { ks.add(k); vs.add(v) }
        keys = ks.toTypedArray(); vals = vs.toTypedArray()
        var buf = LongArray(1 shl 16)
        var n = 0
        for (id in keys.indices) {
            forGrams(keys[id].letters) { h ->
                if (n == buf.size) buf = buf.copyOf(n * 2)
                buf[n++] = ((h.toLong() and 0xFFFFFFFFL) shl 32) or id.toLong()
            }
        }
        java.util.Arrays.sort(buf, 0, n)
        var m = 0
        for (i in 0 until n) if (i == 0 || buf[i] != buf[i - 1]) buf[m++] = buf[i]
        post = buf.copyOf(m)
    }

    private fun lowerBound(x: Long): Int {
        var lo = 0
        var hi = post.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (post[mid] < x) lo = mid + 1 else hi = mid }
        return lo
    }

    fun find(query: LineKey): String? {
        val q = query.letters
        if (keys.isEmpty()) return null
        val votes = HashMap<Int, Int>()
        val seen = HashSet<Int>()
        var selected = 0
        forGrams(q) { h ->
            if (seen.add(h)) {
                val hl = (h.toLong() and 0xFFFFFFFFL) shl 32
                val from = lowerBound(hl)
                var end = from
                while (end < post.size && (post[end] and -0x100000000L) == hl && end - from <= MAX_POSTINGS) end++
                // đoạn có mặt ở quá nhiều câu thì không giúp phân biệt: bỏ, không tính phiếu
                if (end - from > MAX_POSTINGS) return@forGrams
                selected++
                for (i in from until end) {
                    val id = (post[i] and 0xFFFFFFFFL).toInt()
                    votes[id] = (votes[id] ?: 0) + 1
                }
            }
        }
        if (selected == 0) return null
        val need = maxOf(2, selected / 4)
        val top = votes.entries.filter { (id, count) ->
            count >= need && keys[id].numbers == query.numbers && keys[id].cues == query.cues
        }.sortedByDescending { it.value }.take(TOP)
        var bestId = -1
        var best = 0f
        var second = 0f
        var secondVi: String? = null
        for ((id, _) in top) {
            val k = keys[id].letters
            if (kotlin.math.abs(k.length - q.length) * 4 > maxOf(k.length, q.length)) continue
            val sim = similarity(q, k)
            val th = if (k.length < 40) 0.92f else 0.88f
            if (sim < th) continue
            if (sim > best) { second = best; secondVi = if (bestId >= 0) vals[bestId] else null; best = sim; bestId = id }
            else if (sim > second) { second = sim; secondVi = vals[id] }
        }
        if (bestId < 0) return null
        // hai câu khác nghĩa giống ngang ngửa: không chắc câu nào, để bộ dịch xử lý
        if (secondVi != null && secondVi != vals[bestId] && second >= best - 0.02f) return null
        return vals[bestId]
    }

    companion object {
        private const val N = 4
        private const val TOP = 6
        private const val MAX_POSTINGS = 64
        private const val MAX_LEN = 700

        /** Độ dài khoá tối thiểu để được khớp gần đúng: câu ngắn quá thì chỉ nhận khớp đúng. */
        fun minLen(key: String): Int = if (key.any { isCjk(it) }) 8 else 18

        private fun forGrams(k: String, f: (Int) -> Unit) {
            val cjk = k.any { isCjk(it) }
            val n = if (cjk) 2 else N
            val last = k.length - n
            if (last < 0) return
            val shift = if (cjk) 31 else 30
            for (i in 0..last) {
                var h = 0
                for (j in i until i + n) h = h * 31 + k[j].code
                h *= -1640531535
                if (i == 0 || i == last || (h ushr shift) == 0) f(h)
            }
        }

        /** Tỉ lệ giống nhau (1 trừ khoảng cách chỉnh sửa chia độ dài dài hơn). Quá dài thì coi như không giống. */
        fun similarity(a: String, b: String): Float {
            if (a == b) return 1f
            val n = a.length
            val m = b.length
            if (n > MAX_LEN || m > MAX_LEN || n == 0 || m == 0) return 0f
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                for (j in 1..m) {
                    val c = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + c)
                }
                val t = prev; prev = cur; cur = t
            }
            return 1f - prev[m].toFloat() / maxOf(n, m)
        }
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

    const val MAX_LINES = 100000
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

    // ---------------- nhập file Việt hoá của nơi khác ----------------

    /** Chữ trong file: nhận BOM UTF-8 và UTF-16, thử UTF-8 chặt, không được thì coi là Windows-1258 (file Việt hoá cũ từ Windows). */
    fun decode(b: ByteArray): String {
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        val start = if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) 3 else 0
        try {
            val dec = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(b, start, b.size - start)).toString()
        } catch (_: CharacterCodingException) {
        }
        val cs = try { Charset.forName("windows-1258") } catch (_: Exception) { Charsets.ISO_8859_1 }
        return String(b, start, b.size - start, cs)
    }

    internal fun hasSection(raw: String): Boolean = raw.lineSequence().any { l0 ->
        val l = l0.trim()
        l.startsWith("[") && l.endsWith("]") && l.length > 2 && section(l.substring(1, l.length - 1)) != null
    }

    internal val SRC = setOf("original", "source", "src", "orig", "en", "eng", "english", "text", "msgid", "from", "gốc", "câu gốc", "tiếng anh", "nguồn")
    internal val DST = setOf("vi", "vn", "vie", "vietnamese", "translation", "translated", "target", "tgt", "dst", "msgstr", "to", "việt", "dịch", "bản dịch", "tiếng việt", "việt hoá", "việt hóa")

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                when (n) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'u' -> {
                        val h = if (i + 6 <= s.length) s.substring(i + 2, i + 6).toIntOrNull(16) else null
                        if (h != null) { sb.append(h.toChar()); i += 6 } else { sb.append(c); i++ }
                    }
                    else -> { sb.append(n); i += 2 }
                }
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private val JSON_STR = "\"((?:[^\"\\\\]|\\\\.)*)\""

    /** JSON: mảng các đối tượng {"original": "...", "vi": "..."} hoặc bảng phẳng {"câu gốc": "câu Việt"}. */
    private fun fromJson(t: String): List<Pair<String, String>> {
        val kv = Regex("$JSON_STR\\s*:\\s*$JSON_STR")
        val objs = Regex("\\{([^{}]*)\\}").findAll(t).toList()
        val out = ArrayList<Pair<String, String>>()
        if (objs.size == 1 && t.trimStart().startsWith("{") && objs[0].value.length >= t.trim().length - 1) {
            for (m in kv.findAll(objs[0].groupValues[1])) {
                val a = unescape(m.groupValues[1]).trim(); val b = unescape(m.groupValues[2]).trim()
                if (a.any { it.isLetter() } && b.isNotEmpty()) out.add(a to b)
            }
            return out
        }
        for (o in objs) {
            var src: String? = null
            var dst: String? = null
            for (m in kv.findAll(o.groupValues[1])) {
                val k = unescape(m.groupValues[1]).trim().lowercase(Locale.ROOT)
                val v = unescape(m.groupValues[2]).trim()
                if (k in SRC && src == null) src = v
                if (k in DST && dst == null) dst = v
            }
            if (!src.isNullOrEmpty() && !dst.isNullOrEmpty()) out.add(src to dst)
        }
        return out
    }

    private fun poUnquote(s: String): String {
        val t = s.trim()
        return if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) unescape(t.substring(1, t.length - 1)) else unescape(t)
    }

    /** gettext .po: msgid "..." / msgstr "..." (có thể nhiều dòng). */
    private fun fromPo(raw: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val id = StringBuilder()
        val str = StringBuilder()
        var mode = 0 // 1 đang đọc msgid, 2 đang đọc msgstr, 3 bỏ qua (dạng số nhiều)
        fun flush() {
            if (id.isNotEmpty() && str.isNotBlank()) out.add(id.toString().trim() to str.toString().trim())
            id.setLength(0); str.setLength(0)
        }
        for (line in raw.lines()) {
            val l = line.trim()
            when {
                l.startsWith("msgid_plural") -> mode = 3
                l.startsWith("msgid ") || l == "msgid" -> { if (mode == 2) flush(); mode = 1; id.setLength(0); id.append(poUnquote(l.removePrefix("msgid"))) }
                l.startsWith("msgstr[0]") -> { mode = 2; str.append(poUnquote(l.removePrefix("msgstr[0]"))) }
                l.startsWith("msgstr[") -> mode = 3
                l.startsWith("msgstr") -> { mode = 2; str.append(poUnquote(l.removePrefix("msgstr"))) }
                l.startsWith("\"") -> { if (mode == 1) id.append(poUnquote(l)) else if (mode == 2) str.append(poUnquote(l)) }
                l.isEmpty() -> { if (mode == 2 || mode == 3) flush(); mode = 0 }
            }
        }
        flush()
        return out
    }

    private fun csvRow(line: String, d: Char): List<String> {
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (q) {
                if (c == '"') { if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else q = false } else sb.append(c)
            } else if (c == '"' && sb.isEmpty()) q = true
            else if (c == d) { cells.add(sb.toString()); sb.setLength(0) }
            else sb.append(c)
            i++
        }
        cells.add(sb.toString())
        return cells
    }

    /** CSV riêng của DichGame có tên game để ngăn nhập chéo giữa các trò chơi. */
    fun formatScopedCsv(game: String, lines: List<Pair<String, String>>): String {
        require(game.isNotBlank()) { "Hãy chọn game trước khi xuất CSV." }
        fun cell(s: String): String = "\"" + s.replace('\r', ' ').replace('\n', ' ').replace("\"", "\"\"") + "\""
        return buildString {
            append("source,vi,game\r\n")
            for ((original, vi) in lines) {
                append(cell(original)).append(',').append(cell(vi)).append(',').append(cell(game)).append("\r\n")
            }
        }
    }

    fun parseScopedCsv(raw: String): Pack? {
        val rows = raw.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.iterator()
        if (!rows.hasNext()) return null
        val header = csvRow(rows.next(), ',').map { it.trim().lowercase(Locale.ROOT) }
        if (header != listOf("source", "vi", "game")) return null
        val pairs = ArrayList<Pair<String, String>>()
        var game = ""
        while (rows.hasNext()) {
            val cells = csvRow(rows.next(), ',')
            require(cells.size == 3) { "CSV DichGame phải có đúng 3 cột source, vi, game." }
            val source = cells[0].trim()
            val translated = cells[1].trim()
            val rowGame = cells[2].trim()
            require(source.isNotEmpty() && translated.isNotEmpty() && rowGame.isNotEmpty()) {
                "CSV DichGame có dòng thiếu câu gốc, bản dịch hoặc tên game."
            }
            require(rowGame.length <= 200) { "Tên game trong CSV quá dài." }
            if (game.isEmpty()) game = rowGame
            require(GamePackMatch.selected(game, rowGame)) { "CSV chứa nhiều game. Hãy tách riêng từng game." }
            require(pairs.size < MAX_LINES) { "CSV vượt quá giới hạn số câu." }
            pairs.add(source to translated)
        }
        require(pairs.isNotEmpty()) { "CSV DichGame chưa có câu dịch nào." }
        return Pack(game, emptyList(), emptyList(), pairs)
    }

    /** Bảng TSV hoặc CSV: cột câu gốc, cột câu Việt (tự nhận qua dòng tiêu đề nếu có, không thì hai cột đầu). */
    private fun fromTable(raw: String): List<Pair<String, String>>? {
        val rows = raw.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (rows.size < 2) return null
        val sample = rows.take(40)
        var delim: Char? = null
        if (sample.count { it.contains('\t') } * 10 >= sample.size * 8) delim = '\t'
        else for (d in charArrayOf(';', ',')) {
            val counts = sample.map { csvRow(it, d).size }
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            if (mode.key >= 2 && mode.value * 10 >= sample.size * 9 && sample.count { it.contains(" = ") } * 2 < sample.size) { delim = d; break }
        }
        val d = delim ?: return null
        var si = 0
        var di = 1
        var from = 0
        val head = csvRow(rows[0], d).map { it.trim().lowercase(Locale.ROOT) }
        val hs = head.indexOfFirst { it in SRC }
        val hd = head.indexOfFirst { it in DST }
        if (hs >= 0 && hd >= 0 && hs != hd) { si = hs; di = hd; from = 1 }
        val out = ArrayList<Pair<String, String>>()
        for (r in rows.drop(from)) {
            val c = csvRow(r, d)
            if (c.size <= maxOf(si, di)) continue
            val a = c[si].trim(); val b = c[di].trim()
            if (a.isNotEmpty() && b.isNotEmpty()) out.add(a to b)
        }
        return out
    }

    /**
     * Nhập file Việt hoá: nhận dạng của app (có mục [thuật ngữ] / [nhân vật] / [câu thoại]) và các dạng phổ biến của nơi khác:
     * JSON, gettext .po, bảng CSV/TSV (cột câu gốc, cột câu Việt). File chỉ có dòng "gốc = Việt" không có mục thì câu dài
     * tính là câu thoại, từ ngắn tính là thuật ngữ. Chỉ lấy cặp câu chữ; bản vá (mod) sửa file game thì app không đọc được.
     */
    fun parseAny(raw0: String): Pack {
        val raw = raw0.removePrefix("\uFEFF")
        if (hasSection(raw)) return parse(raw)
        val t = raw.trimStart()
        val pairs: List<Pair<String, String>>? = when {
            t.startsWith("{") || t.startsWith("[") -> fromJson(t)
            Regex("(?m)^\\s*msgid\\s+\"").containsMatchIn(raw) -> fromPo(raw)
            else -> null
        }
        if (!pairs.isNullOrEmpty()) return Pack("", emptyList(), emptyList(), pairs.take(MAX_LINES))
        fromTable(raw)?.let { if (it.isNotEmpty()) return Pack("", emptyList(), emptyList(), it.take(MAX_LINES)) }
        val p = parse(raw)
        val long = p.terms.filter { it.first.split(Regex("\\s+")).size >= 4 || it.first.length >= 28 }
        if (long.isEmpty()) return p
        return Pack(p.name, p.terms - long.toSet(), p.cast, (p.lines + long).take(MAX_LINES))
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
