package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class WebReceiverPairingTest {
    @Test fun approvalAndRevocation() {
        var clock = 0L
        var saved = emptySet<String>()
        val p = WebReceiverPairing(persist = { saved = it }, now = { clock })
        val r = p.register()!!
        assertEquals(8, r.code.length)
        assertNull(p.request(r.id)!!.token)
        assertFalse(p.approve("invalid"))
        assertTrue(p.approve(r.code))
        assertFalse(p.approve(r.code))
        val token = p.request(r.id)!!.token!!
        assertTrue(p.valid(token))
        assertTrue(WebReceiverPairing(saved = saved, now = { clock }).valid(token))
        clock = 120001
        assertNull(p.request(r.id))
        assertTrue(p.valid(token))
        p.revokeAll()
        assertFalse(p.valid(token))
        assertTrue(saved.isEmpty())
    }

    @Test fun perPeerLimitAndResetPreserveApprovedToken() {
        val p = WebReceiverPairing(now = { 0L })
        val r = p.register("192.168.1.2")!!
        assertTrue(p.approve(r.code))
        val token = p.request(r.id)!!.token!!
        assertNotNull(p.register("192.168.1.2"))
        assertNull(p.register("192.168.1.2"))
        assertNotNull(p.register("192.168.1.3"))
        p.clearPending()
        assertTrue(p.valid(token))
        assertNotNull(p.register("192.168.1.2"))
    }

    @Test fun pendingLimit() {
        val p = WebReceiverPairing(now = { 0L })
        repeat(8) { assertNotNull(p.register()) }
        assertNull(p.register())
    }
}
