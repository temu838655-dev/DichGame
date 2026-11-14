package com.dichgame.app

/**
 * Kiểm tra mã ghép nối Remote Play.
 * Chỉ nhận đúng 8 chữ số ASCII (0–9), không chấp nhận dấu cách, ký tự Unicode hoặc chuỗi rỗng.
 */
internal fun isValidPairingPin(value: String): Boolean =
    value.length == 8 && value.all { it in '0'..'9' }

internal fun parsePairingPin(value: String): Int? {
    if (!isValidPairingPin(value)) return null
    return value.toIntOrNull()
}
