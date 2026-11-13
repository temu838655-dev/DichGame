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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
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
            val m = Ui.card(this@PackActivity)
            m.addView(Ui.section(this@PackActivity, "✏️", "Của bạn · ${p.profile}"))
            m.addView(Ui.tv(this@PackActivity, "$terms thuật ngữ · $cast nhân vật · $lines câu thoại đã nhập. Phần của bạn luôn được ưu tiên hơn gói có sẵn.", 13f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 10))
            m.addView(Ui.button(this@PackActivity, "Sửa từ điển (gốc = tiếng Việt)") {
                editDialog("Từ điển · ${p.profile}", "Mỗi dòng một từ: gốc = tiếng Việt", "Ví dụ:\nBonfire = Lửa trại\nTarnished = Kẻ Hoen Ố", p.glossary) { p.glossary = it }
            })
            m.addView(Ui.button(this@PackActivity, "Sửa thẻ nhân vật") {
                editDialog("Thẻ nhân vật · ${p.profile}", "Tên = mô tả giọng, xưng hô; nhịp: chậm", "Ví dụ:\nGeralt = trầm, ít lời, hay mỉa mai; xưng \"tôi\"; nhịp: chậm\nCiri = trẻ, bộc trực; gọi Geralt thân mật; nhịp: nhanh", p.cast) { p.cast = it }
            }, Ui.lpm(this@PackActivity, 8, 0))
            if (lines > 0) m.addView(Ui.button(this@PackActivity, "Xóa $lines câu thoại đã nhập") {
                AlertDialog.Builder(this@PackActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage("Xóa các câu thoại đã nhập cho hồ sơ ${p.profile}? Gói có sẵn không bị ảnh hưởng.")
                    .setPositiveButton("Xóa") { _, _ -> PackStore.deleteLines(this@PackActivity, p.profile); render() }
                    .setNegativeButton("Hủy", null).show()
            }, Ui.lpm(this@PackActivity, 8, 0))
            put(m, 0, 12)

            // Nhập, xuất
            val i = Ui.card(this@PackActivity)
            i.addView(Ui.section(this@PackActivity, "📥", "Nhập và chia sẻ gói"))
            i.addView(Ui.tv(this@PackActivity,
                "Gói là file văn bản UTF-8 có ba mục: [thuật ngữ] gốc = tiếng Việt, [nhân vật] Tên = mô tả; nhịp: chậm, và [câu thoại] câu gốc = câu Việt hoá. " +
                    "Câu thoại khớp với chữ trên màn hình thì được dùng luôn, không qua bộ dịch. Gói được nhập vào hồ sơ đang chọn (${p.profile}).", 12.5f, Ui.SUB), Ui.lpm(this@PackActivity, 0, 10))
            val r = Ui.hbox(this@PackActivity)
            r.addView(Ui.button(this@PackActivity, "📂  Từ file") { pickFile() }, Ui.lp(0, WRAP_CONTENT, 1f))
            r.addView(Ui.button(this@PackActivity, "🔗  Từ link") { linkDialog() }, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(this@PackActivity, 10) })
            i.addView(r)
            i.addView(Ui.button(this@PackActivity, "📤  Lưu gói của mình ra file") { saveFile() }, Ui.lpm(this@PackActivity, 8, 0))
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title).setView(box)
            .setPositiveButton("Lưu") { _, _ -> save(et.text.toString().trim()); render() }
            .setNegativeButton("Hủy", null).show()
    }

    private fun pickFile() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 31)
    }

    private fun saveFile() {
        val n = PackStore.gameName(p).ifEmpty { p.profile }.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, "goi-viet-hoa-$n.txt"), 32)
    }

    private fun linkDialog() {
        val et = Ui.field(this, "https://… (file .txt, GitHub dạng /blob/ cũng được)", "")
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Nhập gói từ link").setView(box)
            .setPositiveButton("Tải và nhập") { _, _ ->
                val u = et.text.toString().trim()
                if (u.isEmpty()) return@setPositiveButton
                Toast.makeText(this, "Đang tải gói…", Toast.LENGTH_SHORT).show()
                Thread {
                    val msg = try { PackStore.importText(this, p, PackStore.fetch(u)) } catch (e: Exception) { "Không tải được gói: " + (e.message ?: e.javaClass.simpleName) }
                    runOnUiThread { done(msg) }
                }.start()
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun done(msg: String) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setMessage(msg).setPositiveButton("Đã hiểu", null).show()
        render()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        if (req == 31) {
            Thread {
                val msg = try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.size > 8_000_000) "File quá lớn (tối đa 8 MB)." else PackStore.importText(this, p, String(bytes, Charsets.UTF_8))
                } catch (e: Exception) { "Không đọc được file: " + (e.message ?: e.javaClass.simpleName) }
                runOnUiThread { done(msg) }
            }.start()
        } else if (req == 32) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(PackStore.exportText(this, p).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Đã lưu gói.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Không lưu được: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}
