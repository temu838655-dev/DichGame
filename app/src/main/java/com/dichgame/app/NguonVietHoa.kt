package com.dichgame.app

/**
 * Danh mục các trang Việt hoá của cộng đồng (theo bộ "Dich-Game-Viet-Hoa-Source-Index").
 *
 * Đây chỉ là danh sách LIÊN KẾT: app mở trang gốc, không tải, không chép, không đăng lại bản vá của ai.
 * Bản vá (mod) sửa chữ trong file game là việc khác với dịch phụ đề trên màn hình của app này; dùng song song được.
 * Mỗi bản vá có điều khoản riêng của tác giả, nên luôn tải từ trang gốc.
 */
class Nguon(
    val name: String,
    val url: String,
    val plats: Set<Plat>,
    /** Một dòng nói trang này có gì, theo cách mô tả của danh mục nguồn. */
    val note: String,
    /** Có tìm theo tên game bằng Google `site:` được không (trang web thường). */
    val searchable: Boolean = true,
)

object NguonVietHoa {
    val ALL: List<Nguon> = listOf(
        Nguon("The Red Team", "https://theredteam.vn/viet-hoa", setOf(Plat.PC, Plat.STEAM, Plat.SWITCH, Plat.PS5),
            "Nhóm Việt hoá của người hâm mộ, có trang riêng cho từng game."),
        Nguon("PS Việt Hóa", "https://psviethoa.com/viethoa", setOf(Plat.PS4, Plat.PS5),
            "Danh mục bản Việt hoá cho PS4 và PS5. Kiểm tra mã game (title ID), vùng và phiên bản trước khi cài."),
        Nguon("Tổ Mèo Cam", "https://tomeocam.vn/", setOf(Plat.PC, Plat.STEAM, Plat.SWITCH),
            "Danh mục và trình cài Việt hoá cho PC, Steam Deck và Switch."),
        Nguon("NSW Việt Hóa Hub", "https://nswviethoa.com/", setOf(Plat.SWITCH),
            "Danh mục Việt hoá Switch. Mỗi bản vá ghi rõ mã game, phiên bản cập nhật và cách cài."),
        Nguon("Vịt Việt Hóa", "https://vitviethoa.com/", setOf(Plat.PC, Plat.STEAM),
            "Danh mục bản Việt hoá PC."),
        Nguon("VuiKey", "https://vuikey.com/viet-hoa", setOf(Plat.PC, Plat.STEAM),
            "Danh mục bản Việt hoá PC và Steam."),
        Nguon("VietPatch", "https://vietpatch.online/", setOf(Plat.PC),
            "Danh mục bản Việt hoá PC. Tự kiểm tra tác giả và độ tương thích trước khi dùng."),
        Nguon("viet-hoa-pc (GitHub)", "https://github.com/ngothienlo/viet-hoa-pc", setOf(Plat.PC, Plat.STEAM),
            "Dự án mã nguồn mở Việt hoá PC. Làm theo giấy phép của kho và ghi chú từng game.", searchable = false),
        Nguon("UNLOGICAL Việt hoá (GitHub)", "https://github.com/Foxiary/ul-vi-patch/releases", setOf(Plat.SWITCH),
            "Ví dụ bản vá Switch (Ryujinx) khớp chặt phiên bản game.", searchable = false),
        Nguon("PS-Translator (GitHub)", "https://github.com/kyozxx/PS-Translator", setOf(Plat.PS5),
            "Danh mục game và hướng dẫn dịch bằng AI cho PS5. Không phải kho bản vá đã làm xong.", searchable = false),
    )

    /** Các nguồn dùng được cho nền tảng này, nguồn nhắc tới nền tảng đó lên trước. */
    fun forPlatform(p: Plat?): List<Nguon> = if (p == null) ALL else ALL.filter { p in it.plats }

    /** Link tìm game trong một trang (Google `site:`). Trả về null nếu nguồn không phải trang web thường. */
    fun searchUrl(n: Nguon, game: String): String? {
        if (!n.searchable) return null
        val host = n.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
        return "https://www.google.com/search?q=" + java.net.URLEncoder.encode("site:$host $game", "UTF-8")
    }

    /** Lời nhắc an toàn khi cài bản vá của cộng đồng (theo hướng dẫn của danh mục nguồn). */
    const val SAFETY: String =
        "Trước khi cài bản vá: đúng nền tảng, mã game (title ID), vùng và phiên bản game. Sao lưu file gốc để quay lại được. " +
            "Chỉ tải từ trang gốc của tác giả; app không tải hộ, không đăng lại bản vá, và không hỗ trợ game lậu."
}
