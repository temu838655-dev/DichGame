package com.dichgame.app

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import java.util.UUID

/** Transfer a bundled receiver to the developer account. Installation is a separate step. */
internal object LgPackageTransfer {
    fun upload(context: Context, session: Session): String {
        check(session.isConnected) { "Chưa kết nối TV" }
        val remote = "/tmp/dichgame-" + UUID.randomUUID().toString() + ".ipk"
        val channel = session.openChannel("sftp") as ChannelSftp
        try {
            channel.connect(10000)
            context.assets.open("tv/dichgame-lg.ipk").use { input ->
                channel.put(input, remote)
            }
            check(channel.stat(remote).size > 0) { "IPK rỗng" }
            return remote
        } catch (e: Exception) {
            if (channel.isConnected) runCatching { channel.rm(remote) }
            throw e
        } finally {
            channel.disconnect()
        }
    }

    fun remove(session: Session, remote: String) {
        require(remote.matches(Regex("/tmp/dichgame-[0-9a-f-]{36}\\.ipk")))
        val channel = session.openChannel("sftp") as ChannelSftp
        try {
            channel.connect(10000)
            channel.rm(remote)
        } finally {
            channel.disconnect()
        }
    }
}
