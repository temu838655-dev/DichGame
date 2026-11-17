package com.dichgame.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** Một máy PS4/PS5 tìm thấy trong mạng. */
data class PsHost(
    val addr: String,
    val name: String,
    val ps5: Boolean,
    /** true = đang bật (200 Ok), false = đang nghỉ (620 Server Standby). */
    val ready: Boolean,
    val id: String = "",
    val system: String = "",
)

/**
 * Phần giao thức dò máy PlayStation (thuần Kotlin, không phụ thuộc Android nên kiểm thử được).
 * Gói hỏi: "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:<phiên bản>\n" gửi UDP tới cổng 987 (PS4) hoặc 9302 (PS5).
 * Máy trả lời "HTTP/1.1 200 Ok" (đang bật) hoặc "HTTP/1.1 620 Server Standby" (đang nghỉ) kèm các dòng "khóa:giá trị".
 */
object PsDiscoverProto {
    const val PORT_PS4 = 987
    const val PORT_PS5 = 9302
    const val VER_PS4 = "00020020"
    const val VER_PS5 = "00030010"

    fun packet(version: String): ByteArray =
        ("SRCH * HTTP/1.1\ndevice-discovery-protocol-version:$version\n").toByteArray(Charsets.UTF_8)

    /** Đọc phản hồi của máy; null nếu không phải phản hồi hợp lệ (ví dụ chính gói hỏi của mình dội lại). */
    fun parse(data: ByteArray, len: Int, from: String): PsHost? {
        if (len <= 0) return null
        val text = String(data, 0, minOf(len, data.size), Charsets.UTF_8)
        val lines = text.split('\n').map { it.trimEnd('\r') }
        val first = lines.firstOrNull()?.trim() ?: return null
        if (!first.startsWith("HTTP/1.1")) return null
        val code = first.removePrefix("HTTP/1.1").trim().takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        val ready = when (code) {
            200 -> true
            620 -> false
            else -> return null
        }
        val h = HashMap<String, String>()
        for (l in lines.drop(1)) {
            val i = l.indexOf(':')
            if (i <= 0) continue
            h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val ver = h["device-discovery-protocol-version"] ?: ""
        val type = h["host-type"] ?: ""
        val ps5 = ver == VER_PS5 || type.equals("PS5", true)
        val name = (h["host-name"] ?: "").ifBlank { if (ps5) "PS5" else "PS4" }
        return PsHost(from, name, ps5, ready, h["host-id"] ?: "", h["system-version"] ?: "")
    }

    fun ipToInt(s: String): Int? {
        val p = s.trim().split('.')
        if (p.size != 4) return null
        var r = 0
        for (x in p) {
            val n = x.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            r = (r shl 8) or n
        }
        return r
    }

    fun intToIp(v: Int): String = "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"

    private fun mask(prefix: Int): Int = if (prefix <= 0) 0 else if (prefix >= 32) -1 else (-1 shl (32 - prefix))

    /** Địa chỉ broadcast của mạng (ví dụ 192.168.1.255). */
    fun broadcastOf(ip: Int, prefix: Int): Int = ip or mask(prefix).inv()

    /**
     * Danh sách địa chỉ cần quét trong mạng của mình (trừ địa chỉ mạng, broadcast và chính mình).
     * Mạng lớn hơn /22 chỉ quét dải /24 chứa địa chỉ của mình để không gửi quá nhiều gói.
     */
    fun sweepTargets(ip: Int, prefix: Int): List<String> {
        if (prefix > 30) return emptyList()
        val p = if (prefix < 22) 24 else prefix
        val m = mask(p)
        val net = ip and m
        val bc = net or m.inv()
        val out = ArrayList<String>()
        var a = net + 1
        while (a != bc && out.size < 1100) {
            if (a != ip) out.add(intToIp(a))
            a++
        }
        return out
    }
}

/**
 * Một "chân" dò: một ổ UDP (đã gắn vào đúng mạng Wi-Fi/LAN), các địa chỉ broadcast và dải địa chỉ cần quét từng máy.
 * [label] là dòng mô tả để hiện cho người dùng (ví dụ "Wi-Fi 192.168.1.23").
 */
class PsLeg(
    val socket: DatagramSocket,
    val broadcasts: List<String>,
    val sweep: List<String>,
    val label: String,
)

/**
 * Bộ dò máy PlayStation: gửi gói hỏi theo ba cách cùng lúc để vẫn thấy máy khi router/điện thoại chặn một cách nào đó:
 * 1) 255.255.255.255, 2) địa chỉ broadcast của mạng, 3) gửi thẳng từng IP trong mạng (lặp lại vài vòng).
 * Chạy trên luồng nền; [onChange] được gọi (từ luồng nền) mỗi khi danh sách máy đổi.
 */
class PsScanner(
    private val legs: List<PsLeg>,
    private val extraTargets: List<String>,
    private val onChange: (List<PsHost>) -> Unit,
    private val port4: Int = PsDiscoverProto.PORT_PS4,
    private val port5: Int = PsDiscoverProto.PORT_PS5,
    private val roundMs: Long = 1500L,
    private val dropMs: Long = 9000L,
) {
    @Volatile private var stopped = false
    private val hosts = LinkedHashMap<String, Pair<PsHost, Long>>()
    private val threads = ArrayList<Thread>()
    @Volatile var rounds = 0
        private set
    @Volatile var sent = 0
        private set

    fun start() {
        for (leg in legs) {
            threads.add(Thread({ receiveLoop(leg) }, "dg-ps-recv").apply { isDaemon = true; start() })
        }
        threads.add(Thread({ sendLoop() }, "dg-ps-send").apply { isDaemon = true; start() })
    }

    fun stop() {
        stopped = true
        for (leg in legs) try { leg.socket.close() } catch (_: Throwable) {}
        for (t in threads) try { t.interrupt() } catch (_: Throwable) {}
    }

    fun snapshot(): List<PsHost> = synchronized(hosts) { hosts.values.map { it.first } }

    private fun sendOne(leg: PsLeg, ip: String, p4: ByteArray, p5: ByteArray) {
        try {
            val a = InetAddress.getByName(ip)
            leg.socket.send(DatagramPacket(p4, p4.size, a, port4))
            leg.socket.send(DatagramPacket(p5, p5.size, a, port5))
            sent += 2
        } catch (_: Throwable) {
        }
    }

    private fun sendLoop() {
        val p4 = PsDiscoverProto.packet(PsDiscoverProto.VER_PS4)
        val p5 = PsDiscoverProto.packet(PsDiscoverProto.VER_PS5)
        var round = 0
        while (!stopped) {
            for (leg in legs) {
                if (stopped) return
                sendOne(leg, "255.255.255.255", p4, p5)
                for (b in leg.broadcasts) sendOne(leg, b, p4, p5)
                for (e in extraTargets) sendOne(leg, e, p4, p5)
                // Quét từng IP ở vòng đầu và mỗi 3 vòng sau đó (nhiều router chặn broadcast nhưng cho gửi thẳng).
                if (round % 3 == 0) {
                    var n = 0
                    for (ip in leg.sweep) {
                        if (stopped) return
                        sendOne(leg, ip, p4, p5)
                        if (++n % 32 == 0) try { Thread.sleep(4) } catch (_: InterruptedException) { return }
                    }
                }
            }
            round++
            rounds = round
            try { Thread.sleep(roundMs) } catch (_: InterruptedException) { return }
        }
    }

    private fun receiveLoop(leg: PsLeg) {
        val buf = ByteArray(2048)
        try { leg.socket.soTimeout = 500 } catch (_: Throwable) {}
        while (!stopped) {
            var changed = false
            try {
                val pk = DatagramPacket(buf, buf.size)
                leg.socket.receive(pk)
                val from = pk.address?.hostAddress ?: ""
                val h = PsDiscoverProto.parse(buf, pk.length, from)
                if (h != null) {
                    synchronized(hosts) {
                        val old = hosts[h.addr]?.first
                        hosts[h.addr] = h to System.currentTimeMillis()
                        if (old != h) changed = true
                    }
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Throwable) {
                if (stopped) return
                try { Thread.sleep(200) } catch (_: InterruptedException) { return }
            }
            val now = System.currentTimeMillis()
            synchronized(hosts) {
                val iter = hosts.entries.iterator()
                while (iter.hasNext()) if (now - iter.next().value.second > dropMs) { iter.remove(); changed = true }
            }
            if (changed && !stopped) try { onChange(snapshot()) } catch (_: Throwable) {}
        }
    }

    companion object {
        /** Ổ UDP cho dò máy: cho phép broadcast, nhận phản hồi trên cổng ngẫu nhiên. */
        fun newSocket(): DatagramSocket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(0))
        }
    }
}
