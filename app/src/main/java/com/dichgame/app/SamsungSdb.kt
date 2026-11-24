package com.dichgame.app

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal sequential SDB transport for the user's own Developer Mode TV.
 * No authentication bypass: unsupported AUTH or secure transports fail closed. */
internal class SamsungSdb(private val ip: String) : java.io.Closeable {
    internal data class Packet(val cmd: Int, val a: Int, val b: Int, val data: ByteArray)
    companion object {
        internal const val CNXN = 0x4e584e43
        internal const val OPEN = 0x4e45504f
        internal const val OKAY = 0x59414b4f
        internal const val WRTE = 0x45545257
        internal const val CLSE = 0x45534c43
        internal const val AUTH = 0x48545541
        private const val LIMIT = 262144
        internal fun encode(p: Packet): ByteArray {
            require(p.data.size <= LIMIT)
            return ByteBuffer.allocate(24 + p.data.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(p.cmd).putInt(p.a).putInt(p.b).putInt(p.data.size)
                .putInt(p.data.sumOf { it.toInt() and 255 }).putInt(p.cmd.inv()).put(p.data).array()
        }
        internal fun decode(input: InputStream): Packet {
            val rd = DataInputStream(input)
            val h = ByteArray(24); rd.readFully(h)
            val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = b.int; val a = b.int; val r = b.int; val n = b.int; val sum = b.int
            check(b.int == cmd.inv() && n in 0..LIMIT) { "SDB header không hợp lệ" }
            val data = ByteArray(n); rd.readFully(data)
            check(data.sumOf { it.toInt() and 255 } == sum) { "SDB checksum không hợp lệ" }
            return Packet(cmd, a, r, data)
        }
        private fun word(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)
        internal fun syncFrame(tag: String, length: Int, payload: ByteArray = byteArrayOf()): ByteArray {
            require(tag.length == 4)
            return ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(word(tag)).putInt(length).put(payload).array()
        }
    }

    private val socket = Socket()
    private var nextId = 1
    private var maxPayload = 4096
    private lateinit var input: InputStream
    private var deadline = 0L
    fun connect() {
        require(LgDeveloperKey.isPrivateIpv4(ip)) { "IP TV phải là IPv4 nội bộ" }
        socket.connect(InetSocketAddress(ip, 26101), 8000)
        socket.soTimeout = 15000
        input = socket.getInputStream().buffered()
        deadline = System.nanoTime() + 15_000_000_000L
        send(CNXN, 0x00100000, LIMIT, "host::\u0000".toByteArray())
        val reply = read()
        check(reply.cmd != AUTH) { "TV yêu cầu xác thực SDB chưa được hỗ trợ" }
        check(reply.cmd == CNXN && reply.b in 1..LIMIT && reply.data.toString(Charsets.UTF_8).startsWith("device::")) {
            "TV chưa chấp nhận SDB. Kiểm tra Developer Mode và Host PC IP"
        }
        maxPayload = reply.b
    }
    private fun send(cmd: Int, a: Int, b: Int, data: ByteArray = byteArrayOf()) {
        socket.getOutputStream().apply { write(encode(Packet(cmd, a, b, data))); flush() }
    }
    private fun read(): Packet {
        val remaining = (deadline - System.nanoTime()) / 1_000_000L
        check(remaining > 0) { "TV phản hồi quá lâu" }
        socket.soTimeout = remaining.coerceIn(1, 15000).toInt()
        return decode(input)
    }
    private inner class Stream(val local: Int, val remote: Int) {
        var closed = false
        private val received = ByteArrayOutputStream()
        fun handle(p: Packet) {
            check(p.b == local && p.a == remote) { "SDB stream không khớp" }
            when (p.cmd) {
                WRTE -> {
                    check(received.size() + p.data.size <= 1024 * 1024) { "Phản hồi TV quá lớn" }
                    received.write(p.data); send(OKAY, local, remote)
                }
                CLSE -> { closed = true; send(CLSE, local, remote) }
                else -> error("SDB phản hồi không hợp lệ")
            }
        }
        fun write(data: ByteArray) {
            var pos = 0
            while (pos < data.size) {
                check(!closed) { "TV đóng kết nối" }
                val end = minOf(data.size, pos + maxPayload)
                send(WRTE, local, remote, data.copyOfRange(pos, end))
                while (true) {
                    val p = read()
                    if (p.cmd == OKAY) {
                        check(p.a == remote && p.b == local) { "SDB ACK sai" }
                        break
                    }
                    handle(p)
                    check(!closed) { "TV đóng kết nối khi truyền" }
                }
                pos = end
            }
        }
        fun readAtLeast(n: Int): ByteArray {
            while (received.size() < n && !closed) handle(read())
            check(received.size() >= n) { "Phản hồi SDB thiếu dữ liệu" }
            return received.toByteArray()
        }
        fun finish(): ByteArray {
            while (!closed) handle(read())
            return received.toByteArray()
        }
        fun close() {
            if (!closed) {
                send(CLSE, local, remote)
                while (!closed) {
                    val p = read()
                    if (p.cmd == CLSE) {
                        check(p.a == remote && p.b == local); closed = true
                    } else handle(p)
                }
            }
        }
    }
    private fun open(service: String, timeout: Long): Stream {
        deadline = System.nanoTime() + timeout * 1_000_000L
        val id = nextId++
        send(OPEN, id, 0, (service + '\u0000').toByteArray(Charsets.UTF_8))
        val p = read()
        check(p.cmd == OKAY && p.b == id && p.a != 0) { "TV từ chối dịch vụ SDB: " + service.substringBefore(':') }
        return Stream(id, p.a)
    }
    fun shell(command: String, timeout: Long = 30000): String =
        open("shell:$command", timeout).finish().toString(Charsets.UTF_8)
    fun capabilities(): String {
        val raw = open("capability:", 10000).finish()
        check(raw.size >= 2)
        val n = (raw[0].toInt() and 255) or ((raw[1].toInt() and 255) shl 8)
        check(n <= 4095 && n <= raw.size - 2)
        return raw.copyOfRange(2, n + 2).toString(Charsets.UTF_8)
    }
    fun push(remote: String, bytes: ByteArray) {
        val stream = open("sync:", 120000)
        val dest = "$remote,33261".toByteArray(Charsets.UTF_8)
        stream.write(syncFrame("SEND", dest.size, dest))
        var pos = 0
        while (pos < bytes.size) {
            val end = minOf(bytes.size, pos + 65536)
            val data = bytes.copyOfRange(pos, end)
            stream.write(syncFrame("DATA", data.size, data))
            pos = end
        }
        stream.write(syncFrame("DONE", (System.currentTimeMillis() / 1000).toInt()))
        val result = stream.readAtLeast(8)
        val tag = String(result, 0, 4, Charsets.US_ASCII)
        check(tag == "OKAY") { "TV không xác nhận truyền WGT: $tag" }
        stream.write(syncFrame("QUIT", 0))
        stream.close()
    }
    override fun close() { runCatching { socket.close() } }
}
