package com.dichgame.app

import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Nhận ra chữ tiếng Việt qua các chữ cái chỉ có trong tiếng Việt (không có trong Anh, Pháp, Đức, Tây Ban Nha, Bồ Đào Nha...). */
object Viet {
    /** đ, ơ, ư, ĩ, ũ và khối Latin mở rộng (ạ ả ấ ầ ể ộ ớ ờ ữ ự...). "ă" không tính vì tiếng Rô-ma-ni cũng có. */
    fun strong(c: Char): Boolean =
        c == 'đ' || c == 'Đ' || c == 'ơ' || c == 'Ơ' || c == 'ư' || c == 'Ư' || c == 'ĩ' || c == 'Ĩ' || c == 'ũ' || c == 'Ũ' || c in 'Ạ'..'ỹ'

    fun has(s: String): Boolean {
        val t = if (s.any { it in '̀'..'ͯ' }) Normalizer.normalize(s, Normalizer.Form.NFC) else s
        return t.any { strong(it) }
    }

    /** Tỉ lệ các chuỗi (có chữ cái) chứa chữ đặc trưng tiếng Việt, lấy mẫu tối đa khoảng 500 chuỗi. */
    fun ratio(texts: List<String>): Float {
        val step = maxOf(1, texts.size / 500)
        var n = 0
        var v = 0
        var i = 0
        while (i < texts.size) {
            val t = texts[i]
            i += step
            if (t.none { it.isLetter() }) continue
            n++
            if (has(t)) v++
        }
        return if (n == 0) 0f else v.toFloat() / n
    }

    /** Cột/mặt này là tiếng Việt. Câu thoại gần như luôn có chữ đặc trưng; nhãn menu ngắn thì ít hơn nên ngưỡng thấp. */
    fun isVi(r: Float) = r >= 0.2f

    /** Cột/mặt này chắc chắn không phải tiếng Việt. */
    fun notVi(r: Float) = r < 0.08f
}

/**
 * Đọc một file duy nhất mà bên trong có cả chữ gốc lẫn bản Việt hoá: cột en + vi, JSON mỗi mục hai trường, XLIFF, TMX,
 * .srt hai dòng mỗi mục, dòng xen kẽ (gốc, Việt), dòng "gốc | Việt". Và file .zip chứa cả file gốc lẫn file Việt hoá.
 *
 * Bên nào là tiếng Việt được nhận từ chính chữ (xem [Viet]), không cần đúng thứ tự cột. Bên nào không chắc thì không nhập.
 * Không phụ thuộc Android.
 */
object Bilingual {
    class Found(val pairs: List<Pair<String, String>>, val how: String, val join: LocFormat.Join? = null)

    private val KEY_COL = Regex("^[A-Za-z0-9]+([._:/\\-][A-Za-z0-9]+)+$|^[a-z]+([A-Z][a-z0-9]*)+$")

    /** Phần lớn là mã khoá (menu.start, QUEST_12_TITLE, startGame) chứ không phải chữ hiện trên màn hình. */
    internal fun keyLike(texts: List<String>): Boolean {
        val t = texts.filter { it.isNotBlank() }
        if (t.isEmpty()) return false
        val step = maxOf(1, t.size / 500)
        var n = 0
        var k = 0
        var i = 0
        while (i < t.size) { n++; if (KEY_COL.matches(t[i].trim())) k++; i += step }
        return k * 10 >= n * 6
    }

    /** Xếp cặp theo chiều (câu gốc, câu Việt) bằng cách xem bên nào là tiếng Việt. Không rõ hoặc bên "gốc" là mã khoá thì null. */
    internal fun orient(pairs: List<Pair<String, String>>): List<Pair<String, String>>? {
        if (pairs.size < 2) return null
        val a = pairs.map { it.first }
        val b = pairs.map { it.second }
        val ra = Viet.ratio(a)
        val rb = Viet.ratio(b)
        val out = when {
            Viet.isVi(rb) && Viet.notVi(ra) -> pairs
            Viet.isVi(ra) && Viet.notVi(rb) -> pairs.map { it.second to it.first }
            else -> return null
        }
        if (keyLike(out.map { it.first })) return null
        return out
    }

    private fun score(values: List<String>): Float {
        if (values.isEmpty()) return 0f
        val len = values.sumOf { it.length }.toFloat() / values.size
        val sp = values.count { it.contains(' ') }.toFloat() / values.size
        return len * (sp + 0.1f)
    }

