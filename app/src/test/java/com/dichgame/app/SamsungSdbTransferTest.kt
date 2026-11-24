package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SamsungSdbTransferTest {
    @Test fun transferChecksHandshakeAndSyncAck() {
        val pool = Executors.newSingleThreadExecutor()
        val data = ByteArray(3500) { (it % 251).toByte() }
        try {
            ServerSocket(0).use { listener ->
                val task = pool.submit<ByteArray> {
                    listener.accept().use { peer ->
                        peer.soTimeout = 5000
                        val input = peer.getInputStream()
                        val output = peer.getOutputStream()
                        fun read() = SamsungSdb.decode(input)
                        fun send(cmd: Int, a: Int, b: Int, bytes: ByteArray = byteArrayOf()) {
                            output.write(SamsungSdb.encode(SamsungSdb.Packet(cmd,a,b,bytes))); output.flush()
                        }
                        val hello = read()
                        assertEquals(SamsungSdb.CNXN, hello.cmd)
                        assertEquals(0x00100000, hello.a)
                        send(SamsungSdb.CNXN, 0x00100000, 1024, "device::test".toByteArray())
                        val open = read()
                        assertEquals(SamsungSdb.OPEN, open.cmd)
                        assertEquals("sync:\u0000", open.data.toString(Charsets.UTF_8))
                        val local = open.a
                        send(SamsungSdb.OKAY, 7, local)
                        val collected = java.io.ByteArrayOutputStream()
                        while (true) {
                            val p = read()
                            assertEquals(SamsungSdb.WRTE, p.cmd)
                            assertTrue(p.data.size <= 1024)
                            val tag = String(p.data, 0, 4, Charsets.US_ASCII)
                            send(SamsungSdb.OKAY, 7, local)
                            if (tag == "DATA") collected.write(p.data, 8, p.data.size - 8)
                            if (tag == "DONE") {
                                send(SamsungSdb.WRTE, 7, local, SamsungSdb.syncFrame("OKAY",0))
                                assertEquals(SamsungSdb.OKAY, read().cmd)
                                break
                            }
                        }
                        collected.toByteArray()
                    }
                }
                SamsungSdb("192.168.1.2", Socket("127.0.0.1", listener.localPort)).use {
                    it.connect()
                    it.push("/home/owner/share/tmp/sdk_tools/test.wgt", data)
                }
                assertArrayEquals(data, task.get(10, TimeUnit.SECONDS))
            }
        } finally { pool.shutdownNow() }
    }
}
