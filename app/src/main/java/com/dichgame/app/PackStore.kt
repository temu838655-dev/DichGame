package com.dichgame.app

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Nơi ghép gói Việt hoá với cài đặt: gói có sẵn (VietHoa) theo tên game đang chơi, cộng với phần người dùng tự thêm
 * hoặc nhập vào (từ điển riêng, thẻ nhân vật, câu thoại). Phần của người dùng luôn được ưu tiên hơn gói có sẵn.
 */
object PackStore {
    /** Tên game đang chơi: phần trước dấu phẩy của ô "Đang chơi game", hoặc tên hồ sơ nếu không phải hồ sơ mặc định. */
    fun gameName(p: Prefs): String =
        p.game.substringBefore(',').trim().ifEmpty { if (p.profile != Prefs.DEFAULT_PROFILE) p.profile else "" }

    private fun genreOf(p: Prefs, name: String): String =
        Catalog.genreOf(name).ifEmpty { p.game.substringAfter(',', "").trim() }

    // ---- bảng thuật ngữ ----
    private var termKey = ""
    private var termMap: TermMap? = null

    /** Thuật ngữ đang dùng: từ điển riêng trước, rồi gói có sẵn (nếu bật). */
    @Synchronized
    fun terms(p: Prefs): TermMap {
        val name = gameName(p)
        val key = p.glossary + "\u0000" + name + "\u0000" + p.game + "\u0000" + p.packOn
        termMap?.let { if (key == termKey) return it }
        val user = p.glossary.lines().mapNotNull { l ->
            val i = l.indexOf('=')
            if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
        }
        val built = if (p.packOn) VietHoa.termsFor(name, genreOf(p, name)) else emptyList()
        return TermMap(user + built).also { termMap = it; termKey = key }
    }

    /** Thẻ nhân vật đang dùng: của người dùng trước, rồi có sẵn (nếu bật). */
    fun cast(p: Prefs): List<Character> {
        val builtin = if (p.packOn) VietHoa.castFor(gameName(p)).joinToString("\n") else ""
        return Cast.parse(p.cast + "\n" + builtin).distinctBy { it.name.lowercase() }
    }

