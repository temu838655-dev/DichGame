package com.dichgame.app

/** Một app chơi từ xa hoặc chơi đám mây, và các nền tảng nó dùng được cho. */
class RemoteApp(val pkg: String, val name: String, val desc: String, val plats: Set<Plat>)

/** Các app chơi từ xa thường dùng và cách dùng theo từng nền tảng. Dịch game không tự điều khiển máy chơi game, chỉ hiện bản dịch đè lên hình. */
object RemotePlay {
    val APPS = listOf(
        RemoteApp("com.playstation.remoteplay", "PS Remote Play", "Chơi PS4/PS5 từ xa (app chính thức của Sony)", setOf(Plat.PS5, Plat.PS4)),
        RemoteApp("com.metallic.chiaki", "Chiaki", "Chơi PS4/PS5 từ xa (mã nguồn mở)", setOf(Plat.PS5, Plat.PS4)),
        RemoteApp("com.limelight", "Moonlight", "Chơi game từ máy tính qua Sunshine hoặc GeForce", setOf(Plat.PC, Plat.STEAM)),
        RemoteApp("com.valvesoftware.steamlink", "Steam Link", "Chơi game Steam trên điện thoại", setOf(Plat.PC, Plat.STEAM)),
        RemoteApp("com.nvidia.geforcenow", "GeForce NOW", "Chơi game đám mây, có nhiều game Steam", setOf(Plat.PC, Plat.STEAM)),
        RemoteApp("com.microsoft.xboxone.smartglass", "Xbox", "Chơi Xbox từ xa, Game Pass", setOf(Plat.XBOX)),
    )

    /** Các app hợp với nền tảng [p] đứng trước, các app còn lại đứng sau. */
    fun ordered(p: Plat?): List<RemoteApp> =
        if (p == null) APPS else APPS.filter { p in it.plats } + APPS.filter { p !in it.plats }

    fun forPlatform(p: Plat): List<RemoteApp> = APPS.filter { p in it.plats }

    fun guide(p: Plat): String = when (p) {
        Plat.PS5, Plat.PS4 ->
            "Trên máy ${p.label} bật Chơi từ xa (Cài đặt › Hệ thống › Chơi từ xa). Mở PS Remote Play hoặc Chiaki trên điện thoại, ghép với máy và vào game. " +
                "Rồi quay lại Dịch game bấm Bắt đầu, bản dịch sẽ hiện đè lên hình game."
        Plat.XBOX ->
            "Trên Xbox bật Chơi từ xa (Cài đặt › Thiết bị và kết nối › Tính năng từ xa). Mở app Xbox trên điện thoại, chọn Chơi từ xa trên thiết bị này, " +
                "vào game rồi quay lại Dịch game bấm Bắt đầu."
        Plat.PC, Plat.STEAM ->
            "Cài Sunshine (dùng với Moonlight) hoặc bật Steam Link trên máy tính cùng mạng Wi-Fi. Mở Moonlight hoặc Steam Link, vào game " +
                "rồi quay lại Dịch game bấm Bắt đầu. Chơi ngay trên điện thoại hoặc máy tính bảng chạy game thì chỉ cần bấm Bắt đầu."
        Plat.SWITCH ->
            "Switch không có chơi từ xa. Cần một bộ thu hình HDMI (capture card) nối Switch với điện thoại, mở app xem camera USB, " +
                "rồi bấm Bắt đầu để Dịch game đọc chữ trên màn hình điện thoại."
    }
}
