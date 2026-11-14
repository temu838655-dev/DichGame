package com.dichgame.app

/**
 * Kiểm tra mã ghép nối Remote Play.
 * Chỉ nhận đúng 8 chữ số ASCII (0–9), không chấp nhận dấu cách, ký tự Unicode hoặc chuỗi rỗng.
 */
internal fun parsePairingPin(value: String): Int? {
    if (value.length != 8 || value.any { it !in '0'..'9' }) return null
    return value.toIntOrNull()
}
