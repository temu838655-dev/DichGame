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

    private fun legacyLinesFile(ctx: Context, profile: String) = File(File(ctx.filesDir, "lines"), slug(profile) + ".tsv")
    private fun scopedDir(ctx: Context, profile: String) = File(File(ctx.filesDir, "lines-v2"), slug(profile))

    private fun linesFile(ctx: Context, profile: String): File {
        val prefs = Prefs(ctx)
        val game = GamePackMatch.title(prefs.gameOf(profile), profile,
            prefs.selectedGameTitleOf(profile), Prefs.DEFAULT_PROFILE)
        return linesFileForGame(ctx, profile, game)
    }

    private fun linesFileForGame(ctx: Context, profile: String, game: String): File {
        // Keep existing named-profile data; never copy it into a different game.
        val scopedGame = GamePackMatch.storageGame(profile, game)
        if (scopedGame.isEmpty()) return legacyLinesFile(ctx, profile)
        val digest = GamePackMatch.storageHash(scopedGame)
        return File(scopedDir(ctx, profile), digest + ".tsv")
    }

    private fun readFile(f: File): List<Pair<String, String>> {
        if (!f.exists() && !File(f.path + ".bak").exists()) return emptyList()
        return AtomicFile(f).openRead().bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { l ->
                val t = l.indexOf('\t')
                if (t <= 0) null else l.substring(0, t) to l.substring(t + 1)
            }.toList()
        }
    }

    private fun cloudFile(local: File) = File(local.path + ".cloud")
    data class CloudInfo(val version: Int, val sha256: String, val source: String?)
    private data class CloudData(val info: CloudInfo, val lines: List<Pair<String, String>>)
    // Invalidate an ongoing download if its destination is deleted.
    private var deletionEpoch = 0L

    private fun readCloudHeader(reader: java.io.BufferedReader): CloudInfo {
        val header = reader.readLine()?.split('\t', limit = 4) ?: error("Kho Cloud trống.")
        require(header.size in 3..4 && header[0] == "DG-CLOUD-1") { "Kho Cloud không hợp lệ." }
        val version = header[1].toInt()
        require(version > 0 && Regex("^[0-9a-f]{64}$").matches(header[2]))
        val source = header.getOrNull(3)?.takeIf { it.isNotBlank() }
        return CloudInfo(version, header[2], source)
    }

    @Synchronized
    fun cloudInfo(ctx: Context, profile: String, game: String): CloudInfo? = runCatching {
        val f = cloudFile(linesFileForGame(ctx, profile, game))
        if (!f.exists() && !File(f.path + ".bak").exists()) null
        else AtomicFile(f).openRead().bufferedReader(Charsets.UTF_8).use { readCloudHeader(it) }
    }.getOrNull()

    @Synchronized
    fun deleteCloud(ctx: Context, profile: String, game: String) {
        deletionEpoch++
        markCloudDeleted(linesFileForGame(ctx, profile, game))
        AtomicFile(cloudFile(linesFileForGame(ctx, profile, game))).delete()
        mem = null; memKey = ""
    }

    private fun cloudDeleted(local: File) = File(local.path + ".cloud-disabled")

    private fun markCloudDeleted(local: File) {
        val marker = cloudDeleted(local)
        marker.parentFile?.mkdirs()
        marker.writeText("1")
    }

    @Synchronized fun autoCloudEpoch(ctx: Context, profile: String, game: String): Long? =
        if (cloudDeleted(linesFileForGame(ctx, profile, game)).exists()) null else deletionEpoch

    private fun readCloud(local: File): CloudData? {
        val f = cloudFile(local)
        if (!f.exists() && !File(f.path + ".bak").exists()) return null
        return AtomicFile(f).openRead().bufferedReader(Charsets.UTF_8).use { reader ->
            val info = readCloudHeader(reader)
            val rows = ArrayList<Pair<String, String>>()
            reader.forEachLine { line ->
                val i = line.indexOf('\t')
                require(i > 0 && rows.size < PackText.MAX_LINES)
                rows.add(line.substring(0, i) to line.substring(i + 1))
            }
            CloudData(info, rows)
        }
    }

    private fun combined(local: File): List<Pair<String, String>> =
        RemotePack.mergeOffline(readCloud(local)?.lines.orEmpty(), readFile(local))

    @Synchronized
    fun readLines(ctx: Context, profile: String): List<Pair<String, String>> {
        return readAvailable(linesFile(ctx, profile))
    }

    private fun readAvailable(f: File): List<Pair<String, String>> {
        val local = runCatching { readFile(f) }.getOrDefault(emptyList())
        val cloud = runCatching { readCloud(f)?.lines.orEmpty() }.getOrDefault(emptyList())
        return RemotePack.mergeOffline(cloud, local)
    }

    @Synchronized
    private fun saveCloud(local: File, manifest: RemotePack.Manifest, rows: List<Pair<String, String>>, source: String, epoch: Long, current: () -> Boolean): String {
        require(current()) { "Đồng bộ đã bị hủy." }
        require(epoch == deletionEpoch) { "Kho đã được xóa trong lúc tải. Hãy tải lại nếu cần." }
        val previous = readCloud(local)?.info
        require(previous?.source == null || previous.source == source) { "Nguồn Cloud khác nguồn đã lưu. Hãy xuất bản sao và xóa riêng kho Cloud trước khi đổi nguồn." }
        when (RemotePack.versionDecision(previous?.version, previous?.sha256, manifest.version, manifest.sha256)) {
            RemotePack.Decision.SAME -> if (previous?.source != null) return "Gói Cloud đã ở phiên bản ${manifest.version}; dùng kho offline hiện có."
            RemotePack.Decision.REJECT -> throw java.io.IOException("Phiên bản Cloud cũ hơn hoặc trùng phiên bản nhưng khác nội dung. Kho offline được giữ nguyên.")
            RemotePack.Decision.INSTALL -> Unit
        }
        val unique = RemotePack.mergeOffline(rows, emptyList())
        val total = RemotePack.mergeOffline(unique, readFile(local)).size
        require(total <= PackText.MAX_LINES) { "Kho vượt giới hạn số câu. Không thay đổi dữ liệu." }
        val f = cloudFile(local)
        f.parentFile?.mkdirs()
        val atomic = AtomicFile(f)
        val output = atomic.startWrite()
        try {
            val writer = output.bufferedWriter(Charsets.UTF_8)
            writer.write("DG-CLOUD-1\t${manifest.version}\t${manifest.sha256}\t$source")
            writer.newLine()
            for ((source, vi) in unique) {
                writer.write(source.replace('\t', ' ').replace('\r', ' ').replace('\n', ' '))
                writer.write("\t")
                writer.write(vi.replace('\t', ' ').replace('\r', ' ').replace('\n', ' '))
                writer.newLine()
            }
            writer.flush()
            require(current()) { "Đồng bộ đã bị hủy." }
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
        cloudDeleted(local).delete()
        mem = null; memKey = ""
        return "Đã lưu kho Cloud ${manifest.game} v${manifest.version} (${unique.size} câu Cloud, $total câu tổng). Bản dịch cá nhân được ưu tiên."
    }

    fun lineCount(ctx: Context, profile: String): Int = readLines(ctx, profile).size

    /** Thêm câu thoại vào hồ sơ (trùng câu gốc thì lấy bản mới). Trả về số câu sau khi gộp. */
    @Synchronized
    fun addLines(ctx: Context, profile: String, add: List<Pair<String, String>>): Int {
        val f = linesFile(ctx, profile)
        addLinesTo(f, add, false)
        return readAvailable(f).size
    }

    /**
     * Nhập bản xuất VERIFIED từ knowledge base như lớp dữ liệu nền:
     * không ghi đè câu đã có, vì câu đó có thể là bản người chơi tự hiệu chỉnh.
     */
    @Synchronized
    fun addVerifiedLines(ctx: Context, profile: String, add: List<Pair<String, String>>): Int {
        val f = linesFile(ctx, profile)
        addLinesTo(f, add, true)
        return readAvailable(f).size
    }

    @Synchronized
    private fun addLinesTo(f: File, add: List<Pair<String, String>>, preserveExisting: Boolean): Int {
        // Read and write the same captured target; if reading fails do not overwrite it.
        val existing = readFile(f)
        val list = TranslationLineMerge.merge(existing, add, preserveExisting)
        if (list.size > PackText.MAX_LINES)
            throw java.io.IOException("Kho vượt giới hạn câu; không thay đổi dữ liệu đang lưu.")
        require(RemotePack.mergeOffline(readCloud(f)?.lines.orEmpty(), list).size <= PackText.MAX_LINES) {
            "Kho vượt giới hạn số câu. Không thay đổi dữ liệu."
        }
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
        deletionEpoch++
        val f = linesFile(ctx, profile)
        markCloudDeleted(f)
        AtomicFile(f).delete()
        AtomicFile(cloudFile(f)).delete()
        mem = null; memKey = ""
    }

    @Synchronized
    fun deleteProfileLines(ctx: Context, profile: String) {
        deletionEpoch++
        val legacy = legacyLinesFile(ctx, profile)
        AtomicFile(legacy).delete()
        AtomicFile(cloudFile(legacy)).delete()
        scopedDir(ctx, profile).deleteRecursively()
        mem = null; memKey = ""
    }

    private var memKey = ""
    private var mem: TransMemory? = null

    /** Bộ nhớ câu thoại đang dùng: gói có sẵn (nếu bật) rồi câu đã nhập; câu nhập sau đè câu có sẵn. */
    @Synchronized
    fun memory(ctx: Context, p: Prefs): TransMemory {
        val profile = p.profile
        val name = gameName(p)
        val f = linesFileForGame(ctx, profile, name)
        val cf = cloudFile(f)
        val builtEnabled = p.packOn || p.aiAuto
        val key = profile + "\u0000" + name + "\u0000" + builtEnabled + "\u0000" + f.lastModified() + "\u0000" + f.length() + "\u0000" + cf.lastModified() + "\u0000" + cf.length()
        mem?.let { if (key == memKey) return it }
        val built = if (builtEnabled) VietHoa.linesFor(name) else emptyList()
        val imported = readAvailable(f)
        return TransMemory(built + imported).also { mem = it; memKey = key }
    }

    /** Số mục có sẵn cho game: (thuật ngữ, nhân vật, câu thoại). */
    fun builtinCounts(name: String, genre: String): Triple<Int, Int, Int> =
        Triple(VietHoa.termsFor(name, genre).size, VietHoa.castFor(name).size, VietHoa.linesFor(name).size)

    // ---- nhập, xuất ----

    /** Nhập một gói (văn bản) vào hồ sơ hiện tại. Phần đã có được giữ nguyên. Trả về câu báo kết quả. */
    fun importText(ctx: Context, p: Prefs, text: String, fileName: String = ""): String {
        // CSV riêng này chỉ được xuất từ các mục VERIFIED của knowledge base.
        // Giữ ưu tiên cho bản đã có trên máy (nhất là bản người chơi tự sửa).
        val verifiedKnowledgePack = PackText.parseScopedCsv(text)
        val pack = verifiedKnowledgePack ?: PackText.parseAny(text)
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
        val currentGame = gameName(p)
        if (pack.name.isNotBlank() && (currentGame.isBlank() || !GamePackMatch.selected(currentGame, pack.name))) {
            return "Gói dành cho ${pack.name}, nhưng game đang chọn là ${currentGame.ifBlank { "chưa chọn" }}. Hãy chọn đúng game trong Kho game trước khi nhập."
        }
        val before = p.glossary.lines().count { it.contains('=') }
        p.glossary = VietHoa.merge(p.glossary, pack.terms)
        val addedTerms = p.glossary.lines().count { it.contains('=') } - before
        p.cast = Cast.merge(p.cast, pack.cast)
        val beforeL = lineCount(ctx, p.profile)
        val j = LocFormat.filter(pack.lines, "file")
        val total = if (j.pairs.isEmpty()) beforeL else if (verifiedKnowledgePack != null)
            addVerifiedLines(ctx, p.profile, j.pairs) else addLines(ctx, p.profile, j.pairs)
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

    @Synchronized
    fun exportCsv(ctx: Context, p: Prefs): String {
        val profile = p.profile
        val game = gameName(p)
        return PackText.formatScopedCsv(game, combined(linesFileForGame(ctx, profile, game)))
    }

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

    fun catalogSource(link: String, game: String): String? {
        val url = URL(link)
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null && url.ref == null)
        return RemotePack.catalogSource(fetchWithRetry(url, 512_000) { !Thread.currentThread().isInterrupted }
            .toString(Charsets.UTF_8), game)
    }

    /** Download a user-authorized direct pack link, including ZIP and MO, with a bounded temporary file. */
    fun importLink(ctx: Context, p: Prefs, link: String, expectedProfile: String = p.profile, expectedGame: String = gameName(p), autoEpoch: Long? = null, active: () -> Boolean = { true }): String {
        val profile = expectedProfile
        val game = expectedGame
        require(p.profile == profile && gameName(p) == game) { "Game hoặc hồ sơ đã thay đổi." }
        var u = link.trim()
        val github = Regex("^https://github[.]com/([^/]+/[^/]+)/blob/(.+)$").find(u)
        if (github != null) u = "https://raw.githubusercontent.com/${github.groupValues[1]}/${github.groupValues[2]}"
        val url = URL(u)
        require(url.protocol == "https" && url.userInfo == null) { "Chỉ nhận link HTTPS trực tiếp." }
        if (url.path.endsWith(".dichgame.json", true)) {
            require(game.isNotBlank()) { "Hãy chọn đúng game trước khi tải gói Cloud." }
            val target = linesFileForGame(ctx, profile, game)
            val source = url.toExternalForm()
            require(source.length <= 2048 && source.none { it.isISOControl() }) { "Link Cloud không hợp lệ." }
            val (previous, epoch) = synchronized(this) { readCloud(target)?.info to deletionEpoch }
            require(previous?.source == null || previous.source == source) { "Nguồn Cloud khác nguồn đã lưu. Hãy sao lưu và xóa riêng kho Cloud trước khi đổi nguồn." }
            fun current(): Boolean = active() && p.profile == profile && gameName(p) == game && synchronized(this) {
                epoch == deletionEpoch && (autoEpoch == null || (autoEpoch == epoch && !cloudDeleted(target).exists()))
            }
            val manifest = RemotePack.parse(fetchWithRetry(url, 64_000, ::current).toString(Charsets.UTF_8), game)
            when (RemotePack.versionDecision(previous?.version, previous?.sha256, manifest.version, manifest.sha256)) {
                RemotePack.Decision.SAME -> if (previous?.source != null) return "Kho Cloud đã ở phiên bản ${manifest.version}; không cần tải lại."
                RemotePack.Decision.REJECT -> throw java.io.IOException("Phiên bản Cloud cũ hơn hoặc trùng phiên bản nhưng khác nội dung.")
                RemotePack.Decision.INSTALL -> Unit
            }
            val data = fetchWithRetry(manifest.url, manifest.bytes, ::current)
            val pack = RemotePack.verify(manifest, data)
            if (p.profile != profile || gameName(p) != game) throw java.io.IOException("Game hoặc hồ sơ đã thay đổi. Hãy chọn lại game.")
            val filtered = LocFormat.filter(pack.lines, "cloud")
            require(filtered.pairs.isNotEmpty()) { "Không có câu Việt hóa hợp lệ." }
            return saveCloud(target, manifest, filtered.pairs, source, epoch, ::current)
        }
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
            if (p.profile != profile || gameName(p) != game) throw java.io.IOException("Game hoặc hồ sơ đã thay đổi. Hãy chọn lại game.")
            return importFile(ctx, p, url.path.substringAfterLast('/'), tmp)
        } finally { tmp.delete() }
    }

    private class CloudHttpError(val code: Int) : java.io.IOException("Cloud trả HTTP $code.")

    private fun fetchWithRetry(url: URL, limit: Int, current: () -> Boolean): ByteArray {
        for (attempt in 0..2) {
            if (!current()) throw java.io.IOException("Game hoặc kho đã thay đổi. Hãy tải lại.")
            try {
                return fetchBounded(url, limit, current)
            } catch (e: java.io.IOException) {
                val retry = if (e is CloudHttpError) RemotePack.retryableHttp(e.code)
                    else e is java.net.SocketTimeoutException || e is java.net.ConnectException ||
                        e is java.net.UnknownHostException || e is java.net.SocketException || e is java.io.EOFException
                if (!retry || attempt == 2 || !current()) throw e
                Thread.sleep(500L shl attempt)
            }
        }
        throw java.io.IOException("Không tải được kho Cloud.")
    }

    private fun fetchBounded(url: URL, limit: Int, current: () -> Boolean): ByteArray {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "DichGame")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (c.responseCode != 200) throw CloudHttpError(c.responseCode)
            if (c.contentLengthLong > limit) throw java.io.IOException("File lớn hơn kích thước cho phép.")
            val data = c.inputStream.use { it.readNBytesCapped(limit, current) }
            if (c.contentLengthLong >= 0 && c.contentLengthLong != data.size.toLong()) throw java.io.EOFException("Tải Cloud bị gián đoạn.")
            return data
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int, current: () -> Boolean = { true }): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 14)
        while (true) {
            if (!current()) throw java.io.IOException("Game hoặc kho đã thay đổi. Hãy tải lại.")
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw java.io.IOException("File vượt giới hạn $max byte.")
        }
        return out.toByteArray()
    }
}
