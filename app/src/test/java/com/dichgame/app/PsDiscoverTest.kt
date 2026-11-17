package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PsDiscoverTest {
    private fun resp(code: String, ver: String, type: String, name: String): ByteArray =
        ("HTTP/1.1 $code\nhost-id:AABBCCDDEEFF\nhost-type:$type\n" + (if (name.isNotEmpty()) "host-name:$name\n" else "") +
            "host-request-port:997\ndevice-discovery-protocol-version:$ver\nsystem-version:08000000\n").toByteArray()

    @Test fun parsesReadyPs5() {
        val b = resp("200 Ok", "00030010", "PS5", "PS5-123")
        val h = PsDiscoverProto.parse(b, b.size, "192.168.1.20")!!
        assertEquals("192.168.1.20", h.addr)
        assertEquals("PS5-123", h.name)
        assertTrue(h.ps5)
        assertTrue(h.ready)
        assertEquals("AABBCCDDEEFF", h.id)
    }

    @Test fun parsesStandbyPs4() {
        val b = resp("620 Server Standby", "00020020", "PS4", "")
        val h = PsDiscoverProto.parse(b, b.size, "10.0.0.5")!!
        assertFalse(h.ps5)
        assertFalse(h.ready)
        assertEquals("PS4", h.name)
    }

    @Test fun ignoresOwnSearchPacketAndGarbage() {
        val own = PsDiscoverProto.packet(PsDiscoverProto.VER_PS5)
        assertNull(PsDiscoverProto.parse(own, own.size, "1.2.3.4"))
        assertNull(PsDiscoverProto.parse(ByteArray(0), 0, "1.2.3.4"))
        val other = "HTTP/1.1 404 Not Found\n".toByteArray()
        assertNull(PsDiscoverProto.parse(other, other.size, "1.2.3.4"))
    }

    @Test fun searchPacketFormat() {
        assertEquals("SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00030010\n", String(PsDiscoverProto.packet("00030010")))
    }

    private fun ip(s: String) = PsDiscoverProto.ipToInt(s)!!

    @Test fun ipRoundTrip() {
        assertEquals("192.168.1.23", PsDiscoverProto.intToIp(ip("192.168.1.23")))
        assertEquals("255.255.255.255", PsDiscoverProto.intToIp(ip("255.255.255.255")))
        assertNull(PsDiscoverProto.ipToInt("192.168.1"))
        assertNull(PsDiscoverProto.ipToInt("300.1.1.1"))
        assertNull(PsDiscoverProto.ipToInt("abc"))
    }

    @Test fun broadcastAddress() {
        assertEquals("192.168.1.255", PsDiscoverProto.intToIp(PsDiscoverProto.broadcastOf(ip("192.168.1.23"), 24)))
        assertEquals("192.168.3.255", PsDiscoverProto.intToIp(PsDiscoverProto.broadcastOf(ip("192.168.1.23"), 22)))
        assertEquals("10.255.255.255", PsDiscoverProto.intToIp(PsDiscoverProto.broadcastOf(ip("10.4.5.6"), 8)))
    }

    @Test fun sweepSkipsSelfNetworkAndBroadcast() {
        val t = PsDiscoverProto.sweepTargets(ip("192.168.1.23"), 24)
        assertEquals(253, t.size)
        assertFalse(t.contains("192.168.1.23"))
        assertFalse(t.contains("192.168.1.0"))
        assertFalse(t.contains("192.168.1.255"))
        assertTrue(t.contains("192.168.1.1"))
        assertTrue(t.contains("192.168.1.254"))
    }

    @Test fun sweepOfBigNetworkStaysInOwn24() {
        val t = PsDiscoverProto.sweepTargets(ip("10.1.2.3"), 8)
        assertEquals(253, t.size)
        assertTrue(t.all { it.startsWith("10.1.2.") })
    }

    @Test fun sweepOf22Covers1021() {
        assertEquals(1021, PsDiscoverProto.sweepTargets(ip("192.168.4.10"), 22).size)
    }

    @Test fun sweepOfTinyNetworkIsEmpty() {
        assertTrue(PsDiscoverProto.sweepTargets(ip("10.0.0.1"), 31).isEmpty())
    }

    /** Máy PS giả trên 127.0.0.1: nghe một cổng UDP, thấy gói SRCH đúng phiên bản thì trả lời như máy thật. */
    private class FakePs(val ver: String, val type: String, val code: String) {
        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        @Volatile var asked = 0
        private val t = Thread {
            val buf = ByteArray(1024)
            try {
                while (true) {
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    val q = String(buf, 0, p.length)
                    if (!q.startsWith("SRCH * HTTP/1.1")) continue
                    asked++
                    if (!q.contains("device-discovery-protocol-version:$ver")) continue
                    val r = ("HTTP/1.1 $code\nhost-id:112233445566\nhost-type:$type\nhost-name:Fake-$type\n" +
                        "host-request-port:997\ndevice-discovery-protocol-version:$ver\nsystem-version:09000000\n").toByteArray()
                    socket.send(DatagramPacket(r, r.size, p.address, p.port))
                }
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true; start() }
        fun close() { try { socket.close() } catch (_: Throwable) {} }
    }

    private class Box { @Volatile var v: List<PsHost>? = null; @Volatile var calls = 0 }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(20) }
        return cond()
    }

    @Test fun scannerFindsPs5ByUnicastSweepOnly() {
        val ps5 = FakePs(PsDiscoverProto.VER_PS5, "PS5", "200 Ok")
        val ps4Port = DatagramSocket(0, InetAddress.getByName("127.0.0.1")) // cổng PS4 không ai nghe
        val s = PsScanner.newSocket()
        val box = Box()
        val sc = PsScanner(
            listOf(PsLeg(s, emptyList(), listOf("127.0.0.1"), "thử")),
            emptyList(), { box.v = it }, port4 = ps4Port.localPort, port5 = ps5.port, roundMs = 100,
        )
        sc.start()
        try {
            assertTrue("phải thấy máy giả", waitFor(5000) { box.v?.isNotEmpty() == true })
            val h = box.v!!.first()
            assertEquals("127.0.0.1", h.addr)
            assertTrue(h.ps5)
            assertTrue(h.ready)
            assertEquals("Fake-PS5", h.name)
            assertNotNull(sc.snapshot().firstOrNull())
        } finally {
            sc.stop(); ps5.close(); ps4Port.close()
        }
    }

    @Test fun scannerFindsStandbyPs4ThroughExtraTarget() {
        val ps4 = FakePs(PsDiscoverProto.VER_PS4, "PS4", "620 Server Standby")
        val ps5Port = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val s = PsScanner.newSocket()
        val box = Box()
        val sc = PsScanner(
            listOf(PsLeg(s, emptyList(), emptyList(), "thử")),
            listOf("127.0.0.1"), { box.v = it }, port4 = ps4.port, port5 = ps5Port.localPort, roundMs = 100,
        )
        sc.start()
        try {
            assertTrue(waitFor(5000) { box.v?.isNotEmpty() == true })
            assertFalse(box.v!!.first().ps5)
            assertFalse(box.v!!.first().ready)
        } finally {
            sc.stop(); ps4.close(); ps5Port.close()
        }
    }

    @Test fun scannerDropsHostThatStopsAnswering() {
        val ps5 = FakePs(PsDiscoverProto.VER_PS5, "PS5", "200 Ok")
        val s = PsScanner.newSocket()
        val box = Box()
        val sc = PsScanner(
            listOf(PsLeg(s, emptyList(), listOf("127.0.0.1"), "thử")),
            emptyList(), { box.v = it }, port4 = 9, port5 = ps5.port, roundMs = 100, dropMs = 700,
        )
        sc.start()
        try {
            assertTrue(waitFor(5000) { box.v?.isNotEmpty() == true })
            ps5.close() // máy tắt hẳn
            assertTrue("máy ngừng trả lời thì phải bị bỏ khỏi danh sách", waitFor(6000) { box.v?.isEmpty() == true })
        } finally {
            sc.stop(); ps5.close()
        }
    }

    @Test fun scannerWithNothingAroundFindsNothing() {
        val s = PsScanner.newSocket()
        val box = Box()
        val sc = PsScanner(listOf(PsLeg(s, emptyList(), listOf("127.0.0.1"), "thử")), emptyList(), { box.calls++ },
            port4 = 9, port5 = 9, roundMs = 100)
        sc.start()
        Thread.sleep(500)
        sc.stop()
        assertEquals(0, box.calls)
        assertTrue(sc.snapshot().isEmpty())
        assertTrue(sc.sent > 0)
    }
}
