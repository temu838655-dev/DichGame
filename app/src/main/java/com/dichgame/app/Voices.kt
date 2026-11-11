package com.dichgame.app

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Một gói giọng đọc offline có thể tải về. */
class VoicePack(
    val id: String,
    val name: String,
    val desc: String,
    val url: String,
    /** Dung lượng tải về, MB (gần đúng, để báo trước cho người dùng). */
    val downloadMb: Int,
    val license: String,
    val credit: String,
)

/** Danh sách gói giọng và việc tải / xóa chúng. Giọng nằm trong bộ nhớ riêng của app, xóa app là mất. */
object Voices {
    val PACKS = listOf(
        VoicePack(
            id = "vais1000",
            name = "Giọng Piper · VAIS-1000",
            desc = "Giọng tiếng Việt chạy hoàn toàn trên máy, không cần mạng, không tốn lượt gọi AI.",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-vi_VN-vais1000-medium.tar.bz2",
            downloadMb = 65,
            license = "CC BY 4.0",
            credit = "Bộ dữ liệu VAIS-1000 (Vais), huấn luyện bằng Piper, đóng gói bởi dự án sherpa-onnx.",
        ),
    )

    fun pack(id: String): VoicePack? = PACKS.firstOrNull { it.id == id }

    fun dir(ctx: Context, id: String) = File(ctx.filesDir, "voices/$id")

    fun installed(ctx: Context, id: String): Boolean = File(dir(ctx, id), "ok").exists()

    fun sizeMb(ctx: Context, id: String): Int =
        ((dir(ctx, id).listFiles()?.sumOf { it.length() } ?: 0L) / (1024 * 1024)).toInt()

    fun delete(ctx: Context, id: String) {
        dir(ctx, id).deleteRecursively()
        File(ctx.filesDir, "voices/$id.tmp").deleteRecursively()
    }

    /**
     * Tải gói rồi giải nén thẳng từ luồng mạng (không tốn chỗ cho file nén). Chạy ở luồng nền.
     * Trả về null nếu xong, hoặc câu báo lỗi. [onProgress] nhận 0..100.
     */
    fun download(ctx: Context, pack: VoicePack, cancel: AtomicBoolean, onProgress: (Int) -> Unit): String? {
        val root = File(ctx.filesDir, "voices")
        val tmp = File(root, pack.id + ".tmp")
        val dst = dir(ctx, pack.id)
        try {
            root.mkdirs()
            if (root.usableSpace < (pack.downloadMb * 2L + 40L) * 1024 * 1024) return "Máy không đủ chỗ trống (cần khoảng ${pack.downloadMb * 2} MB)."
            tmp.deleteRecursively()
            tmp.mkdirs()
            val conn = (URL(pack.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DichGame")
            }
            if (conn.responseCode != 200) return "Không tải được giọng (mã ${conn.responseCode})."
            val total = conn.contentLengthLong
            var got = 0L
            var last = -1
            val counted = object : FilterInputStream(conn.inputStream) {
                override fun read(): Int { val r = super.read(); if (r >= 0) bump(1); return r }
                override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) bump(r); return r }
                fun bump(n: Int) {
                    if (cancel.get()) throw CancelledException()
                    got += n
                    if (total > 0) {
                        val pct = (got * 99 / total).toInt()
                        if (pct != last) { last = pct; onProgress(pct) }
                    }
                }
            }
            var haveModel = false
            var haveTokens = false
            TarArchiveInputStream(BZip2CompressorInputStream(counted.buffered(1 shl 16))).use { tar ->
                while (true) {
                    val e = tar.nextEntry ?: break
                    if (e.isDirectory) continue
                    val base = e.name.substringAfterLast('/')
                    val out = when {
                        base.endsWith(".onnx") -> "model.onnx"
                        base == "tokens.txt" -> "tokens.txt"
                        base == "MODEL_CARD" -> "MODEL_CARD"
                        else -> null
                    } ?: continue
                    File(tmp, out).outputStream().buffered(1 shl 16).use { o -> copy(tar, o, cancel) }
                    if (out == "model.onnx") haveModel = true
                    if (out == "tokens.txt") haveTokens = true
                }
            }
            if (!haveModel || !haveTokens) return "Gói giọng tải về bị thiếu file."
            File(tmp, "ok").writeText("1")
            dst.deleteRecursively()
            if (!tmp.renameTo(dst)) return "Không lưu được giọng."
            onProgress(100)
            return null
        } catch (_: CancelledException) {
            tmp.deleteRecursively()
            return "Đã hủy."
        } catch (e: java.io.IOException) {
            tmp.deleteRecursively()
            return "Tải giọng bị gián đoạn (kiểm tra mạng rồi thử lại)."
        } catch (e: Exception) {
            tmp.deleteRecursively()
            return "Tải giọng lỗi: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private class CancelledException : RuntimeException()

    private fun copy(i: InputStream, o: java.io.OutputStream, cancel: AtomicBoolean) {
        val buf = ByteArray(1 shl 16)
        while (true) {
            if (cancel.get()) throw CancelledException()
            val n = i.read(buf)
            if (n < 0) break
            o.write(buf, 0, n)
        }
    }
}
