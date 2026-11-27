package com.dichgame.app

import java.util.Locale

/**
 * Đọc các file chữ của game (bảng chữ gốc và bảng chữ đã Việt hoá) và ghép hai file thành các cặp "câu gốc = câu Việt hoá".
 * Dùng khi người dùng tải về file Việt hoá của một game (ví dụ en.json + vi.json, strings.xml, .properties, .srt)
 * rồi áp vào game đó, để lúc chơi câu nào trên màn hình khớp thì hiện đúng bản Việt hoá trong file, như khi chơi bản Việt hoá trên máy tính.
 *
 * Chỉ đọc được file CHỮ có cấu trúc. Bản vá nhị phân (.exe, .pak, .assets, .locres, .xdelta...) sửa thẳng file game thì không đọc được.
 * Không phụ thuộc Android.
 */
object LocFormat {
    /** Một file chữ đã đọc: danh sách (khoá, chữ) theo đúng thứ tự trong file. [kind]: json, xml, strings, props, srt, lines, csv. */
    class Table(val entries: List<Pair<String, String>>, val kind: String) {
        val size: Int get() = entries.size
        val byKey: Map<String, String> by lazy { LinkedHashMap<String, String>().also { m -> for ((k, v) in entries) m[k] = v } }
        /** Khoá theo vị trí (srt, từng dòng) chỉ có nghĩa khi ghép theo thứ tự. */
        val ordered: Boolean get() = kind == "srt" || kind == "lines"
    }

    /** Đọc một file. [fileName] giúp đoán định dạng (đuôi file). Trả null nếu không nhận ra hoặc rỗng. */
    fun parse(raw0: String, fileName: String = ""): Table? {
        val raw = raw0.removePrefix("\uFEFF")
        if (raw.isBlank()) return null
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val t = raw.trimStart()
        val tab: Table? = when {
            ext == "json" || t.startsWith("{") || t.startsWith("[") -> fromJson(raw)
            ext == "srt" || Regex("(?m)^\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s*-->").containsMatchIn(raw) -> fromSrt(raw)
            ext == "xml" || ext == "resx" || t.startsWith("<?xml") || t.startsWith("<resources") || t.startsWith("<root") -> fromXml(raw)
            ext == "strings" || looksLikeStrings(raw) -> fromStrings(raw)
            ext == "yml" || ext == "yaml" || Regex("(?m)\\A(?:\\s*#.*\\n)*\\s*l_[a-z_]+:\\s*$").containsMatchIn(raw) -> fromYaml(raw)
            ext == "csv" || ext == "tsv" -> fromCsv(raw)
            else -> fromProps(raw) ?: fromLines(raw)
        }
        return tab?.takeIf { it.size > 0 }
    }

    // ---------------- JSON ----------------

    private fun fromJson(raw: String): Table? {
        val v = MiniJson.parse(raw) ?: return null
        val out = ArrayList<Pair<String, String>>()
        flatten(v, "", out, 0)
        return if (out.isEmpty()) null else Table(out, "json")
    }

    private fun flatten(v: Any?, path: String, out: MutableList<Pair<String, String>>, depth: Int) {
        if (depth > 30 || out.size > 400_000) return
        when (v) {
            is String -> if (path.isNotEmpty()) out.add(path to v)
            is Map<*, *> -> for ((k, x) in v) flatten(x, if (path.isEmpty()) k.toString() else "$path.$k", out, depth + 1)
            is List<*> -> v.forEachIndexed { i, x -> flatten(x, if (path.isEmpty()) i.toString() else "$path.$i", out, depth + 1) }
        }
    }

