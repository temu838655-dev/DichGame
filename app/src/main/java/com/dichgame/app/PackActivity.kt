package com.dichgame.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Gói Việt hoá: bật tắt gói có sẵn của game, dịch theo cảm xúc và nhịp nhân vật, sửa từ điển và thẻ nhân vật,
 * nhập gói của cộng đồng từ file hoặc link, và lưu gói của mình ra file để chia sẻ.
 */
class PackActivity : Activity() {
    private lateinit var p: Prefs
    private var voicePreview: Speaker? = null
    private var linkBusy = false

    override fun onDestroy() {
        voicePreview?.shutdown()
        voicePreview = null
        super.onDestroy()
    }

    /** Bước 1 của "Ghép 2 file": file chữ gốc đã chọn, chờ file Việt hoá. */
    private var origName = ""
    private var origText: String? = null
    private var fileScope: Pair<String, String>? = null
    private fun scope(): Pair<String, String> = p.profile to PackStore.gameName(p)
    private fun requireScope(expected: Pair<String, String>?) {
        require(expected != null && expected == scope()) { "Game đã thay đổi. Hãy chọn lại file cho đúng game." }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
        if (b?.containsKey("file_profile") == true) {
            fileScope = b.getString("file_profile").orEmpty() to b.getString("file_game").orEmpty()
        }
        // mở từ Kho game ("Nhập file Việt hoá của game này"): hỏi luôn cách nhập
        if (b == null && intent.getBooleanExtra("import", false)) importMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        fileScope?.let { outState.putString("file_profile", it.first); outState.putString("file_game", it.second) }
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        p = Prefs(this)
        val name = PackStore.gameName(p)
        val genre = Catalog.genreOf(name).ifEmpty { p.game.substringAfter(',', "").trim() }
        val (bt, bc, bl) = PackStore.builtinCounts(name, genre)
        screen("Gói Việt hoá") {
            put(Ui.tv(this@PackActivity,
                "Gói Việt hoá giữ cho bản dịch dùng đúng một cách gọi tên riêng, xưng hô và giọng từng nhân vật, thay vì để máy dịch tự do. " +
                    "Áp dụng cả khi dịch bằng AI và khi dịch trên máy.", 13f, Ui.SUB), 0, 12)

            // Gói có sẵn
            val a = Ui.card(this@PackActivity)
            a.addView(Ui.section(this@PackActivity, "👑", "Gói có sẵn"))
            a.addView(Ui.tv(this@PackActivity, "Hồ sơ: ${p.profile}\nGame: " + (name.ifEmpty { "chưa chọn (chọn trong Kho game)" }), 14f, Ui.TEXT))
            a.addView(Ui.tv(this@PackActivity,
                "Có sẵn: $bt thuật ngữ, $bc nhân vật, $bl câu thông báo chuẩn" + if (name.isNotEmpty() && !VietHoa.hasSpecific(name)) "\nGame này chưa có gói riêng, đang dùng gói theo thể loại và gói chung." else "",
                13f, Ui.SUB), Ui.lpm(this@PackActivity, 6, 10))
            a.addView(Ui.switchRow(this@PackActivity, "Dùng gói có sẵn", "Tắt thì chỉ dùng từ điển và thẻ nhân vật do bạn tự nhập.", p.packOn) { p.packOn = it })
            put(a, 0, 12)

            // Cảm xúc
            val e = Ui.card(this@PackActivity)
            e.addView(Ui.section(this@PackActivity, "🎭", "Cảm xúc và tiết tấu"))
            e.addView(Ui.switchRow(this@PackActivity, "Dịch theo cảm xúc và nhịp nhân vật",
                "AI nhận ra ai đang nói và họ giận, sợ, buồn hay mỉa mai, rồi chọn từ ngữ, xưng hô và nhịp câu (dồn dập hay chậm rãi) cho đúng. Dịch trên máy thì đoán qua dấu câu và chữ in hoa.", p.emoTr) { p.emoTr = it })
            e.addView(Ui.switchRow(this@PackActivity, "Hiện nhãn cảm xúc", "Dòng nhỏ có màu phía trên phụ đề, ví dụ \"giận dữ · nhanh\" (trên điện thoại).", p.emoLabel) { p.emoLabel = it }, Ui.lpm(this@PackActivity, 10, 0))
            e.addView(Ui.tv(this@PackActivity, "Muốn giọng đọc đổi theo cảm xúc, bật \"Đọc theo cảm xúc\" trong Cài đặt › Giọng đọc.", 12f, Ui.SUB), Ui.lpm(this@PackActivity, 8, 0))
            put(e, 0, 12)

            // Của bạn
            val terms = p.glossary.lines().count { it.contains('=') }
            val cast = p.cast.lines().count { it.contains('=') }
            val lines = PackStore.lineCount(this@PackActivity, p.profile)
            val localLines = PackStore.localLines(this@PackActivity, p.profile)
            val m = Ui.card(this@PackActivity)
            m.addView(Ui.section(this@PackActivity, "✏️", "Của bạn · ${p.profile}"))
            m.addView(Ui.tv(this@PackActivity,
                "$terms thuật ngữ · $cast nhân vật · ${localLines.size} câu lưu trên máy · $lines câu có thể tra (gồm Cloud). Bản của bạn luôn được ưu tiên hơn gói có sẵn.",
                13f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 10))
            if (localLines.isNotEmpty()) m.addView(Ui.button(this@PackActivity, "Xem / sửa câu thoại đã nhập (${localLines.size})") {
                reviewPackLines()
            }, Ui.lpm(this@PackActivity, 0, 8))
            m.addView(Ui.button(this@PackActivity, "Sửa từ điển (gốc = tiếng Việt)") {
                editDialog("Từ điển · ${p.profile}", "Mỗi dòng một từ: gốc = tiếng Việt", "Ví dụ:\nBonfire = Lửa trại\nTarnished = Kẻ Hoen Ố", p.glossary) { p.glossary = it }
            })
            m.addView(Ui.button(this@PackActivity, "Sửa thẻ nhân vật") {
                editDialog("Thẻ nhân vật · ${p.profile}", "Tên = nam/nữ; giọng: tên Việt; nhịp: chậm/vừa/nhanh", "Ví dụ:\nJason = nam; giọng: Hùng; ít lời; nhịp: chậm\nLucia = nữ; giọng: Mai; bộc trực; nhịp: nhanh\nBạn có thể bỏ \"giọng: ...\" để app tự dùng giọng nam/nữ trong Cài đặt.", p.cast) { p.cast = it }
            }, Ui.lpm(this@PackActivity, 8, 0))
            if (lines > 0) m.addView(Ui.button(this@PackActivity, "Xóa $lines câu thoại đã nhập") {
                AlertDialog.Builder(this@PackActivity, R.style.DgDialog)
                    .setMessage("Xóa các câu thoại đã nhập cho hồ sơ ${p.profile}? Gói có sẵn không bị ảnh hưởng.")
                    .setPositiveButton("Xóa") { _, _ -> PackStore.deleteLines(this@PackActivity, p.profile); render() }
                    .setNegativeButton("Hủy", null).show()
            }, Ui.lpm(this@PackActivity, 8, 0))
            put(m, 0, 12)

            // Đóng góp cộng đồng: bản nháp và báo lỗi chỉ lưu trên máy, không giả vờ đã gửi lên máy chủ.
            val communityDrafts = CommunityLocalStore.drafts(this@PackActivity, p.profile, name)
            val communityReports = CommunityLocalStore.reports(this@PackActivity, p.profile, name)
            val community = Ui.card(this@PackActivity)
            community.addView(Ui.section(this@PackActivity, "🤝", "Đóng góp cộng đồng"))
            community.addView(Ui.tv(this@PackActivity,
                "Gửi bản dịch và báo câu sai để chuẩn bị đóng góp cho kho chung. Hiện tại dữ liệu được lưu riêng trên thiết bị; chưa gửi lên máy chủ và chưa được tính là VERIFIED.",
                12.5f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 8))
            community.addView(Ui.tv(this@PackActivity,
                "${communityDrafts.size} bản nháp · ${communityReports.size} báo lỗi · Game: " + name.ifBlank { "chưa chọn" },
                13f, Ui.TEXT), Ui.lpm(this@PackActivity, 0, 8))
            community.addView(Ui.button(this@PackActivity, "＋  Soạn bản dịch đóng góp") { addCommunityDraft() })
            community.addView(Ui.button(this@PackActivity, "⚑  Báo câu dịch sai") { addCommunityReport() }, Ui.lpm(this@PackActivity, 8, 0))
            if (communityDrafts.isNotEmpty()) community.addView(Ui.button(this@PackActivity, "Xem bản nháp (${communityDrafts.size})") {
                showCommunityDrafts()
            }, Ui.lpm(this@PackActivity, 8, 0))
            if (communityReports.isNotEmpty()) community.addView(Ui.button(this@PackActivity, "Xem báo lỗi đã lưu (${communityReports.size})") {
                showCommunityReports()
            }, Ui.lpm(this@PackActivity, 8, 0))
            put(community, 0, 12)

            val cloud = PackStore.cloudInfo(this@PackActivity, p.profile, name)
            val cloudCard = Ui.card(this@PackActivity)
            cloudCard.addView(Ui.section(this@PackActivity, "☁", "Kho Cloud · offline"))
            cloudCard.addView(Ui.tv(this@PackActivity,
                if (cloud == null) "Chưa có kho Cloud cho game đang chọn."
                else "Phiên bản ${cloud.version} đã lưu trên máy. Khi mất mạng vẫn dùng được. Bản nhập tay được ưu tiên.",
                13f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 8))
            cloudCard.addView(Ui.button(this@PackActivity, "Địa chỉ danh mục Cloud") {
                val field = android.widget.EditText(this@PackActivity).apply {
                    setSingleLine(true)
                    setText(p.packCatalogUrl)
                    hint = "https://.../catalog.json"
                }
                AlertDialog.Builder(this@PackActivity, R.style.DgDialog)
                    .setTitle("Danh mục Việt hóa HTTPS")
                    .setView(field)
                    .setPositiveButton("Lưu") { _, _ ->
                        val value = field.text.toString().trim()
                        val valid = value.isEmpty() || runCatching {
                            val u = java.net.URL(value)
                            u.protocol == "https" && u.host.isNotBlank() && u.userInfo == null && u.ref == null
                        }.getOrDefault(false)
                        if (valid) p.packCatalogUrl = value
                        else android.widget.Toast.makeText(this@PackActivity, "Link HTTPS không hợp lệ", android.widget.Toast.LENGTH_SHORT).show()
                        render()
                    }.setNegativeButton("Hủy", null).show()
            })
            cloudCard.addView(Ui.switchRow(this@PackActivity, "Tự kiểm tra kho Cloud",
                "Tìm nguồn trong danh mục hoặc kiểm tra nguồn đã lưu khi chơi, mỗi 30 phút. Không xóa kho offline khi lỗi mạng.",
                p.autoPackRefresh) { p.autoPackRefresh = it })
            cloudCard.addView(Ui.button(this@PackActivity, if (linkBusy) "Đang tải…" else if (cloud?.source != null) "Kiểm tra bản Cloud mới" else "Nhập link gói Cloud") {
                val source = cloud?.source
                if (source != null) downloadLink(source) else linkDialog()
            })
            if (cloud != null) cloudCard.addView(Ui.button(this@PackActivity, "Xóa riêng kho Cloud") {
                val expected = scope()
                AlertDialog.Builder(this@PackActivity, R.style.DgDialog)
                    .setMessage("Chỉ xóa kho Cloud đã tải của game này. Giữ nguyên các câu nhập tay và bộ nhớ AI.")
                    .setPositiveButton("Xóa kho Cloud") { _, _ ->
                        if (scope() == expected) PackStore.deleteCloud(this@PackActivity, expected.first, expected.second)
                        render()
                    }
                    .setNegativeButton("Hủy", null).show()
            }, Ui.lpm(this@PackActivity, 8, 0))
            put(cloudCard, 0, 12)

            // Bộ nhớ dịch AI lưu trên thiết bị theo hồ sơ, tách khỏi gói Việt hóa nhập tay.
            val learned = LearnedTranslations.count(this@PackActivity, p.profile, name)
            val learnedCard = Ui.card(this@PackActivity)
            learnedCard.addView(Ui.section(this@PackActivity, "🧠", "Bộ nhớ dịch theo game"))
            if (name.isBlank()) learnedCard.addView(Ui.tv(this@PackActivity,
                "Hãy chọn game ở Kho game để bộ nhớ không bị trộn giữa các trò chơi.",
                12.5f, Ui.DANGER), Ui.lpm(this@PackActivity, 2, 6))
            learnedCard.addView(Ui.switchRow(this@PackActivity, "Dùng lại và tự nhớ bản dịch AI",
                "Tắt sẽ ngừng dùng và ngừng lưu câu AI mới; dữ liệu đã nhớ vẫn giữ cho đến khi bạn xóa.",
                p.learnTranslations) { enabled ->
                p.learnTranslations = enabled
                render()
            })
            learnedCard.addView(Ui.tv(this@PackActivity,
                "$learned câu đã lưu riêng cho hồ sơ ${p.profile}. Dữ liệu nằm trên thiết bị, " +
                    "không sao lưu lên đám mây; gỡ ứng dụng có thể mất dữ liệu. " +
                    "Gặp lại đúng câu và người nói sẽ không tốn lượt Gemini.",
                12.5f, Ui.SUB), Ui.lpm(this@PackActivity, 8, 10))
            if (learned > 0) {
                learnedCard.addView(Ui.button(this@PackActivity, "Xem và sửa câu đã nhớ") {
                    reviewLearnedTranslations()
                }, Ui.lpm(this@PackActivity, 2, 5))
                learnedCard.addView(Ui.button(this@PackActivity, "Tìm câu đã nhớ (gốc hoặc tiếng Việt)") {
                    searchLearnedTranslations()
                }, Ui.lpm(this@PackActivity, 2, 5))
                learnedCard.addView(Ui.button(this@PackActivity, "Xóa toàn bộ $learned câu dịch đã nhớ") {
                    AlertDialog.Builder(this@PackActivity, R.style.DgDialog)
                        .setTitle("Xóa bộ nhớ dịch của ${p.profile}?")
                        .setMessage("Chỉ xóa câu do AI tự ghi nhớ. Không xóa gói Việt hóa đã nhập, từ điển hoặc giọng nhân vật.")
                        .setPositiveButton("Xóa bộ nhớ") { _, _ ->
                            LearnedTranslations.clear(this@PackActivity, p.profile, name)
                            render()
                        }
                        .setNegativeButton("Hủy", null).show()
                }, Ui.lpm(this@PackActivity, 4, 0))
            }
            learnedCard.addView(Ui.tv(this@PackActivity,
                "Lưu ý: AI vẫn có thể dịch sai dù qua kiểm tra. Hãy sửa hoặc xóa câu nhớ sai; " +
                    "app không tự lưu lời thoại ngắn, dễ phụ thuộc ngữ cảnh.",
                12f, Ui.SUB), Ui.lpm(this@PackActivity, 9, 0))
            put(learnedCard, 0, 12)

            // Tự nhận diện người nói từ phụ đề và gán giọng cho từng nhân vật.
            val v = Ui.card(this@PackActivity)
            val detected = p.detectedSpeakers()
            val castCards = PackStore.cast(p)
            val known = (detected.map { name ->
                Cast.byName(name, castCards)?.names?.firstOrNull() ?: name
            } + castCards.mapNotNull { it.names.firstOrNull() })
                .distinctBy { it.trim().lowercase() }.take(300)
            v.addView(Ui.section(this@PackActivity, "🎙", "Giọng từng nhân vật"))
            v.addView(Ui.switchRow(this@PackActivity, "Tự gán giọng theo nhân vật",
                "Khi nhận diện tên người nói từ phụ đề, app tự giữ một giọng ổn định. Có thể đổi từng giọng bên dưới.",
                p.characterVoices) { enabled -> p.characterVoices = enabled; render() })
            v.addView(Ui.navRow(this@PackActivity, null,
                "Nhân vật đang nói khi game không hiện tên",
                p.activeSpeaker.ifBlank { "Chưa chọn · đọc giọng mặc định" }) {
                pickActiveSpeaker(known)
            })
            v.addView(Ui.tv(this@PackActivity,
                "Nhấn tên nhân vật để gán giọng; bấm Nghe thử trong bảng chọn để kiểm tra giọng hiện tại (sử dụng lượt Gemini).",
                12f, Ui.SUB), Ui.lpm(this@PackActivity, 6, 6))
            v.addView(Ui.tv(this@PackActivity,
                "Đã lưu ${detected.size} tên nhân vật trong hồ sơ này (gồm tên phát hiện và tên bạn tự thêm). " +
                    "Tự nhận diện chỉ đáng tin khi game có nhãn tên; " +
                    "chưa nhận diện được người nói sẽ dùng giọng mặc định. Giọng tự động không suy đoán giới tính theo tên.",
                12.5f, Ui.SUB), Ui.lpm(this@PackActivity, 8, 10))
            for (name in known.take(25)) {
                val voice = Prefs.aiVoiceLabel(p.aiVoiceFor(name, ""))
                val status = if (p.effectiveManualVoice(name) != null) "Đã chọn tay" else "Tự động"
                v.addView(Ui.navRow(this@PackActivity, null, name, "$voice · $status") { pickCharacterVoice(name) })
            }
            if (known.size > 25) v.addView(Ui.button(this@PackActivity,
                "Xem và chọn giọng cho tất cả ${known.size} nhân vật") {
                AlertDialog.Builder(this@PackActivity, R.style.DgDialog)
                    .setTitle("Nhân vật trong ${p.profile}")
                    .setItems(known.toTypedArray()) { _, index -> pickCharacterVoice(known[index]) }
                    .setNegativeButton("Đóng", null).show()
            }, Ui.lpm(this@PackActivity, 8, 0))
            v.addView(Ui.button(this@PackActivity, "＋  Thêm nhân vật và chọn giọng") {
                addCharacterVoice()
            }, Ui.lpm(this@PackActivity, 10, 0))
            if (detected.isNotEmpty()) {
                v.addView(Ui.button(this@PackActivity, "Xóa tên nhân vật nhận nhầm") {
                    deleteDetectedSpeaker(detected)
                }, Ui.lpm(this@PackActivity, 6, 0))
            }
            put(v, 0, 12)

            // Nhập, xuất
            val i = Ui.card(this@PackActivity)
            i.addView(Ui.section(this@PackActivity, "📥", "Nhập và chia sẻ gói"))
            i.addView(Ui.tv(this@PackActivity,
                "\"Từ file\" tự nhận dạng: gói của app (ba mục [thuật ngữ] gốc = Việt, [nhân vật] Tên = mô tả; nhịp: chậm, [câu thoại] câu gốc = câu Việt), " +
                    "hoặc MỘT file có cả hai ngôn ngữ (bảng .csv/.tsv hai cột, .json mỗi mục hai trường, .po/.mo, XLIFF, TMX, Ren'Py .rpy, phụ đề .srt hai dòng, mỗi dòng \"gốc | Việt\"), " +
                    "hoặc file .zip chứa file gốc và file Việt hoá. Câu thoại khớp với chữ trên màn hình thì được dùng luôn, không qua bộ dịch. Nhập vào hồ sơ đang chọn (${p.profile}).", 12.5f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 10))
            val r = Ui.hbox(this@PackActivity)
            r.addView(Ui.button(this@PackActivity, "📂  Từ file") { pickFile(31) }, Ui.lp(0, WRAP_CONTENT, 1f))
            r.addView(Ui.button(this@PackActivity, "🔗  Từ link") { linkDialog() }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@PackActivity, 10) })
            i.addView(r)
            i.addView(Ui.button(this@PackActivity, "🔀  Ghép 2 file: file gốc + file Việt hoá") { joinStart() }, Ui.lpm(this@PackActivity, 8, 0))
            i.addView(Ui.tv(this@PackActivity,
                "Bản Việt hoá của game thường gồm hai file chữ cùng cấu trúc: file gốc (en.json, strings.xml...) và file đã dịch (vi.json...). " +
                    "Chọn \"Ghép 2 file\" rồi chọn lần lượt hai file; app ghép theo khoá hoặc theo thứ tự dòng. Khi chơi, câu nào trên màn hình khớp một câu gốc trong file " +
                    "(kể cả khi chữ đọc lệch vài ký tự) thì hiện đúng bản Việt hoá trong file, như chơi bản Việt hoá trên máy tính.\n" +
                    "Đọc được: .json, .xml (strings.xml, .resx), .strings, .properties/.ini, .yml (Paradox), .srt, .csv/.tsv, .po/.mo, .zip chứa các file đó, file mỗi dòng một câu. " +
                    "Câu có biến số như {tên} hay %s bị bỏ qua vì app không biết điền. Bản vá dạng nhị phân (.exe, .pak, .locres, .xdelta…) sửa thẳng file game thì app không đọc được: " +
                    "hãy cài bản vá đó vào game như bình thường, app vẫn dịch những chỗ còn lại.", 12f, Ui.SUB), Ui.lpm(this@PackActivity, 10, 0))
            i.addView(Ui.button(this@PackActivity, "📤  Lưu gói của mình ra file") { saveFile() }, Ui.lpm(this@PackActivity, 8, 0))
            i.addView(Ui.button(this@PackActivity, "📤  Xuất câu thoại CSV (gốc, Việt, game)") { saveFile(true) }, Ui.lpm(this@PackActivity, 8, 0))
            put(i, 0, 12)

