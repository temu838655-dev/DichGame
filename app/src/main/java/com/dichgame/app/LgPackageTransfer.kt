package com.dichgame.app

import android.content.Context
import com.jcraft.jsch.Session
import java.util.UUID

/** webOS TV CLI uses SSH stream transfer; SFTP is not available on every TV. */
internal object LgPackageTransfer {
    fun upload(context: Context, session: Session): String {
        check(session.isConnected) { "Chưa kết nối TV" }
        val prepared = LgDeveloperCommand.execute(session,
            "/usr/bin/test -d /media/developer/temp || /bin/mkdir -p /media/developer/temp", 10000)
        check(prepared.exitCode == 0) { "Không tạo được thư mục cài đặt trên TV" }
        val remote = "/media/developer/temp/dichgame-" + UUID.randomUUID() + ".ipk"
        try {
            val pkg = context.assets.open("tv/dichgame-lg.ipk").use { it.readBytes() }
            check(pkg.isNotEmpty() && pkg.size <= 16 * 1024 * 1024) { "IPK không hợp lệ" }
            val sent = LgDeveloperCommand.execute(session, "/bin/cat > '$remote'", 60000,
                stdin = pkg.inputStream())
            check(sent.exitCode == 0) { "Không truyền được IPK" }
            val verified = LgDeveloperCommand.execute(session, "/usr/bin/wc -c < '$remote'", 10000)
            check(verified.exitCode == 0 && verified.output.trim().toLongOrNull() == pkg.size.toLong()) {
                "Kích thước IPK trên TV không khớp"
            }
            return remote
        } catch (e: Exception) {
            runCatching { remove(session, remote) }
            throw e
        }
    }

    fun remove(session: Session, remote: String) {
        require(remote.matches(Regex("/media/developer/temp/dichgame-[0-9a-f-]{36}\\.ipk")))
        val result = LgDeveloperCommand.execute(session, "/bin/rm -f '$remote'", 10000)
        check(result.exitCode == 0) { "Không xóa được IPK tạm" }
    }
}