    // ---------------- khoá = giá trị ----------------

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append(' ')
                    'r' -> {}
                    'u' -> {
                        val h = if (i + 6 <= s.length) s.substring(i + 2, i + 6).toIntOrNull(16) else null
                        if (h != null) { sb.append(h.toChar()); i += 4 } else sb.append('u')
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun looksLikeStrings(raw: String) = Regex("(?m)^\\s*\"[^\"]+\"\\s*=\\s*\".*\"\\s*;").findAll(raw).take(3).count() >= 2

    /** iOS/Mac .strings: "khoá" = "giá trị"; */
    private fun fromStrings(raw: String): Table? {
        val rx = Regex("^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*;", RegexOption.MULTILINE)
        val out = rx.findAll(raw).map { unescape(it.groupValues[1]) to unescape(it.groupValues[2]) }.toList()
        return if (out.isEmpty()) null else Table(out, "strings")
    }

    /** .properties / .ini / .lang: khoá=giá trị. Chỉ nhận khi đa số dòng có dạng đó. Mục [section] thành tiền tố khoá. */
    private fun fromProps(raw: String): Table? {
        val rows = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") && !it.startsWith("//") }
        if (rows.size < 2) return null
        var section = ""
        val out = ArrayList<Pair<String, String>>()
        var kv = 0
        var data = 0
        for (l in rows) {
            if (l.startsWith("[") && l.endsWith("]")) { section = l.substring(1, l.length - 1).trim() + "."; continue }
            data++
            val i = l.indexOf('=')
            if (i <= 0) continue
            val k = l.substring(0, i).trim()
            if (k.length > 80 || (k.contains(' ') && k.length > 24)) continue
            kv++
            out.add(section + k to unescape(l.substring(i + 1).trim().trim('"')))
        }
        return if (data >= 2 && kv * 10 >= data * 7) Table(out, "props") else null
    }

    private fun fromCsv(raw: String): Table? {
        val rows = raw.lines().filter { it.isNotBlank() }
        if (rows.size < 2) return null
        val d = if (rows.take(20).count { it.contains('\t') } * 2 >= rows.take(20).size) '\t' else if (rows.take(20).count { it.contains(';') } * 2 >= rows.take(20).size) ';' else ','
        val out = ArrayList<Pair<String, String>>()
        for (r in rows) {
            val cells = csvCells(r, d)
            if (cells.size < 2) continue
            out.add(cells[0].trim() to cells[1].trim())
        }
        return if (out.size < 2) null else Table(out, "csv")
    }

    internal fun csvCells(line: String, d: Char): List<String> {
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

    // ---------------- YAML (Paradox: l_english: rồi KEY:0 "chữ"; Rails i18n lồng nhau) ----------------

    private fun yamlValue(rest: String): String? {
        val r = rest.trim()
        if (r.isEmpty()) return null
        val c = r[0]
        if (c == '"' || c == '\'') {
            var i = 1
            val sb = StringBuilder()
            while (i < r.length) {
                val ch = r[i]
                if (ch == '\\' && c == '"' && i + 1 < r.length) { sb.append(r[i + 1].let { if (it == 'n') '\n' else it }); i += 2; continue }
                if (ch == c) { if (c == '\'' && i + 1 < r.length && r[i + 1] == '\'') { sb.append('\''); i += 2; continue }; return sb.toString() }
                sb.append(ch); i++
            }
            return null
        }
        if (c == '|' || c == '>' || c == '[' || c == '{' || c == '&' || c == '*' || c == '!') return null
        return r.substringBefore(" #").trim()
    }

    private fun fromYaml(raw: String): Table? {
        val rx = Regex("^(\\s*)(\"[^\"]+\"|'[^']+'|[^\\s:#\"'][^:#]*?)\\s*:\\s*(?:\\d+\\s+)?(.*)$")
        val stack = ArrayList<Pair<Int, String>>()
        val out = ArrayList<Pair<String, String>>()
        for (line in raw.lines()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val m = rx.matchEntire(line.trimEnd()) ?: continue
            val indent = m.groupValues[1].length
            val key = m.groupValues[2].trim().trim('"', '\'')
            val rest = m.groupValues[3]
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeAt(stack.size - 1)
            if (rest.isBlank()) { stack.add(indent to key); continue }
            val v = yamlValue(rest) ?: continue
            // khoá gốc "l_english"/"vi" chỉ là tên ngôn ngữ, bỏ ra khỏi đường dẫn để hai file ngôn ngữ ghép được theo khoá
            val keys = stack.map { it.second }
            val path = (if (keys.isNotEmpty() && (keys[0].startsWith("l_") || isLangKey(keys[0]))) keys.drop(1) else keys) + key
            out.add(path.joinToString(".") to v)
        }
        return if (out.isEmpty()) null else Table(out, "yml")
    }

    private fun isLangKey(k: String) = k.lowercase(Locale.ROOT).replace('_', '-').let { it.length == 2 || Regex("[a-z]{2}-[a-z]{2}").matches(it) }

    // ---------------- XML ----------------

    internal fun xmlText(s: String): String {
        var t = s.trim()
        if (t.startsWith("<![CDATA[") && t.endsWith("]]>")) t = t.substring(9, t.length - 3)
        t = Regex("<value>(.*?)</value>", RegexOption.DOT_MATCHES_ALL).find(t)?.groupValues?.get(1) ?: t
        return unescape(t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&"))
    }

    /** Android strings.xml, .NET .resx và các dạng <tên name|id|key="khoá">chữ</tên> tương tự. */
    private fun fromXml(raw: String): Table? {
        val rx = Regex("<([A-Za-z_][\\w.:-]*)\\s+[^>]*?\\b(?:name|id|key)=\"([^\"]+)\"[^>]*?>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
        val out = rx.findAll(raw).map { it.groupValues[2] to xmlText(it.groupValues[3]) }.filter { it.second.isNotBlank() && !it.second.contains('<') }.toList()
        return if (out.isEmpty()) null else Table(out, "xml")
    }

    // ---------------- phụ đề .srt và file từng dòng ----------------

    private fun fromSrt(raw: String): Table? {
        val out = ArrayList<Pair<String, String>>()
        for (block in raw.replace("\r", "").split(Regex("\n\\s*\n"))) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val text = lines.drop(ti + 1).joinToString(" ").replace(Regex("<[^>]{1,40}>|\\{\\\\[^}]*}"), "").trim()
            if (text.isNotEmpty()) out.add(lines[ti].substringBefore("-->").trim() to text)
        }
        return if (out.isEmpty()) null else Table(out, "srt")
    }

    /** File chữ thường, mỗi dòng một câu (bỏ dòng trống); khoá là số thứ tự dòng, chỉ ghép được theo thứ tự. */
    private fun fromLines(raw: String): Table? {
        val rows = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (rows.size < 2) return null
        return Table(rows.mapIndexed { i, s -> "L${i + 1}" to s }, "lines")
    }

    // ---------------- ghép hai file ----------------

    class Join(
        val pairs: List<Pair<String, String>>,
        /** Cách ghép: "khoá" hoặc "thứ tự"; null nếu không ghép được. */
        val how: String?,
        val total: Int,
        val untranslated: Int,
        val withVariables: Int,
        val noText: Int,
        val note: String,
    )

    /** Ghép [orig] (chữ gốc) với [vi] (chữ Việt hoá) thành các cặp dùng được. */
    fun join(orig: Table, vi: Table): Join {
        // file từng dòng chỉ có khoá L1, L2... trùng nhau ngẫu nhiên: không tính là "cùng khoá", chỉ ghép theo thứ tự
        val keyed = orig.kind != "lines" && vi.kind != "lines"
        val keys = if (keyed) orig.byKey.keys.count { it in vi.byKey } else 0
        val small = minOf(orig.size, vi.size)
        val pairs = ArrayList<Pair<String, String>>()
        var how: String? = null
        var note = ""
        if (keys >= 2 && keys * 2 >= small) {
            how = "khoá"
            for ((k, v) in orig.entries) vi.byKey[k]?.let { pairs.add(v to it) }
        } else if (orig.ordered && vi.ordered && orig.size >= 3 && kotlin.math.abs(orig.size - vi.size) * 50 <= maxOf(orig.size, vi.size)) {
            how = "thứ tự"
            for (i in 0 until minOf(orig.size, vi.size)) pairs.add(orig.entries[i].second to vi.entries[i].second)
            if (orig.size != vi.size) note = "Hai file lệch ${kotlin.math.abs(orig.size - vi.size)} dòng, ghép theo thứ tự có thể lệch từ chỗ đó."
        } else {
            note = "Không ghép được: " + (if (keyed) "hai file chỉ có $keys khoá trùng" else "hai file số dòng khác nhau") +
                " (file gốc ${orig.size} mục, file Việt ${vi.size} mục). Cần hai file cùng cấu trúc (cùng khoá) hoặc cùng số dòng."
            return Join(emptyList(), null, small, 0, 0, 0, note)
        }
        return filter(pairs, how, note)
    }

    /** Lọc cặp: bỏ cặp chưa dịch (giống hệt gốc), không có chữ, hoặc còn biến số ({0}, %s...) mà app không biết điền. Làm sạch mã định dạng. */
    fun filter(pairs: List<Pair<String, String>>, how: String?, note: String = ""): Join {
        var untranslated = 0; var vars = 0; var noText = 0
        val out = ArrayList<Pair<String, String>>(pairs.size)
        for ((a0, b0) in pairs) {
            if (LineClean.hasVars(a0) || LineClean.hasVars(b0)) { vars++; continue }
            val a = LineClean.clean(a0); val b = LineClean.clean(b0)
            if (a.none { it.isLetter() } || b.none { it.isLetter() }) { noText++; continue }
            if (Canon.key(a) == Canon.key(b)) { untranslated++; continue }
            out.add(a to b)
        }
        return Join(out, how, pairs.size, untranslated, vars, noText, note)
    }
}

/** Làm sạch chữ trong file game: bỏ thẻ định dạng, đổi xuống dòng thành khoảng trắng, nhận ra biến số. */
object LineClean {
    private val BR = Regex("(?i)<br\\s*/?>|\\\\[nr]|\\r?\\n")
    private val TAG = Regex("</?[A-Za-z][^<>\\n]{0,60}>")
    private val SPACES = Regex("\\s+")
    private val VAR = Regex("\\{[^{}\\n]{1,40}\\}|%(?:\\d+\\$)?[sdfi]\\b|\\$\\w+\\$|\\\\[cCnNvVpPiI]\\[\\d+\\]|\\[\\[[^\\]]+\\]\\]|\\[[A-Za-z_][\\w.]{0,30}\\]|<<[^>]{1,40}>>|@\\{[^}]+\\}")
    /** Thẻ định dạng chữ (không phải biến số): {b} {i} {w} {color=#f00} của Ren'Py, mã màu §Y và §! của Paradox. */
    private val FMT = Regex("\\{/?(?:i|b|u|s|w|p|nw|fast|plain|k|a|font|color|size|alpha|cps|rt|rb|image|space|vspace|art|clear|noalt)(?:=[^{}]*)?\\}|§[A-Za-z0-9!]")

    /** Còn chỗ trống phải điền giá trị lúc chạy game (tên người chơi, số lượng...): app không biết giá trị nên không dùng được dòng này. */
    fun hasVars(s: String): Boolean = VAR.containsMatchIn(s.replace(FMT, ""))

    fun clean(s: String): String = s.replace(BR, " ").replace(TAG, "").replace(FMT, "").replace(SPACES, " ").trim()
}

/** Bộ đọc JSON tối thiểu (không phụ thuộc thư viện): object, array, chuỗi, số, true/false/null. Lỗi cú pháp thì trả null. */
internal object MiniJson {
    private class Err : Exception()

    fun parse(s: String): Any? = try {
        val p = Parser(s)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i < s.length) null else v ?: NULL
    } catch (_: Err) { null } catch (_: StackOverflowError) { null }

    /** Giá trị JSON null (để phân biệt với lỗi). */
    val NULL = Any()

    private class Parser(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }

        fun value(depth: Int): Any? {
            if (depth > 40 || i >= s.length) throw Err()
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", NULL)
                else -> if (c == '-' || c in '0'..'9') num() else throw Err()
            }
        }

        fun lit(w: String, v: Any): Any { if (!s.startsWith(w, i)) throw Err(); i += w.length; return v }

        fun num(): Any {
            val st = i
            while (i < s.length && (s[i] in '0'..'9' || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDoubleOrNull() ?: throw Err()
        }

        fun str(): String {
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw Err()
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { if (i + 4 > s.length) throw Err(); sb.append((s.substring(i, i + 4).toIntOrNull(16) ?: throw Err()).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw Err()
        }

        fun arr(depth: Int): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws(); out.add(value(depth + 1)); ws()
                if (i >= s.length) throw Err()
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                throw Err()
            }
        }

        fun obj(depth: Int): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw Err()
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') throw Err()
                i++
                ws()
                out[k] = value(depth + 1)
                ws()
                if (i >= s.length) throw Err()
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                throw Err()
            }
        }
    }
}