            val t = Ui.card(this@PackActivity)
            t.addView(Ui.section(this@PackActivity, "🌐", "Bản Việt hoá của cộng đồng"))
            t.addView(Ui.tv(this@PackActivity,
                "Các nhóm Việt hoá do người hâm mộ làm bản vá (mod) sửa chữ trong file game, cài vào game bạn đã có. Khác với gói dịch phụ đề ở đây và dùng song song được. " +
                    "Dịch game chỉ mở trang gốc của họ, không tải hộ, không chép lại bản vá. Mỗi bản vá có điều khoản của tác giả.", 12.5f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 10))
            for (n in NguonVietHoa.ALL) {
                t.addView(Ui.button(this@PackActivity, n.name + "  ·  " + n.plats.joinToString(" ") { it.label }) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.url))) } catch (_: Exception) {}
                }, Ui.lpm(this@PackActivity, 6, 0))
            }
            t.addView(Ui.tv(this@PackActivity, NguonVietHoa.SAFETY, 11.5f, Ui.SUB), Ui.lpm(this@PackActivity, 10, 0))
            put(t, 0, 0)
        }
    }

    /** Quản lý các câu đã nhập trên máy, tách biệt với kho Cloud và bộ nhớ AI. */
    private fun addCommunityDraft() {
        val game = PackStore.gameName(p).trim()
        if (game.isBlank()) {
            Toast.makeText(this, "Hãy chọn game trong Kho game trước khi đóng góp.", Toast.LENGTH_LONG).show()
            return
        }
        val source = Ui.field(this, "Câu gốc trong game", "")
        val translation = Ui.field(this, "Bản dịch tiếng Việt", "")
        val contributor = Ui.field(this, "Tên đóng góp (không bắt buộc)", "")
        source.minLines = 2
        translation.minLines = 3
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 18)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Bản nháp sẽ gắn với game và hồ sơ hiện tại. Chưa gửi lên máy chủ, chưa công khai và không thay thế bản dịch đã xác minh.", 12.5f, Ui.SUB))
        box.addView(source, Ui.lpm(this, 0, 8))
        box.addView(translation, Ui.lpm(this, 0, 8))
        box.addView(contributor)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Soạn đóng góp · $game")
            .setView(box)
            .setPositiveButton("Lưu bản nháp") { _, _ ->
                runCatching {
                    CommunityLocalStore.addDraft(this, p.profile, game, source.text.toString(),
                        translation.text.toString(), contributor.text.toString())
                }.onSuccess {
                    Toast.makeText(this, "Đã lưu bản nháp trên thiết bị. Chưa gửi lên kho cộng đồng.", Toast.LENGTH_LONG).show()
                    render()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Không lưu được bản nháp.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun addCommunityReport() {
        val game = PackStore.gameName(p).trim()
        if (game.isBlank()) {
            Toast.makeText(this, "Hãy chọn game trong Kho game trước khi báo lỗi.", Toast.LENGTH_LONG).show()
            return
        }
        val source = Ui.field(this, "Câu gốc hoặc câu cần báo lỗi", "")
        val reason = Ui.field(this, "Lý do (dịch sai, sai ngữ cảnh, sai tên riêng…)", "")
        source.minLines = 2
        reason.minLines = 2
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 18)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Báo lỗi được lưu cục bộ để bạn kiểm tra lại trước khi gửi. Không làm thay đổi câu trong kho.", 12.5f, Ui.SUB))
        box.addView(source, Ui.lpm(this, 0, 8))
        box.addView(reason)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Báo bản dịch sai · $game")
            .setView(box)
            .setPositiveButton("Lưu báo lỗi") { _, _ ->
                runCatching {
                    CommunityLocalStore.addReport(this, p.profile, game, source.text.toString(), reason.text.toString())
                }.onSuccess {
                    Toast.makeText(this, "Đã lưu báo lỗi trên thiết bị. Chưa gửi cho người kiểm duyệt.", Toast.LENGTH_LONG).show()
                    render()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Không lưu được báo lỗi.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun showCommunityDrafts() {
        val game = PackStore.gameName(p)
        val entries = CommunityLocalStore.drafts(this, p.profile, game)
        if (entries.isEmpty()) { render(); return }
        val labels = entries.map { "BẢN NHÁP · ${it.source.take(54)}\\n→ ${it.translation.take(72)}" }.toTypedArray()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Bản nháp trên thiết bị · ${entries.size}")
            .setItems(labels) { _, index ->
                val item = entries[index]
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setTitle("Bản nháp đóng góp")
                    .setMessage("Game: ${item.game}\\nNgười đóng góp: ${item.contributor.ifBlank { "Không ghi tên" }}\\n\\n${item.source}\\n\\n→ ${item.translation}\\n\\nTrạng thái: CHƯA GỬI · CHƯA XÁC MINH")
                    .setPositiveButton("Xóa bản nháp") { _, _ ->
                        CommunityLocalStore.deleteDraft(this, p.profile, game, item.id)
                        render()
                    }
                    .setNegativeButton("Đóng", null).show()
            }
            .setNegativeButton("Đóng", null).show()
    }

    private fun showCommunityReports() {
        val game = PackStore.gameName(p)
        val entries = CommunityLocalStore.reports(this, p.profile, game)
        if (entries.isEmpty()) { render(); return }
        val labels = entries.map { "CHƯA GỬI · ${it.source.take(55)}\\n${it.reason.take(72)}" }.toTypedArray()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Báo lỗi lưu trên thiết bị · ${entries.size}")
            .setItems(labels) { _, index ->
                val item = entries[index]
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setTitle("Chi tiết báo lỗi")
                    .setMessage("Game: ${item.game}\\nCâu: ${item.source}\\nLý do: ${item.reason}\\n\\nTrạng thái: CHƯA GỬI")
                    .setPositiveButton("Xóa báo lỗi") { _, _ ->
                        CommunityLocalStore.deleteReport(this, p.profile, game, item.id)
                        render()
                    }
                    .setNegativeButton("Đóng", null).show()
            }
            .setNegativeButton("Đóng", null).show()
    }

    private fun reviewPackLines(search: String = "") {
        val all = PackStore.localLines(this, p.profile)
        val q = search.trim().lowercase(java.util.Locale.ROOT)
        val entries = all.filter { q.isBlank() ||
            it.first.lowercase(java.util.Locale.ROOT).contains(q) ||
            it.second.lowercase(java.util.Locale.ROOT).contains(q)
        }.take(100)
        if (entries.isEmpty()) {
            Toast.makeText(this, "Không có câu đã nhập nào khớp.", Toast.LENGTH_SHORT).show()
            return
        }
        val choices = entries.map { it.first.take(68) + "\n→ " + it.second.take(88) }.toTypedArray()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Câu đã nhập trên máy · ${entries.size}/${all.size}")
            .setItems(choices) { _, index -> editPackLine(entries[index]) }
            .setPositiveButton("Tìm") { _, _ -> searchPackLines() }
            .setNegativeButton("Đóng", null).show()
    }

    private fun searchPackLines() {
        val field = Ui.field(this, "Tìm trong câu gốc hoặc tiếng Việt", "")
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Tìm câu đã nhập")
            .setView(field)
            .setPositiveButton("Tìm") { _, _ -> reviewPackLines(field.text.toString()) }
            .setNegativeButton("Hủy", null).show()
    }

    private fun editPackLine(entry: Pair<String, String>) {
        val source = Ui.field(this, "Câu gốc", entry.first).apply {
            minLines = 2
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val translation = Ui.field(this, "Bản tiếng Việt", entry.second).apply {
            minLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 18)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Chỉ sửa dữ liệu đã lưu trên máy. Kho Cloud không bị thay đổi.", 12.5f, Ui.SUB),
            Ui.lpm(this, 0, 8))
        box.addView(source)
        box.addView(translation, Ui.lpm(this, 0, 8))
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Sửa câu Việt hóa")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val ok = runCatching {
                    PackStore.editLocalLine(this, p.profile, entry.first, source.text.toString(), translation.text.toString())
                }.getOrDefault(false)
                if (!ok) Toast.makeText(this, "Không thể lưu. Hãy kiểm tra câu gốc và bản dịch.", Toast.LENGTH_LONG).show()
                render()
            }
            .setNeutralButton("Xóa câu") { _, _ ->
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setMessage("Xóa câu này khỏi dữ liệu lưu trên máy? Bản cùng câu trong kho Cloud (nếu có) vẫn được giữ.")
                    .setPositiveButton("Xóa") { _, _ ->
                        if (!PackStore.deleteLocalLine(this, p.profile, entry.first))
                            Toast.makeText(this, "Không tìm thấy câu để xóa.", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton("Hủy", null).show()
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun searchLearnedTranslations() {
        val field = Ui.field(this, "Nhập từ/câu cần tìm trong bản gốc hoặc tiếng Việt", "")
        val box = Ui.vbox(this).apply {
            val pad = Ui.dp(this@PackActivity, 20)
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Tìm trong bộ nhớ dịch")
            .setView(box)
            .setPositiveButton("Tìm") { _, _ ->
                reviewLearnedTranslations(field.text.toString().trim())
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun reviewLearnedTranslations(search: String = "") {
        val entries = LearnedTranslations.recent(
            this, p.profile, PackStore.gameName(p), 80, search)
        if (entries.isEmpty()) {
            Toast.makeText(this, "Không có câu dịch nào khớp từ khóa.", Toast.LENGTH_SHORT).show()
            return
        }
        val choices = entries.map { it.original.take(65) + "\n→ " + it.vi.take(85) }.toTypedArray()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle(if (search.isBlank()) "Câu đã nhớ · ${p.profile}" else "Kết quả: $search")
            .setItems(choices) { _, index -> editLearnedTranslation(entries[index]) }
            .setNegativeButton("Đóng", null).show()
    }

    private fun editLearnedTranslation(entry: LearnedTranslations.Entry) {
        val field = Ui.field(this, "Bản tiếng Việt chính xác", entry.vi)
        field.minLines = 3
        field.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 18)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, "Câu gốc: " + entry.original, 13f, Ui.SUB), Ui.lpm(this, 0, 8))
        box.addView(field)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Sửa bản dịch đã nhớ")
            .setView(box)
            .setPositiveButton("Lưu bản sửa") { _, _ ->
                if (!LearnedTranslations.edit(this, p.profile, entry.key, field.text.toString()))
                    Toast.makeText(this, "Bản dịch không hợp lệ.", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNeutralButton("Xóa câu này") { _, _ ->
                LearnedTranslations.delete(this, p.profile, entry.key)
                render()
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun deleteDetectedSpeaker(detected: List<String>) {
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Chọn tên nhận diện sai")
            .setItems(detected.toTypedArray()) { _, index ->
                val name = detected[index]
                AlertDialog.Builder(this, R.style.DgDialog)
                    .setTitle("Xóa '$name'?")
                    .setMessage("Xóa tên này khỏi danh sách đã nhận diện và bỏ lựa chọn giọng riêng cho tên đó. Nhân vật có sẵn trong gói Việt hóa vẫn được giữ.")
                    .setPositiveButton("Xóa") { _, _ ->
                        p.forgetDetectedSpeaker(name)
                        render()
                    }
                    .setNegativeButton("Hủy", null).show()
            }
            .setNegativeButton("Đóng", null).show()
    }

    private fun pickActiveSpeaker(names: List<String>) {
        val options = (listOf("Không chọn nhân vật") + names).toTypedArray()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Người đang nói (không có nhãn)")
            .setItems(options) { _, index ->
                p.activeSpeaker = if (index == 0) "" else names[index - 1]
                render()
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun pickCharacterVoice(name: String) {
        val automatic = Prefs.aiVoiceLabel(p.automaticAiVoiceFor(name, ""))
        val labels = (listOf("Tự động · $automatic") + Prefs.AI_VOICES.map { it.second }).toTypedArray()
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Giọng nhân vật: $name")
            .setItems(labels) { _, index ->
                p.setManualVoice(name, if (index == 0) null else Prefs.AI_VOICES[index - 1].first)
                render()
            }
            .setNeutralButton("▶ Nghe thử") { _, _ -> previewCharacterVoice(name) }
            .setNegativeButton("Hủy", null).show()
    }

    /** Phát mẫu bằng chính giọng đã gán, không tự gọi Gemini khi người dùng chưa bấm nghe. */
    private fun previewCharacterVoice(name: String) {
        if (!p.characterVoices) {
            Toast.makeText(this, "Hãy bật Tự gán giọng theo nhân vật trước.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!p.aiVoiceOn) {
            Toast.makeText(this, "Hãy chọn Gemini AI ở Cài đặt giọng đọc và thêm khóa Gemini.", Toast.LENGTH_LONG).show()
            return
        }
        if (voicePreview == null) {
            voicePreview = Speaker(this, p)
        }
        voicePreview?.speak("Xin chào, đây là giọng đọc tiếng Việt của nhân vật $name.", speakerName = name, interrupt = true)
    }

    private fun addCharacterVoice() {
        val edit = Ui.field(this, "Tên nhân vật như trong phụ đề", "")
        val box = Ui.vbox(this).apply {
            val n = Ui.dp(this@PackActivity, 20)
            setPadding(n, n / 2, n, 0)
            addView(edit)
        }
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Thêm người nói thủ công")
            .setView(box)
            .setPositiveButton("Tiếp tục") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.length !in 2..40 || name.count { it.isLetter() } < 2) {
                    Toast.makeText(this, "Tên nhân vật không hợp lệ.", Toast.LENGTH_SHORT).show()
                } else {
                    p.rememberSpeaker(name, fromUser = true)
                    pickCharacterVoice(name)
                }
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun editDialog(title: String, hint: String, example: String, value: String, save: (String) -> Unit) {
        val et = Ui.field(this, hint, value)
        et.minLines = 7
        et.gravity = android.view.Gravity.TOP
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(Ui.tv(this, example, 12.5f, Ui.SUB))
        box.addView(et)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle(title).setView(box)
            .setPositiveButton("Lưu") { _, _ -> save(et.text.toString().trim()); render() }
            .setNegativeButton("Hủy", null).show()
    }

    private fun pickFile(req: Int) {
        if (req != 34) fileScope = scope()
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), req)
    }

    /** Hỏi cách nhập file Việt hoá (mở từ Kho game). */
    private fun importMenu() {
        val items = arrayOf("🔀  Ghép 2 file: file gốc + file Việt hoá", "📂  Một file (gói, hai ngôn ngữ, hoặc .zip)", "🔗  Từ link")
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Nhập file Việt hoá cho ${p.profile}")
            .setItems(items) { _, w -> when (w) { 0 -> joinStart(); 1 -> pickFile(31); else -> linkDialog() } }
            .setNegativeButton("Hủy", null).show()
    }

    private fun joinStart() {
        origText = null
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Ghép 2 file")
            .setMessage("Bước 1: chọn file chữ GỐC của game (tiếng Anh, Nhật...).\nBước 2: chọn file đã VIỆT HOÁ cùng cấu trúc.\n\n" +
                "App ghép hai file theo khoá (hoặc theo thứ tự dòng nếu là .srt hay file từng dòng) rồi nhập vào hồ sơ ${p.profile}.")
            .setPositiveButton("Chọn file gốc") { _, _ -> pickFile(33) }
            .setNegativeButton("Hủy", null).show()
    }

    private fun nameOf(uri: Uri): String {
        var n: String? = null
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) n = c.getString(0)
            }
        } catch (_: Exception) {}
        return n ?: uri.lastPathSegment.orEmpty()
    }

    /** Chép file đã chọn vào bộ nhớ tạm của app (tối đa [MAX_COPY] byte) để đọc nhiều lần, kể cả .zip. */
    private fun copyToCache(uri: Uri): java.io.File {
        val f = java.io.File.createTempFile("nhap", ".tmp", cacheDir)
        try {
            var size = 0L
            contentResolver.openInputStream(uri)?.use { ins ->
                f.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > MAX_COPY) throw java.io.IOException("File quá lớn (tối đa ${MAX_COPY / 1_000_000} MB).")
                        out.write(buf, 0, n)
                    }
                }
            } ?: throw java.io.IOException("Không mở được file.")
            return f
        } catch (e: Exception) {
            f.delete()
            throw e
        }
    }

    /** Đọc file chữ (tối đa [MAX_FILE] byte), tự nhận mã hoá. */
    private fun readText(uri: Uri): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        contentResolver.openInputStream(uri)?.use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_FILE) throw java.io.IOException("File quá lớn (tối đa ${MAX_FILE / 1_000_000} MB).")
            }
        }
        return PackText.decode(out.toByteArray())
    }

    private fun saveFile(csv: Boolean = false) {
        if (csv && PackStore.gameName(p).isBlank()) {
            Toast.makeText(this, "Hãy chọn game trước khi xuất CSV.", Toast.LENGTH_LONG).show()
            return
        }
        fileScope = scope()
        val n = PackStore.gameName(p).ifEmpty { p.profile }.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if (csv) "text/csv" else "text/plain")
            .putExtra(Intent.EXTRA_TITLE, "goi-viet-hoa-$n." + if (csv) "csv" else "txt"), if (csv) 35 else 32)
    }

    private fun linkDialog() {
        val et = Ui.field(this, "https://… (file trực tiếp hoặc .dichgame.json)", "")
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        AlertDialog.Builder(this, R.style.DgDialog)
            .setTitle("Nhập gói từ link").setMessage("Chỉ dùng link file được phép tải và chia sẻ. Chọn đúng game. Gói Cloud .dichgame.json được kiểm tra tên game và SHA-256, lưu offline và giữ bản dịch đang có.").setView(box)
            .setPositiveButton("Tải và nhập") { _, _ ->
                val u = et.text.toString().trim()
                if (u.isEmpty()) return@setPositiveButton
                downloadLink(u)
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun downloadLink(url: String) {
        if (linkBusy) {
            Toast.makeText(this, "Đang tải một gói khác.", Toast.LENGTH_SHORT).show()
            return
        }
        val expected = scope()
        val prefs = p
        val app = applicationContext
        linkBusy = true
        Toast.makeText(this, "Đang tải gói…", Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                PackStore.importLink(app, prefs, url, expected.first, expected.second)
            } catch (e: Exception) {
                "Không tải được gói: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                linkBusy = false
                if (!isFinishing && !isDestroyed) done(msg)
            }
        }.start()
    }

    private fun done(msg: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this, R.style.DgDialog).setMessage(msg).setPositiveButton("Đã hiểu", null).show()
        render()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        val expected = fileScope
        if (req in 31..35 && (expected == null || expected != scope())) {
            origText = null
            done("Game đã thay đổi. Hãy chọn lại file cho đúng game.")
            return
        }
        if (req == 31) {
            val nm = nameOf(uri)
            Thread {
                val msg = try {
                    val tmp = copyToCache(uri)
                    try { requireScope(expected); PackStore.importFile(this, p, nm, tmp) } finally { tmp.delete() }
                } catch (e: Exception) { "Không đọc được file: " + (e.message ?: e.javaClass.simpleName) }
                runOnUiThread { done(msg) }
            }.start()
        } else if (req == 33) {
            val nm = nameOf(uri)
            Thread {
                try {
                    val t = readText(uri)
                    runOnUiThread {
                        if (expected != scope()) { done("Game đã thay đổi. Hãy chọn lại file."); return@runOnUiThread }
                        origName = nm; origText = t
                        Toast.makeText(this, "Đã đọc file gốc. Bước 2: chọn file đã Việt hoá.", Toast.LENGTH_LONG).show()
                        pickFile(34)
                    }
                } catch (e: Exception) {
                    runOnUiThread { done("Không đọc được file gốc: " + (e.message ?: e.javaClass.simpleName)) }
                }
            }.start()
        } else if (req == 34) {
            val o = origText
            if (o == null) { done("Chưa có file gốc. Hãy chọn lại từ bước 1."); return }
            val nm = nameOf(uri)
            Thread {
                val msg = try { val translated = readText(uri); requireScope(expected); PackStore.importJoined(this, p, origName, o, nm, translated) } catch (e: Exception) { "Không đọc được file Việt hoá: " + (e.message ?: e.javaClass.simpleName) }
                runOnUiThread { origText = null; done(msg) }
            }.start()
        } else if (req == 32 || req == 35) {
            Thread {
                val msg = try {
                    requireScope(expected)
                    val content = if (req == 35) PackStore.exportCsv(this, p) else PackStore.exportText(this, p)
                    requireScope(expected)
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("Không mở được file.")
                    stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    "Đã lưu gói."
                } catch (e: Exception) {
                    "Không lưu được: " + (e.message ?: "")
                }
                runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            }.start()
        }
    }

    private companion object {
        /** File chữ của game có thể lớn (hàng chục nghìn câu), nhưng quá mức này thường không phải file chữ. */
        const val MAX_FILE = 24_000_000

        /** File chọn để nhập (có thể là .zip chứa nhiều file chữ). */
        const val MAX_COPY = 80_000_000L
    }
}
