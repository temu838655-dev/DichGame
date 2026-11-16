package com.dichgame.app

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads the small Silero VAD model separately from Whisper and verifies its pinned SHA-256. */
internal class SileroVadModelInstaller(
    private val directory: File,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() { cancelled.set(true) }

    fun isInstalled(): Boolean = isValid(File(directory, FILE_NAME))

    fun install(onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        cancelled.set(false)
        directory.mkdirs()
        check(directory.isDirectory) { "Không tạo được thư mục model giọng nói." }
        val availableBytes = StatFs(directory.absolutePath).availableBytes
        if (availableBytes < MODEL_SIZE + 2L * 1024L * 1024L) {
            throw IllegalStateException("Không đủ dung lượng trống để cài bộ lọc giọng nói.")
        }
        val target = File(directory, FILE_NAME)
        if (isValid(target)) return
        val part = File(directory, "$FILE_NAME.part")
        part.delete()
        val connection = connectionFactory(URL(DOWNLOAD_URL))
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Máy chủ Silero VAD trả về HTTP ${connection.responseCode}."
            }
            val length = connection.contentLengthLong
            if (length >= 0L) check(length == MODEL_SIZE) { "Kích thước model Silero VAD không đúng." }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        check(!cancelled.get()) { "Đã hủy tải model Silero VAD." }
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MODEL_SIZE) { "File Silero VAD lớn bất thường." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(total, MODEL_SIZE)
                    }
                    output.fd.sync()
                }
            }
            check(total == MODEL_SIZE) { "Tải thiếu dữ liệu model Silero VAD." }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(SHA256, ignoreCase = true)) { "SHA-256 model Silero VAD không khớp." }
            check(!cancelled.get()) { "Đã hủy tải model Silero VAD." }
            try {
                java.nio.file.Files.move(part.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                check(part.renameTo(target)) { "Không thể hoàn tất file model Silero VAD." }
            }
            check(isValid(target)) { "Kiểm tra model Silero VAD sau cài đặt thất bại." }
        } catch (error: Throwable) {
            part.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(file: File): Boolean {
        if (!file.isFile || file.length() != MODEL_SIZE) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
            .equals(SHA256, ignoreCase = true)
    }

    companion object {
        const val FILE_NAME = "silero_vad.onnx"
        private const val MODEL_SIZE = 643_854L
        private const val SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
        private const val DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    }
}
