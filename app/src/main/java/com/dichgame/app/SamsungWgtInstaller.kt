package com.dichgame.app

import android.content.Context
import android.net.Uri
import android.util.Xml
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/** Only imports an already signed DichGame WGT; certificate validity is checked by TV. */
internal object SamsungWgtInstaller {
    private const val APP = "DichGameTV.Receiver"
    private const val PKG = "DichGameTV"
    private const val MAX = 32 * 1024 * 1024

    private fun read(context: Context, uri: Uri): ByteArray {
        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                check(output.size() + n <= MAX) { "WGT vượt 32 MB" }
                output.write(buffer, 0, n)
            }
        } ?: error("Không đọc được WGT")
        return output.toByteArray()
    }

    private fun validate(context: Context, bytes: ByteArray) {
        val temp = File.createTempFile("dg-wgt-", ".wgt", context.cacheDir)
        try {
            temp.writeBytes(bytes)
            ZipFile(temp).use { zip ->
                for (name in listOf("config.xml", "author-signature.xml", "signature1.xml")) {
                    val entry = zip.getEntry(name) ?: error("WGT chưa có $name")
                    check(entry.size in 1..1048576) { "Tệp WGT không hợp lệ: $name" }
                }
                val entry = zip.getEntry("config.xml")
                check(entry.size <= 65536)
                zip.getInputStream(entry).use { input ->
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    parser.setInput(input, "UTF-8")
                    var valid = false
                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG &&
                            parser.namespace == "http://tizen.org/ns/widgets" && parser.name == "application") {
                            valid = parser.getAttributeValue(null, "id") == APP &&
                                parser.getAttributeValue(null, "package") == PKG
                        }
                        parser.next()
                    }
                    check(valid) { "Chỉ hỗ trợ WGT DichGame TV" }
                }
            }
        } finally { temp.delete() }
    }

    fun install(context: Context, ip: String, uri: Uri, status: (String) -> Unit): String {
        val bytes = read(context, uri)
        validate(context, bytes)
        SamsungSdb(ip).use { sdb ->
            status("Đang kết nối SDB...")
            sdb.connect()
            val capabilities = runCatching { sdb.capabilities() }.getOrDefault("")
            val dir = capabilities.lineSequence().firstOrNull { it.startsWith("sdk_toolpath:") }
                ?.substringAfter(':')?.trim()?.trimEnd('/')
                ?.takeIf { it in listOf("/home/owner/share/tmp/sdk_tools", "/opt/usr/apps/tmp") }
                ?: "/home/owner/share/tmp/sdk_tools"
            val remote = "$dir/dichgame-" + UUID.randomUUID().toString() + ".wgt"
            try {
                status("Đang truyền WGT đã ký...")
                sdb.push(remote, bytes)
                status("Đang cài ứng dụng...")
                val result = sdb.shell("0 vd_appinstall $PKG $remote", 120000)
                check(result.contains("install completed", ignoreCase = true) &&
                    !result.contains("install failed", ignoreCase = true)) {
                    "TV chưa xác nhận cài thành công: " + result.take(400)
                }
                status("Đang xác nhận ứng dụng đã cài...")
                val apps = sdb.shell("0 vd_applist")
                check(apps.contains(APP) || apps.contains(PKG)) { "Chưa xác nhận được DichGame trong danh sách app TV" }
                val opened = runCatching { sdb.shell("0 was_execute $APP") }.getOrDefault("")
                return if (opened.contains(APP) && (opened.contains("launched", true) || opened.contains("resumed", true))) {
                    "TV xác nhận đã cài và mở DichGame."
                } else "TV xác nhận đã cài. Hãy mở DichGame trong Apps trên TV."
            } finally { runCatching { sdb.shell("0 rmfile $remote", 5000) } }
        }
    }
}