    private fun first(a: Collection<String>, names: Set<String>): String? = a.firstOrNull { it.lowercase(Locale.ROOT) in names }

    /** Một file: nhận ra cách bố trí, trả các cặp (gốc, Việt) hoặc null nếu không phải file hai ngôn ngữ. */
    fun parse(raw0: String, fileName: String = ""): Found? {
        val raw = raw0.removePrefix("﻿")
        if (raw.isBlank()) return null
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val t = raw.trimStart()
        return when {
            raw.contains("<source") && raw.contains("<target") -> fromXliff(raw)
            raw.contains("<tuv") -> fromTmx(raw)
            ext == "rpy" || (raw.contains("translate ") && Regex("(?m)^\\s*new\\s+\"").containsMatchIn(raw)) -> fromRenpy(raw)
            ext == "json" || t.startsWith("{") || t.startsWith("[") -> fromJson(raw)
            ext == "srt" || Regex("(?m)^\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s*-->").containsMatchIn(raw) -> fromSrt(raw)
            else -> fromTable(raw) ?: fromSeparated(raw) ?: fromAlternating(raw)
        }
    }

    // ---------------- XLIFF, TMX ----------------

    private val XLIFF = Regex("<source(?:\\s[^>]*)?>(.*?)</source>\\s*(?:<note(?:\\s[^>]*)?>.*?</note>\\s*)*<target(?:\\s[^>]*)?>(.*?)</target>", RegexOption.DOT_MATCHES_ALL)

