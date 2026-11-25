package com.dichgame.app

/** Một ứng dụng chơi từ xa cho các máy console DịchGame hỗ trợ. */
class RemoteApp(val pkg: String, val name: String, val desc: String, val plats: Set<Plat>)

/** Chỉ liệt kê luồng chơi từ xa cho PS5, PS4 và Xbox; PC chỉ có thể là nguồn dữ liệu Việt hóa. */
object RemotePlay {
    val APPS = listOf(
        RemoteApp("com.playstation.remoteplay", "PS Remote Play", "Chơi PS4/PS5 từ xa (app chính thức của Sony)", setOf(Plat.PS5, Plat.PS4)),
        RemoteApp("com.metallic.chiaki", "Chiaki", "Chơi PS4/PS5 từ xa (mã nguồn mở)", setOf(Plat.PS5, Plat.PS4)),
        RemoteApp("com.microsoft.xboxone.smartglass", "Xbox", "Chơi Xbox từ xa, Game Pass", setOf(Plat.XBOX)),
    )

    /** Các ứng dụng phù hợp với console đang chọn được xếp trước. */
    fun ordered(p: Plat?): List<RemoteApp> =
        if (p == null || p !in SUPPORTED) emptyList()
        else APPS.filter { p in it.plats }

    fun forPlatform(p: Plat): List<RemoteApp> =
        if (p in SUPPORTED) APPS.filter { p in it.plats } else emptyList()

    fun guide(p: Plat): String = when (p) {
        Plat.PS5, Plat.PS4 ->
            "Trên máy ${p.label} bật Chơi từ xa (Cài đặt › Hệ thống › Chơi từ xa). Mở PS Remote Play hoặc Chiaki trên điện thoại, ghép với máy và vào game. " +
                "Rồi quay lại Dịch game bấm Bắt đầu, bản dịch sẽ hiện đè lên hình game."
        Plat.XBOX ->
            "Trên Xbox bật Chơi từ xa (Cài đặt › Thiết bị và kết nối › Tính năng từ xa). Mở app Xbox trên điện thoại, chọn Chơi từ xa trên thiết bị này, " +
                "vào game rồi quay lại Dịch game bấm Bắt đầu."
        else -> "DịchGame hiện hỗ trợ chơi và dịch trên PS5, PS4 và Xbox. Bản Việt hóa PC chỉ là nguồn dữ liệu tham khảo cho cùng game."
    }

    val SUPPORTED = setOf(Plat.PS5, Plat.PS4, Plat.XBOX)
}