    // ---- bộ nhớ câu thoại ----
    private fun slug(profile: String): String =
        profile.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40) + "_" + Integer.toHexString(profile.hashCode())

    private fun linesFile(ctx: Context, profile: String) = File(File(ctx.filesDir, "lines"), slug(profile) + ".tsv")

    fun readLines(ctx: Context, profile: String): List<Pair<String, String>> {
        val f = linesFile(ctx, profile)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines(Charsets.UTF_8).mapNotNull { l ->
                val t = l.indexOf('\t')
                if (t <= 0) null else l.substring(0, t) to l.substring(t + 1)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun lineCount(ctx: Context, profile: String): Int = readLines(ctx, profile).size

    /** Thêm câu thoại vào hồ sơ (trùng câu gốc thì lấy bản mới). Trả về số câu sau khi gộp. */
    fun addLines(ctx: Context, profile: String, add: List<Pair<String, String>>): Int {
        val map = LinkedHashMap<String, Pair<String, String>>()
        for (e in readLines(ctx, profile) + add) {
            val k = Canon.key(e.first)
            if (Canon.usable(k) && e.second.isNotBlank()) map[k] = e.first.replace('\t', ' ').replace('\n', ' ') to e.second.replace('\t', ' ').replace('\n', ' ')
        }
        val list = map.values.toList().takeLast(PackText.MAX_LINES)
        val f = linesFile(ctx, profile)
        f.parentFile?.mkdirs()
        f.writeText(list.joinToString("\n") { it.first + "\t" + it.second }, Charsets.UTF_8)
        return list.size
    }

    fun deleteLines(ctx: Context, profile: String) { try { linesFile(ctx, profile).delete() } catch (_: Exception) {} }

    private var memKey = ""
    private var mem: TransMemory? = null

    /** Bộ nhớ câu thoại đang dùng: gói có sẵn (nếu bật) rồi câu đã nhập; câu nhập sau đè câu có sẵn. */
    @Synchronized
    fun memory(ctx: Context, p: Prefs): TransMemory {
        val f = linesFile(ctx, p.profile)
        val name = gameName(p)
        val key = p.profile + "\u0000" + name + "\u0000" + p.packOn + "\u0000" + f.lastModified() + "\u0000" + f.length()
        mem?.let { if (key == memKey) return it }
        val built = if (p.packOn) VietHoa.linesFor(name) else emptyList()
        return TransMemory(built + readLines(ctx, p.profile)).also { mem = it; memKey = key }
    }

    /** Số mục có sẵn cho game: (thuật ngữ, nhân vật, câu thoại). */
    fun builtinCounts(name: String, genre: String): Triple<Int, Int, Int> =
        Triple(VietHoa.termsFor(name, genre).size, VietHoa.castFor(name).size, VietHoa.linesFor(name).size)

    // ---- nhập, xuất ----

    /** Nhập một gói (văn bản) vào hồ sơ hiện tại. Phần đã có được giữ nguyên. Trả về câu báo kết quả. */
    fun importText(ctx: Context, p: Prefs, text: String): String {
        val pack = PackText.parseAny(text)
        if (pack.isEmpty) {
            val t = LocFormat.parse(text)
            if (t != null) return "File này là file chữ một ngôn ngữ của game (${t.size} mục), chưa có cặp \"gốc = Việt hoá\" để nhập. " +
                "Nếu có cả file gốc và file đã Việt hoá, dùng \"Ghép 2 file\" và chọn lần lượt hai file."
            return "File không có gì để nhập. Cần các mục [thuật ngữ], [nhân vật] hoặc [câu thoại], hoặc dùng \"Ghép 2 file\" với file gốc và file Việt hoá."
        }
        val before = p.glossary.lines().count { it.contains('=') }
        p.glossary = VietHoa.merge(p.glossary, pack.terms)
        val addedTerms = p.glossary.lines().count { it.contains('=') } - before
        p.cast = Cast.merge(p.cast, pack.cast)
        val beforeL = lineCount(ctx, p.profile)
        val j = LocFormat.filter(pack.lines, "file")
        val total = if (j.pairs.isEmpty()) beforeL else addLines(ctx, p.profile, j.pairs)
        val who = if (pack.name.isNotBlank()) " \"${pack.name}\"" else ""
        return "Đã nhập gói$who vào hồ sơ ${p.profile}: thêm $addedTerms thuật ngữ, ${pack.cast.size} nhân vật, ${total - beforeL} câu thoại." + skipNote(j)
    }

    private fun skipNote(j: LocFormat.Join): String {
        val parts = ArrayList<String>()
        if (j.untranslated > 0) parts.add("${j.untranslated} câu chưa dịch (giống hệt câu gốc)")
        if (j.withVariables > 0) parts.add("${j.withVariables} câu có biến số như {tên}, %s mà app không biết điền")
        if (j.noText > 0) parts.add("${j.noText} dòng không có chữ")
        return if (parts.isEmpty()) "" else "\nBỏ qua: " + parts.joinToString("; ") + "."
    }

    /**
     * Ghép file chữ gốc của game với file đã Việt hoá (cùng khoá hoặc cùng thứ tự dòng) rồi nhập vào hồ sơ hiện tại.
     * Câu nào trên màn hình khớp một câu gốc trong file thì hiện đúng bản Việt hoá trong file.
     */
    fun importJoined(ctx: Context, p: Prefs, origName: String, orig: String, viName: String, vi: String): String {
        val a = LocFormat.parse(orig, origName)
            ?: return "Không đọc được file gốc \"$origName\". Cần file chữ: .json, .xml (strings.xml, .resx), .strings, .properties/.ini, .srt hoặc mỗi dòng một câu."
        val b = LocFormat.parse(vi, viName)
            ?: return "Không đọc được file Việt hoá \"$viName\". Cần file chữ: .json, .xml (strings.xml, .resx), .strings, .properties/.ini, .srt hoặc mỗi dòng một câu."
        val j = LocFormat.join(a, b)
        if (j.how == null) return j.note
        if (j.pairs.isEmpty()) return "Ghép theo ${j.how} được ${j.total} cặp nhưng không cặp nào dùng được." + skipNote(j)
        val before = lineCount(ctx, p.profile)
        val total = addLines(ctx, p.profile, j.pairs)
        return "Đã ghép theo ${j.how}: ${j.pairs.size} câu đưa vào hồ sơ ${p.profile} (hồ sơ có ${total} câu thoại, thêm mới ${total - before})." + skipNote(j) +
            (if (j.note.isNotEmpty()) "\n" + j.note else "") +
            "\nKhi chơi, câu nào trên màn hình khớp một câu gốc trong file thì hiện đúng bản Việt hoá trong file, không qua bộ dịch. Chữ đọc lệch vài ký tự vẫn khớp được."
    }

    /** Xuất phần của người dùng trong hồ sơ hiện tại thành file gói để chia sẻ. */
    fun exportText(ctx: Context, p: Prefs): String =
        PackText.format(gameName(p).ifEmpty { p.profile }, p.glossary, p.cast, readLines(ctx, p.profile))

    /** Tải văn bản từ link (chỉ https, tối đa 4 MB). Link GitHub dạng /blob/ được đổi sang bản thô. Chạy ở luồng nền. */
    fun fetch(url: String): String {
        var u = url.trim()
        val m = Regex("^https?://github\\.com/([^/]+/[^/]+)/blob/(.+)$").find(u)
        if (m != null) u = "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}"
        if (!u.startsWith("https://")) throw java.io.IOException("Chỉ nhận link https.")
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "DichGame")
            if (c.responseCode != 200) throw java.io.IOException("Mã lỗi ${c.responseCode}.")
            val bytes = c.inputStream.use { it.readNBytesCapped(4_000_000) }
            return String(bytes, Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 14)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw java.io.IOException("File quá lớn (tối đa 4 MB).")
        }
        return out.toByteArray()
    }
}