    private fun fromXliff(raw: String): Found? {
        val pairs = XLIFF.findAll(raw).map { LocFormat.xmlText(it.groupValues[1]) to LocFormat.xmlText(it.groupValues[2]) }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }.toList()
        return orient(pairs)?.let { Found(it, "XLIFF") }
    }

    private fun fromTmx(raw: String): Found? {
        val tu = Regex("<tu\\b[^>]*>(.*?)</tu>", RegexOption.DOT_MATCHES_ALL)
        val tuv = Regex("<tuv[^>]*?lang=\"([^\"]+)\"[^>]*>.*?<seg[^>]*>(.*?)</seg>", RegexOption.DOT_MATCHES_ALL)
        val out = ArrayList<Pair<String, String>>()
        for (m in tu.findAll(raw)) {
            val v = tuv.findAll(m.groupValues[1]).map { it.groupValues[1].lowercase(Locale.ROOT) to LocFormat.xmlText(it.groupValues[2]) }.toList()
            val vi = v.firstOrNull { it.first.startsWith("vi") }?.second ?: continue
            val src = (v.firstOrNull { it.first.startsWith("en") } ?: v.firstOrNull { !it.first.startsWith("vi") })?.second ?: continue
            if (src.isNotBlank() && vi.isNotBlank()) out.add(src to vi)
        }
        return if (out.isEmpty()) null else Found(out, "TMX")
    }

    // ---------------- Ren'Py (game visual novel): translate xxx: old "..." new "..." ----------------

    private fun unq(s: String): String = s.replace("\\n", " ").replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\")

    private fun fromRenpy(raw: String): Found? {
        val q = "\"((?:[^\"\\\\]|\\\\.)*)\""
        val pairs = ArrayList<Pair<String, String>>()
        for (m in Regex("(?m)^\\s*old\\s+$q\\s*\\r?\\n\\s*new\\s+$q").findAll(raw)) pairs.add(unq(m.groupValues[1]) to unq(m.groupValues[2]))
        // lời thoại: dòng chú thích giữ câu gốc, dòng sau là bản dịch
        val spk = "(?:[A-Za-z_][\\w.]*\\s+)?"
        for (m in Regex("(?m)^\\s*#\\s*$spk$q\\s*\\r?\\n\\s*$spk$q\\s*$").findAll(raw)) pairs.add(unq(m.groupValues[1]) to unq(m.groupValues[2]))
        val ok = pairs.filter { it.first.isNotBlank() && it.second.isNotBlank() }
        return orient(ok)?.let { Found(it, "Ren'Py") }
    }

    // ---------------- gettext .mo (đã biên dịch) ----------------

    fun isMo(b: ByteArray): Boolean = b.size > 28 && (
        (b[0] == 0xDE.toByte() && b[1] == 0x12.toByte() && b[2] == 0x04.toByte() && b[3] == 0x95.toByte()) ||
            (b[0] == 0x95.toByte() && b[1] == 0x04.toByte() && b[2] == 0x12.toByte() && b[3] == 0xDE.toByte()))

    /** File .mo có cả câu gốc lẫn bản dịch. Đọc theo bảng độ dài + vị trí ở đầu file; sai khuôn thì trả null. */
    fun fromMo(b: ByteArray): Found? {
        if (!isMo(b)) return null
        val le = b[0] == 0xDE.toByte()
        fun u32(o: Int): Long {
            if (o < 0 || o + 4 > b.size) return -1
            val x = if (le) (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
            else (b[o + 3].toLong() and 0xFF) or ((b[o + 2].toLong() and 0xFF) shl 8) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o].toLong() and 0xFF) shl 24)
            return x
        }
        val n = u32(8)
        val oo = u32(12)
        val to = u32(16)
        if (n <= 0 || n > 400_000 || oo < 0 || to < 0) return null
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until n.toInt()) {
            val ol = u32((oo + 8 * i).toInt()); val of = u32((oo + 8 * i + 4).toInt())
            val tl = u32((to + 8 * i).toInt()); val tf = u32((to + 8 * i + 4).toInt())
            if (ol < 0 || of < 0 || tl < 0 || tf < 0 || of + ol > b.size || tf + tl > b.size) return null
            var a = String(b, of.toInt(), ol.toInt(), Charsets.UTF_8).substringBefore('\u0000')
            val v = String(b, tf.toInt(), tl.toInt(), Charsets.UTF_8).substringBefore('\u0000')
            if (a.contains('\u0004')) a = a.substringAfter('\u0004')
            if (a.isNotBlank() && v.isNotBlank()) out.add(a to v)
        }
        return orient(out)?.let { Found(it, "gettext .mo") }
    }

    // ---------------- JSON ----------------

    private fun fromJson(raw: String): Found? {
        val tab = LocFormat.parse(raw, "x.json") ?: return null
        // (b) hai bảng ngôn ngữ ở gốc: {"en": {...}, "vi": {...}}
        val top = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        for ((k, v) in tab.entries) {
            val i = k.indexOf('.')
            if (i > 0) top.getOrPut(k.substring(0, i)) { ArrayList() }.add(k.substring(i + 1) to v)
        }
        val big = top.filter { it.value.size >= 3 }
        if (big.size >= 2) {
            val ratio = big.mapValues { Viet.ratio(it.value.map { e -> e.second }) }
            val vi = big.keys.filter { Viet.isVi(ratio[it]!!) }.maxByOrNull { (if (it.lowercase(Locale.ROOT) in PackText.DST) 10f else 0f) + ratio[it]!! }
            if (vi != null) {
                val srcs = big.keys.filter { it != vi && Viet.notVi(ratio[it]!!) }
                val src = srcs.maxByOrNull { (if (it.lowercase(Locale.ROOT) in PackText.SRC) 1e6f else 0f) + big[it]!!.size }
                if (src != null) {
                    val j = LocFormat.join(LocFormat.Table(big[src]!!, "json"), LocFormat.Table(big[vi]!!, "json"))
                    if (j.how != null) return Found(j.pairs, "JSON hai bảng \"$src\" và \"$vi\"", j)
                }
            }
        }
        // (a) mỗi mục có hai trường ngôn ngữ: [{"id":1,"en":"..","vi":".."}] hoặc {"a": {"en":"..","vi":".."}} hoặc [["Hello","Xin chào"]]
        val groups = LinkedHashMap<String, LinkedHashMap<String, String>>()
        for ((k, v) in tab.entries) {
            val i = k.lastIndexOf('.')
            groups.getOrPut(if (i < 0) "" else k.substring(0, i)) { LinkedHashMap() }[if (i < 0) k else k.substring(i + 1)] = v
        }
        val multi = groups.values.filter { it.size >= 2 }
        if (multi.size < 2) return null
        val count = HashMap<String, Int>()
        for (g in multi) for (f in g.keys) count[f] = (count[f] ?: 0) + 1
        val need = maxOf(2, multi.size / 2)
        val fields = count.filter { it.value >= need }.keys
        if (fields.size < 2) return null
        val vals = fields.associateWith { f -> multi.mapNotNull { it[f] } }
        val ratio = vals.mapValues { Viet.ratio(it.value) }
        val vi = fields.filter { Viet.isVi(ratio[it]!!) }.maxByOrNull { (if (it.lowercase(Locale.ROOT) in PackText.DST) 10f else 0f) + ratio[it]!! } ?: return null
        val src = fields.filter { it != vi && Viet.notVi(ratio[it]!!) && !keyLike(vals[it]!!) }
            .maxByOrNull { (if (it.lowercase(Locale.ROOT) in PackText.SRC) 1e6f else 0f) + score(vals[it]!!) } ?: return null
        val pairs = multi.mapNotNull { g -> val a = g[src]; val b = g[vi]; if (a != null && b != null) a to b else null }
        return if (pairs.isEmpty()) null else Found(pairs, "JSON trường \"$src\" và \"$vi\"")
    }

    // ---------------- phụ đề .srt hai dòng ----------------

    private fun fromSrt(raw: String): Found? {
        val pairs = ArrayList<Pair<String, String>>()
        for (block in raw.replace("\r", "").split(Regex("\n\\s*\n"))) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val body = lines.drop(ti + 1).map { it.replace(Regex("<[^>]{1,40}>|\\{\\\\[^}]*}"), "").trim() }.filter { it.isNotEmpty() }
            if (body.size < 2 || body.size % 2 != 0) continue
            val h = body.size / 2
            pairs.add(body.take(h).joinToString(" ") to body.drop(h).joinToString(" "))
        }
        if (pairs.size < 3) return null
        return orient(pairs)?.let { Found(it, "phụ đề hai ngôn ngữ") }
    }

    // ---------------- bảng nhiều cột ----------------

    private val KEY_HEADERS = setOf("key", "id", "name", "tag", "label", "index", "stt", "no", "#", "khoá", "khóa", "mã")

    private fun consistent(sample: List<String>, d: Char): Boolean {
        val counts = sample.map { LocFormat.csvCells(it, d).size }
        val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return false
        return mode.key >= 2 && mode.value * 10 >= sample.size * 8
    }

    private fun fromTable(raw: String): Found? {
        val rows = raw.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (rows.size < 3) return null
        val sample = rows.take(60)
        val d: Char = if (sample.count { it.contains('\t') } * 10 >= sample.size * 8) '\t'
        else listOf(';', ',').firstOrNull { consistent(sample, it) } ?: return null
        val cells = rows.map { LocFormat.csvCells(it, d).map { c -> c.trim() } }
        val ncol = cells.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key ?: return null
        if (ncol < 2) return null
        var data = cells.filter { it.size == ncol }
        val head = data.firstOrNull()?.map { it.lowercase(Locale.ROOT) } ?: return null
        val named = head.any { it in PackText.SRC || it in PackText.DST || it in KEY_HEADERS }
        if (named) data = data.drop(1)
        if (data.size < 3) return null
        val cols = (0 until ncol).map { c -> data.map { it[c] }.filter { it.isNotEmpty() } }
        val ratio = cols.map { Viet.ratio(it) }
        val viC = (0 until ncol).filter { Viet.isVi(ratio[it]) }
            .maxByOrNull { (if (named && head[it] in PackText.DST) 10f else 0f) + ratio[it] } ?: return null
        val srcC = (0 until ncol).filter { it != viC && Viet.notVi(ratio[it]) && !keyLike(cols[it]) }
            .maxByOrNull { (if (named && head[it] in PackText.SRC) 1e6f else 0f) + score(cols[it]) } ?: return null
        val pairs = data.map { it[srcC] to it[viC] }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        if (pairs.isEmpty()) return null
        return Found(pairs, "bảng, cột ${srcC + 1} (gốc) và cột ${viC + 1} (Việt)")
    }

    // ---------------- dòng "gốc | Việt" và dòng xen kẽ ----------------

    private val SEPS = listOf("\t", " | ", "|", " → ", "→", " => ", "=>", " -> ", "->", " = ", "=")

    private fun fromSeparated(raw: String): Found? {
        val rows = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
        if (rows.size < 3) return null
        for (s in SEPS) {
            if (rows.count { it.contains(s) } * 10 < rows.size * 8) continue
            val pairs = rows.mapNotNull { r ->
                val i = r.indexOf(s)
                if (i <= 0) null else r.substring(0, i).trim() to r.substring(i + s.length).trim()
            }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            orient(pairs)?.let { return Found(it, "từng dòng \"gốc ${if (s == "\t") "Tab" else s.trim()} Việt\"") }
        }
        return null
    }

    private fun fromAlternating(raw: String): Found? {
        val rows = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val n = rows.size / 2
        if (n < 3) return null
        val pairs = (0 until n).map { rows[2 * it] to rows[2 * it + 1] }
        return orient(pairs)?.let { Found(it, "dòng xen kẽ (một dòng gốc, một dòng Việt)") }
    }

    // ---------------- file chỉ có tiếng Việt ----------------

    /** Số câu nếu file có vẻ chỉ có tiếng Việt (không có câu gốc để đối chiếu), ngược lại 0. */
    fun viOnlyCount(raw0: String, fileName: String = ""): Int {
        val raw = raw0.removePrefix("﻿")
        val texts: List<String> = LocFormat.parse(raw, fileName)?.entries?.map { it.second }
            ?: raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (texts.size < 3) return 0
        return if (Viet.ratio(texts) >= 0.4f) texts.size else 0
    }

    // ---------------- file nén .zip ----------------

    fun archiveKind(b: ByteArray): String? = when {
        b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2].toInt() == 3 && b[3].toInt() == 4 -> "zip"
        b.size > 4 && b[0] == 'R'.code.toByte() && b[1] == 'a'.code.toByte() && b[2] == 'r'.code.toByte() && b[3] == '!'.code.toByte() -> "rar"
        b.size > 6 && b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() && b[2] == 0xBC.toByte() && b[3] == 0xAF.toByte() -> "7z"
        else -> null
    }

    class ZipFound(
        val pairs: List<Pair<String, String>>,
        /** Nguồn đã dùng: (nhãn, số câu dùng được). */
        val sources: List<Pair<String, Int>>,
        /** File chỉ có tiếng Việt, không có file gốc đi kèm. */
        val viOnly: List<String>,
        /** Các file chữ không đọc được hoặc không ghép được. */
        val failed: List<String>,
        val skippedFiles: Int,
        val untranslated: Int,
        val withVariables: Int,
        val noText: Int,
    )

    private val TEXT_EXT = setOf("json", "xml", "resx", "xlf", "xliff", "tmx", "strings", "properties", "ini", "lang", "srt", "csv", "tsv", "txt", "po", "lng")
    private const val MAX_ENTRY = 12_000_000
    private const val MAX_TOTAL = 48_000_000L
    private const val MAX_SCAN = 4000

    private val VI_TAGS = setOf("vi", "vie", "vn", "vnm", "vivn", "vietnamese", "viet", "vietnam", "tiengviet")
    private val LANG_TAGS = VI_TAGS + setOf(
        "en", "eng", "english", "enus", "engb", "us", "uk", "gb", "ja", "jp", "jpn", "japanese", "ko", "kr", "kor", "korean",
        "zh", "cn", "chs", "cht", "chinese", "tw", "hk", "fr", "fra", "fre", "french", "de", "ger", "deu", "german",
        "es", "spa", "esp", "spanish", "pt", "por", "portuguese", "br", "ru", "rus", "russian", "it", "ita", "italian",
        "pl", "pol", "polish", "tr", "tur", "turkish", "th", "tha", "thai", "id", "ind", "indonesian", "nl", "dut", "dutch",
        "ar", "ara", "arabic", "sv", "swe", "cs", "cz", "ro", "hu", "da", "fi", "no", "nb", "ms", "default", "base", "source", "original", "gốc"
    )

    private class Ent(val path: String, val text: String) {
        private val segs = path.lowercase(Locale.ROOT).split('/').filter { it.isNotEmpty() }
        private fun toks(s: String) = s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        /** Ngôn ngữ ghi ở tên file, nếu không có thì ở thư mục gần nhất có ghi: "vi", tên ngôn ngữ khác, hoặc null (file không ghi). */
        val lang: String? = run {
            var r: String? = null
            for (i in segs.indices.reversed()) {
                val t = toks(if (i == segs.size - 1) segs[i].substringBeforeLast('.') else segs[i])
                val vi = t.firstOrNull { it in VI_TAGS }
                val other = t.firstOrNull { it in LANG_TAGS && it !in VI_TAGS }
                if (vi != null) { r = "vi"; break }
                if (other != null) { r = other; break }
            }
            r
        }
        val base: String = segs.flatMap { toks(it) }.filter { it !in LANG_TAGS }.joinToString("/")
        val name: String = toks(segs.last()).filter { it !in LANG_TAGS }.joinToString("/")
        val ext: String = segs.last().substringAfterLast('.', "")
    }

    /** Đọc một file .zip chứa file Việt hoá: ghép file gốc với file Việt cùng tên (khác nhau ở tên ngôn ngữ), và đọc file có cả hai ngôn ngữ. */
    fun fromZipFile(f: File): ZipFound {
        val ents = ArrayList<Pair<String, String>>()
        var skipped = 0
        var total = 0L
        java.util.zip.ZipFile(f).use { zf ->
            var scanned = 0
            val en = zf.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                if (e.isDirectory) continue
                if (++scanned > MAX_SCAN) { skipped++; continue }
                val path = e.name.replace('\\', '/')
                val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (path.contains("__MACOSX") || path.substringAfterLast('/').startsWith(".") || ext !in TEXT_EXT) { skipped++; continue }
                if (e.size > MAX_ENTRY) { skipped++; continue }
                val buf = zf.getInputStream(e).use { ins ->
                    val out = java.io.ByteArrayOutputStream()
                    val b = ByteArray(1 shl 15)
                    var over = false
                    while (true) {
                        val n = ins.read(b)
                        if (n < 0) break
                        out.write(b, 0, n)
                        if (out.size() > MAX_ENTRY) { over = true; break }
                    }
                    if (over) null else out.toByteArray()
                }
                if (buf == null) { skipped++; continue }
                total += buf.size
                if (total > MAX_TOTAL) { skipped++; break }
                ents.add(path to PackText.decode(buf))
            }
        }
        return pairEntries(ents, skipped)
    }

    /** Ghép các file đã đọc (đường dẫn, chữ) trong một file nén. Tách riêng để kiểm thử không cần tạo file .zip. */
    fun pairEntries(files: List<Pair<String, String>>, skipped: Int = 0): ZipFound {
        val ents = files.map { Ent(it.first, it.second) }
        val pairs = ArrayList<Pair<String, String>>()
        val sources = ArrayList<Pair<String, Int>>()
        val viOnly = ArrayList<String>()
        val failed = ArrayList<String>()
        var un = 0; var vars = 0; var nt = 0
        val used = HashSet<Ent>()

        fun add(label: String, j: LocFormat.Join) {
            pairs.addAll(j.pairs)
            sources.add(label to j.pairs.size)
            un += j.untranslated; vars += j.withVariables; nt += j.noText
        }

        val vis = ents.filter { it.lang == "vi" }
        val others = ents.filter { it.lang != "vi" }
        // 1) ghép file Việt với file gốc cùng tên: trước hết theo cả đường dẫn, rồi theo tên file khi chỉ có một ứng viên
        val partner = LinkedHashMap<Ent, Ent>()
        fun pick(cands: List<Ent>): Ent? = cands.firstOrNull { it.lang == "en" || it.lang == "eng" || it.lang == "english" || it.lang == "enus" || it.lang == "engb" } ?: cands.firstOrNull { it.lang == null } ?: cands.firstOrNull()
        val usedOther = HashSet<Ent>()
        for (v in vis) {
            val c = others.filter { it !in usedOther && it.base == v.base && it.ext == v.ext }
            pick(c)?.let { partner[v] = it; usedOther.add(it) }
        }
        for (v in vis) {
            if (v in partner) continue
            val c = others.filter { it !in usedOther && it.name == v.name && it.ext == v.ext }
            val vc = vis.filter { it !in partner && it.name == v.name && it.ext == v.ext }
            if (c.size == 1 && vc.size == 1) { partner[v] = c[0]; usedOther.add(c[0]) }
        }
        for ((v, o) in partner) {
            val a = LocFormat.parse(o.text, o.path)
            val b = LocFormat.parse(v.text, v.path)
            if (a == null || b == null) { failed.add(v.path.substringAfterLast('/')); continue }
            val j = LocFormat.join(a, b)
            if (j.how == null) { failed.add(o.path.substringAfterLast('/') + " + " + v.path.substringAfterLast('/')); continue }
            used.add(v); used.add(o)
            add(o.path.substringAfterLast('/') + " + " + v.path.substringAfterLast('/') + " (" + j.how + ")", j)
        }
        // 2) file có cả hai ngôn ngữ ngay trong file
        for (e in ents) {
            if (e in used) continue
            val b = parse(e.text, e.path)
            val found = b?.pairs ?: PackText.parseAny(e.text).lines.takeIf { it.isNotEmpty() }?.let { orient(it) }
            if (found != null) {
                val j = LocFormat.filter(found, "một file")
                used.add(e)
                add(e.path.substringAfterLast('/') + " (có cả hai ngôn ngữ)", j)
            }
        }
        // 3) file còn lại chỉ có tiếng Việt
        for (e in ents) {
            if (e in used) continue
            if (e.lang == "vi" || viOnlyCount(e.text, e.path) > 0) viOnly.add(e.path.substringAfterLast('/'))
        }
        return ZipFound(pairs.take(PackText.MAX_LINES), sources, viOnly, failed, skipped, un, vars, nt)
    }
}
