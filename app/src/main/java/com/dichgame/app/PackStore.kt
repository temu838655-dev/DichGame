package com.dichgame.app

import android.content.Context
import android.util.AtomicFile
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
        GamePackMatch.title(p.game, p.profile, p.selectedGameTitle, Prefs.DEFAULT_PROFILE)

    private fun genreOf(p: Prefs, name: String): String =
        Catalog.genreOf(name).ifEmpty { p.game.substringAfter(',', "").trim() }

    // ---- bảng thuật ngữ ----
    private var termKey = ""
    private var termMap: TermMap? = null

    /** Thuật ngữ đang dùng: từ điển riêng trước, rồi gói có sẵn (nếu bật). */
    @Synchronized
    fun terms(p: Prefs): TermMap {
        val name = gameName(p)
        val key = p.glossary + "\u0000" + name + "\u0000" + p.game + "\u0000" + (p.packOn || p.aiAuto)
        termMap?.let { if (key == termKey) return it }
        val user = p.glossary.lines().mapNotNull { l ->
            val i = l.indexOf('=')
            if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
        }
        val built = if (p.packOn || p.aiAuto) VietHoa.termsFor(name, genreOf(p, name)) else emptyList()
        return TermMap(user + built).also { termMap = it; termKey = key }
    }

    /** Thẻ nhân vật đang dùng: của người dùng trước, rồi có sẵn (nếu bật). */
    fun cast(p: Prefs): List<Character> {
        val builtin = if (p.packOn || p.aiAuto) VietHoa.castFor(gameName(p)).joinToString("\n") else ""
        return Cast.parse(p.cast + "\n" + builtin).distinctBy { it.name.lowercase() }
    }

    // ---- bộ nhớ câu thoại ----
    private fun slug(profile: String): String =
        profile.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40) + "_" + Integer.toHexString(profile.hashCode())

    private fun linesFile(ctx: Context, profile: String) = File(File(ctx.filesDir, "lines"), slug(profile) + ".tsv")

    @Synchronized
    fun readLines(ctx: Context, profile: String): List<Pair<String, String>> {
        val f = linesFile(ctx, profile)
        return try {
            AtomicFile(f).openRead().bufferedReader(Charsets.UTF_8).useLines { lines -> lines.mapNotNull { l ->
                val t = l.indexOf('\t')
                if (t <= 0) null else l.substring(0, t) to l.substring(t + 1)
            }.toList() }
        } catch (_: Exception) { emptyList() }
    }

    fun lineCount(ctx: Context, profile: String): Int = readLines(ctx, profile).size

    /** Thêm câu thoại vào hồ sơ (trùng câu gốc thì lấy bản mới). Trả về số câu sau khi gộp. */
    @Synchronized
    fun addLines(ctx: Context, profile: String, add: List<Pair<String, String>>): Int {
        val map = LinkedHashMap<String, Pair<String, String>>()
        for (e in readLines(ctx, profile) + add) {
            val k = Canon.key(e.first)
            if (Canon.usable(k) && e.second.isNotBlank()) map[k] = e.first.replace('\t', ' ').replace('\n', ' ') to e.second.replace('\t', ' ').replace('\n', ' ')
        }
        val list = map.values.toList().takeLast(PackText.MAX_LINES)
        val f = linesFile(ctx, profile)
        f.parentFile?.mkdirs()
        val atomic = AtomicFile(f)
        val output = atomic.startWrite()
        try {
            val writer = output.bufferedWriter(Charsets.UTF_8)
            for ((original, vi) in list) {
                writer.write(original); writer.write("\t"); writer.write(vi); writer.newLine()
            }
            writer.flush()
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
        mem = null; memKey = ""
        return list.size
    }

    @Synchronized
    fun deleteLines(ctx: Context, profile: String) {
        AtomicFile(linesFile(ctx, profile)).delete()
        mem = null; memKey = ""
    }

    private var memKey = ""
    private var mem: TransMemory? = null

    /** Bộ nhớ câu thoại đang dùng: gói có sẵn (nếu bật) rồi câu đã nhập; câu nhập sau đè câu có sẵn. */
    @Synchronized
    fun memory(ctx: Context, p: Prefs): TransMemory {
        val f = linesFile(ctx, p.profile)
        val name = gameName(p)
        val key = p.profile + "\u0000" + name + "\u0000" + (p.packOn || p.aiAuto) + "\u0000" + f.lastModified() + "\u0000" + f.length()
        mem?.let { if (key == memKey) return it }
        val built = if (p.packOn || p.aiAuto) VietHoa.linesFor(name) else emptyList()
        return TransMemory(built + readLines(ctx, p.profile)).also { mem = it; memKey = key }
    }

    /** Số mục có sẵn cho game: (thuật ngữ, nhân vật, câu thoại). */
    fun builtinCounts(name: String, genre: String): Triple<Int, Int, Int> =
        Triple(VietHoa.termsFor(name, genre).size, VietHoa.castFor(name).size, VietHoa.linesFor(name).size)

    // ---- nhập, xuất ----

    /** Nhập một gói (văn bản) vào hồ sơ hiện tại. Phần đã có được giữ nguyên. Trả về câu báo kết quả. */
    fun importText(ctx: Context, p: Prefs, text: String, fileName: String = ""): String {
        val pack = PackText.parseAny(text)
        if (pack.isEmpty) {
            // file có cả chữ gốc lẫn bản Việt hoá bên trong (cột en + vi, XLIFF, TMX, .srt hai dòng, Ren'Py...)
            Bilingual.parse(text, fileName)?.let { return importFound(ctx, p, it) }
            val vo = Bilingual.viOnlyCount(text, fileName)
            if (vo > 0) return "File này chỉ có tiếng Việt ($vo câu), không có câu gốc đi kèm nên app không biết câu nào trên màn hình khớp với câu nào. " +
                "Cần file có cả hai ngôn ngữ, hoặc dùng \"Ghép 2 file\" với file gốc của game (cùng cấu trúc) và file Việt hoá này."
            val t = LocFormat.parse(text, fileName)
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

    /** Nhập các cặp (gốc, Việt) đọc được từ một file có sẵn cả hai ngôn ngữ. */
    private fun importFound(ctx: Context, p: Prefs, f: Bilingual.Found): String {
        val j = f.join ?: LocFormat.filter(f.pairs, f.how ?: "file")
        if (j.pairs.isEmpty()) return "Đọc được ${j.total} cặp (${f.how}) nhưng không cặp nào dùng được." + skipNote(j)
        val before = lineCount(ctx, p.profile)
        val total = addLines(ctx, p.profile, j.pairs)
        return "Đã đọc file hai ngôn ngữ (${f.how}): ${j.pairs.size} câu đưa vào hồ sơ ${p.profile} (hồ sơ có $total câu thoại, thêm mới ${total - before})." + skipNote(j) +
            "\nKhi chơi, câu nào trên màn hình khớp một câu gốc trong file thì hiện đúng bản Việt hoá trong file, không qua bộ dịch."
    }

    /**
     * Nhập một file người dùng chọn, tự nhận dạng: gói của app, file có cả hai ngôn ngữ, gettext .mo, .zip chứa file Việt hoá.
     * [file] là bản chép tạm của file đã chọn. File nhị phân (.exe, .pak, .xdelta...) và Office thì nói rõ vì sao không đọc được.
     */
    fun importFile(ctx: Context, p: Prefs, name: String, file: File): String {
        val len = file.length()
        if (len == 0L) return "File \"$name\" trống."
        val head = file.inputStream().use { ins -> val b = ByteArray(4096); val n = ins.read(b); if (n <= 0) ByteArray(0) else b.copyOf(n) }
        Bilingual.officeKind(name)?.let {
            return "\"$name\" là $it, app không đọc trực tiếp được. Hãy xuất ra file .csv (hai cột: câu gốc, câu Việt) hoặc .json rồi nhập lại."
        }
        when (Bilingual.archiveKind(head)) {
            "zip" -> return importZip(ctx, p, name, file)
            "rar", "7z" -> return "\"$name\" là file nén .${Bilingual.archiveKind(head)}, app chỉ giải nén được .zip. Hãy giải nén trên máy rồi chọn file chữ bên trong (.json, .csv, .po, .srt...), hoặc nén lại thành .zip."
        }
        if (Bilingual.isMo(head)) {
            if (len > MAX_PLAIN) return tooBig(name)
            val f = Bilingual.fromMo(file.readBytes())
                ?: return "File gettext \"$name\" không có cả câu gốc và bản Việt hoá đọc được (hoặc cột gốc không phải chữ hiển thị)."
            return importFound(ctx, p, f)
        }
        Bilingual.binaryKind(name, head)?.let {
            return "\"$name\" là $it: bản vá loại này sửa thẳng file của game nên app không đọc được chữ bên trong. " +
                "Hãy cài bản vá vào game như bình thường (app vẫn dịch những chỗ còn lại). Nếu nhóm Việt hoá có kèm file chữ (.json, .csv, .po, .srt, .zip...) thì nhập file đó."
        }
        if (len > MAX_PLAIN) return tooBig(name)
        return importText(ctx, p, PackText.decode(file.readBytes()), name)
    }

    private const val MAX_PLAIN = 24_000_000L

    private fun tooBig(name: String) = "File \"$name\" quá lớn (tối đa ${MAX_PLAIN / 1_000_000} MB cho file chữ)."

    /** Nhập file .zip: ghép file gốc với file Việt hoá cùng tên trong đó và đọc file có sẵn cả hai ngôn ngữ. */
    private fun importZip(ctx: Context, p: Prefs, name: String, file: File): String {
        val z = try { Bilingual.fromZipFile(file) } catch (e: Exception) {
            return "Không mở được \"$name\" như file .zip: " + (e.message ?: e.javaClass.simpleName)
        }
        val sb = StringBuilder()
        if (z.pairs.isEmpty()) {
            sb.append("Trong \"$name\" không tìm được cặp câu (gốc, Việt) nào dùng được.")
        } else {
            val before = lineCount(ctx, p.profile)
            val total = addLines(ctx, p.profile, z.pairs)
            sb.append("Đã đọc \"$name\": ${z.pairs.size} câu đưa vào hồ sơ ${p.profile} (hồ sơ có $total câu thoại, thêm mới ${total - before}).")
            for ((label, n) in z.sources.take(8)) sb.append("\n• $label: $n câu")
            if (z.sources.size > 8) sb.append("\n• …và ${z.sources.size - 8} nguồn khác")
        }
        val skip = ArrayList<String>()
        if (z.untranslated > 0) skip.add("${z.untranslated} câu chưa dịch (giống hệt câu gốc)")
        if (z.withVariables > 0) skip.add("${z.withVariables} câu có biến số mà app không biết điền")
        if (z.noText > 0) skip.add("${z.noText} dòng không có chữ")
        if (skip.isNotEmpty()) sb.append("\nBỏ qua: ").append(skip.joinToString("; ")).append('.')
        if (z.viOnly.isNotEmpty()) sb.append("\nFile chỉ có tiếng Việt, không có file gốc cùng tên để ghép: ").append(z.viOnly.take(6).joinToString(", ")).append(if (z.viOnly.size > 6) "…" else "").append('.')
        if (z.failed.isNotEmpty()) sb.append("\nKhông ghép được: ").append(z.failed.take(6).joinToString(", ")).append(if (z.failed.size > 6) "…" else "").append('.')
        if (z.skippedFiles > 0) sb.append("\nBỏ qua ${z.skippedFiles} file không phải file chữ (ảnh, âm thanh, chương trình...) hoặc quá lớn.")
        if (z.pairs.isNotEmpty()) sb.append("\nKhi chơi, câu nào trên màn hình khớp một câu gốc thì hiện đúng bản Việt hoá trong file, không qua bộ dịch.")
        return sb.toString()
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

    /** Download a user-authorized direct pack link, including ZIP and MO, with a bounded temporary file. */
    fun importLink(ctx: Context, p: Prefs, link: String): String {
        val profile = p.profile
        var u = link.trim()
        val github = Regex("^https://github[.]com/([^/]+/[^/]+)/blob/(.+)$").find(u)
        if (github != null) u = "https://raw.githubusercontent.com/${github.groupValues[1]}/${github.groupValues[2]}"
        val url = URL(u)
        require(url.protocol == "https" && url.userInfo == null) { "Chỉ nhận link HTTPS trực tiếp." }
        val tmp = File.createTempFile("remote-pack", ".tmp", ctx.cacheDir)
        try {
            val c = url.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 10000
                c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "DichGame")
                if (c.responseCode != 200) throw java.io.IOException("Link không trả về file trực tiếp (HTTP ${c.responseCode}).")
                if (c.contentType.orEmpty().contains("text/html", true)) throw java.io.IOException("Đây là trang web, không phải file lời thoại.")
                c.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(65536)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > 80_000_000L) throw java.io.IOException("Gói vượt quá 80 MB.")
                            output.write(buf, 0, n)
                        }
                    }
                }
            } finally { c.disconnect() }
            if (p.profile != profile) throw java.io.IOException("Hồ sơ game đã thay đổi. Hãy chọn lại game.")
            return importFile(ctx, p, url.path.substringAfterLast('/'), tmp)
        } finally { tmp.delete() }
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
