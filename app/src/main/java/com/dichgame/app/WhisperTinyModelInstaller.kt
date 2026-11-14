package com.dichgame.app

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs the multilingual Whisper Tiny INT8 model outside the APK.
 *
 * Files are pinned to an immutable upstream Hugging Face revision and verified
 * with SHA-256 before being atomically promoted to the names expected by OfflineGameAsr.
 * Call from an IO/background executor, never the main or Chiaki decoder thread.
 */
internal class WhisperTinyModelInstaller(
    private val modelDirectory: File,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    data class Progress(
        val fileName: String,
        val fileIndex: Int,
        val fileCount: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    data class Result(val directory: File, val downloadedBytes: Long, val skippedBytes: Long)

    internal data class Asset(
        val remoteName: String,
        val localName: String,
        val size: Long,
        val sha256: String,
    )

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun isInstalled(): Boolean = assets().all { isValid(File(modelDirectory, it.localName), it) }

    fun install(onProgress: (Progress) -> Unit = {}): Result {
        cancelled.set(false)
        modelDirectory.mkdirs()
        check(modelDirectory.isDirectory) { "Không tạo được thư mục model Whisper." }

        val assets = assets()
        val requiredBytes = assets.sumOf { it.size } + MIN_FREE_SPACE_RESERVE
        val availableBytes = StatFs(modelDirectory.absolutePath).availableBytes
        check(availableBytes >= requiredBytes) {
            "Không đủ dung lượng trống. Cần khoảng ${formatMb(requiredBytes)} MB."
        }

        var downloaded = 0L
        var skipped = 0L
        assets.forEachIndexed { index, asset ->
            checkNotCancelled()
            val target = File(modelDirectory, asset.localName)
            if (isValid(target, asset)) {
                skipped += asset.size
                onProgress(Progress(asset.localName, index + 1, assets.size, asset.size, asset.size))
                return@forEachIndexed
            }

            if (target.exists()) target.delete()
            val part = File(modelDirectory, asset.localName + ".part")
            if (part.exists()) part.delete()
            downloadAndVerify(asset, part) { bytes ->
                onProgress(Progress(asset.localName, index + 1, assets.size, bytes, asset.size))
            }
            checkNotCancelled()
            try {
                java.nio.file.Files.move(
                    part.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                check(part.renameTo(target)) { "Không thể hoàn tất file model ${asset.localName}." }
            }
            downloaded += asset.size
        }

        check(assets.all { isValid(File(modelDirectory, it.localName), it) }) {
            "Kiểm tra model Whisper sau cài đặt không thành công."
        }
        return Result(modelDirectory, downloaded, skipped)
    }

    private fun downloadAndVerify(asset: Asset, part: File, progress: (Long) -> Unit) {
        val url = URL("$BASE_URL/$REVISION/${asset.remoteName}")
        val connection = connectionFactory(url)
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Máy chủ model trả về HTTP ${connection.responseCode}."
            }
            val length = connection.contentLengthLong
            if (length >= 0L) check(length == asset.size) { "Kích thước ${asset.remoteName} không đúng." }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkNotCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= asset.size) { "File ${asset.remoteName} lớn bất thường." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        progress(total)
                    }
                    output.fd.sync()
                }
            }
            check(total == asset.size) { "Tải thiếu dữ liệu cho ${asset.remoteName}." }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualHash.equals(asset.sha256, ignoreCase = true)) {
                "SHA-256 không khớp cho ${asset.remoteName}; file tải về đã bị loại bỏ."
            }
        } catch (error: Throwable) {
            part.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(file: File, asset: Asset): Boolean {
        if (!file.isFile || file.length() != asset.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash.equals(asset.sha256, ignoreCase = true)
    }

    private fun checkNotCancelled() {
        check(!cancelled.get()) { "Đã hủy tải model Whisper." }
    }

    private fun formatMb(bytes: Long): Long = (bytes + 1024 * 1024 - 1) / (1024 * 1024)

    companion object {
        private const val BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve"
        private const val REVISION = "65176e2deb88badc814a94058666cadccc29b61c"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MIN_FREE_SPACE_RESERVE = 16L * 1024L * 1024L

        // Multilingual Tiny INT8: ~103.6 MB total. Stable local names match OfflineGameAsr.
        internal fun assets() = listOf(
            Asset("tiny-encoder.int8.onnx", "encoder.onnx", 12_937_772L,
                "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
            Asset("tiny-decoder.int8.onnx", "decoder.onnx", 89_855_401L,
                "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
            Asset("tiny-tokens.txt", "tokens.txt", 816_730L,
                "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
        )
    }
}
