package com.dichgame.app

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream

/** Bounded SSH command execution for the developer installer. Run off the UI thread. */
internal object LgDeveloperCommand {
    data class Result(val exitCode: Int, val output: String)

    fun execute(session: Session, command: String, timeoutMs: Long = 60000, stdin: java.io.InputStream? = null, terminal: ((String) -> Boolean)? = null): Result {
        require(timeoutMs in 1000L..120000L)
        check(session.isConnected) { "Chưa kết nối TV" }
        val channel = session.openChannel("exec") as ChannelExec
        val output = ByteArrayOutputStream()
        try {
            channel.setCommand(command)
            channel.setInputStream(stdin)
            val input = channel.inputStream
            val errors = channel.errStream
            channel.connect(10000)
            val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
            val buffer = ByteArray(4096)
            while (true) {
                for (stream in listOf(input, errors)) {
                    while (stream.available() > 0) {
                        check(android.os.SystemClock.elapsedRealtime() < deadline) { "TV phản hồi quá lâu" }
                        val count = stream.read(buffer, 0, minOf(buffer.size, stream.available()))
                        if (count <= 0) break
                        check(output.size() + count <= 262144) { "Phản hồi TV quá lớn" }
                        output.write(buffer, 0, count)
                        if (stream === input && terminal?.invoke(output.toString("UTF-8")) == true) return Result(0, output.toString("UTF-8"))
                    }
                }
                if (channel.isClosed && input.available() == 0 && errors.available() == 0) {
                    return Result(channel.exitStatus, output.toString("UTF-8"))
                }
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "TV phản hồi quá lâu" }
                Thread.sleep(50)
            }
        } finally {
            channel.disconnect()
        }
    }
}
