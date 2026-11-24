package com.dichgame.app

import org.junit.Test
import org.junit.Assert.*

class SamsungSdbTest {
    @Test fun packetRoundTrip() {
        val data = byteArrayOf(1, 2, -1)
        val original = SamsungSdb.Packet(SamsungSdb.WRTE, 3, 7, data)
        val decoded = SamsungSdb.decode(SamsungSdb.encode(original).inputStream())
        assertEquals(original.cmd, decoded.cmd)
        assertEquals(3, decoded.a)
        assertEquals(7, decoded.b)
        assertArrayEquals(data, decoded.data)
    }
    @Test(expected = IllegalStateException::class) fun rejectsCorruption() {
        val bytes = SamsungSdb.encode(SamsungSdb.Packet(SamsungSdb.WRTE, 1, 2, byteArrayOf(42)))
        bytes[24] = 43
        SamsungSdb.decode(bytes.inputStream())
    }
    @Test fun syncFrame() {
        assertArrayEquals(byteArrayOf(68,65,84,65,2,0,0,0,1,2),
            SamsungSdb.syncFrame("DATA",2,byteArrayOf(1,2)))
    }
}
