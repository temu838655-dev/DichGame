package com.dichgame.app

import java.util.Locale

enum class Gender { MALE, FEMALE;
    val vi: String get() = if (this == MALE) "nam" else "nữ"
}

/** Một giọng AI của Gemini: [code] là mã gửi cho máy chủ (không đổi), [name] là tên người Việt hiện trong app, [tone] mô tả giọng. */
class GVoice(val code: String, val name: String, val gender: Gender, val tone: String) {
    /** "Mai · nữ · chắc chắn". */
    val label: String get() = "$name · ${gender.vi} · $tone"
}

/**
 * Danh mục 30 giọng Gemini. Giới tính theo tài liệu Google Cloud Text-to-Speech (Gemini TTS), tính chất giọng theo tài liệu
 * Gemini API (Firm, Upbeat...). Tên người Việt chỉ để hiển thị: mã giọng vẫn là tên gốc nên cài đặt cũ không bị mất.
 */
object VoiceCatalog {
    val ALL: List<GVoice> = listOf(
        // nữ
        GVoice("Kore", "Mai", Gender.FEMALE, "chắc chắn"),
        GVoice("Zephyr", "Lan", Gender.FEMALE, "tươi sáng"),
        GVoice("Sulafat", "Hương", Gender.FEMALE, "ấm áp"),
        GVoice("Aoede", "Thảo", Gender.FEMALE, "nhẹ nhàng"),
        GVoice("Gacrux", "Hạnh", Gender.FEMALE, "trưởng thành"),
        GVoice("Leda", "Linh", Gender.FEMALE, "trẻ trung"),
        GVoice("Callirrhoe", "Ngọc", Gender.FEMALE, "dễ chịu"),
        GVoice("Autonoe", "Trang", Gender.FEMALE, "tươi sáng"),
        GVoice("Despina", "Thu", Gender.FEMALE, "mượt mà"),
        GVoice("Erinome", "Phương", Gender.FEMALE, "rõ ràng"),
        GVoice("Laomedeia", "Quỳnh", Gender.FEMALE, "vui tươi"),
        GVoice("Achernar", "Diệp", Gender.FEMALE, "dịu"),
        GVoice("Pulcherrima", "Châu", Gender.FEMALE, "mạnh dạn"),
        GVoice("Vindemiatrix", "Hà", Gender.FEMALE, "dịu dàng"),
        // nam
        GVoice("Puck", "Minh", Gender.MALE, "vui tươi"),
        GVoice("Charon", "Khoa", Gender.MALE, "rõ ràng, thông tin"),
        GVoice("Achird", "Tuấn", Gender.MALE, "thân thiện"),
        GVoice("Fenrir", "Dũng", Gender.MALE, "sôi nổi"),
        GVoice("Orus", "Hùng", Gender.MALE, "chắc chắn"),
        GVoice("Enceladus", "Sơn", Gender.MALE, "hơi thở nhẹ"),
        GVoice("Iapetus", "Long", Gender.MALE, "rõ ràng"),
        GVoice("Umbriel", "Bảo", Gender.MALE, "dễ chịu"),
        GVoice("Algieba", "Hiếu", Gender.MALE, "mượt mà"),
        GVoice("Algenib", "Cường", Gender.MALE, "khàn"),
        GVoice("Rasalgethi", "Đức", Gender.MALE, "thông tin"),
        GVoice("Alnilam", "Thành", Gender.MALE, "chắc chắn"),
        GVoice("Schedar", "Việt", Gender.MALE, "đều đặn"),
        GVoice("Zubenelgenubi", "Phong", Gender.MALE, "thoải mái"),
        GVoice("Sadachbia", "Khải", Gender.MALE, "sôi nổi"),
        GVoice("Sadaltager", "Trí", Gender.MALE, "am hiểu"),
    )

    private val byCode = ALL.associateBy { it.code }

    fun byCode(code: String): GVoice? = byCode[code]

    /** Tên hiện trong app cho mã giọng; mã lạ (model mới thêm) thì hiện nguyên mã. */
    fun label(code: String): String = byCode[code]?.label ?: code

    fun pool(g: Gender): List<GVoice> = ALL.filter { it.gender == g }

    /** Danh sách hiện trong ô chọn giọng: nữ rồi nam. */
    fun labels(): List<String> = ALL.map { it.label }

    fun codeAt(index: Int): String = ALL[index.coerceIn(0, ALL.lastIndex)].code

    fun indexOf(code: String): Int = ALL.indexOfFirst { it.code == code }.coerceAtLeast(0)

    /** Tên khóa so sánh tên nhân vật: chữ thường, bỏ dấu cách và dấu câu. */
    fun nameKey(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
