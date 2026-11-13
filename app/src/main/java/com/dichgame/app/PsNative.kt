package com.dichgame.app

/**
 * Engine Remote Play (chiaki-ng) là thư viện native chỉ có trong bản arm64.
 * Hỏi [available] trước khi dùng bất kỳ lớp nào của com.metallic.chiaki.lib; máy ảo x86 hoặc chip 32-bit sẽ trả về false.
 */
object PsNative {
    val available: Boolean by lazy {
        try { System.loadLibrary("chiaki-jni"); true } catch (_: Throwable) { false }
    }
}
