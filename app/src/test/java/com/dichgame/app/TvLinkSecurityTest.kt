package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLinkSecurityTest {
    @Test fun allowsLoopbackAndPrivateLanHosts() {
        assertTrue(isLocalTvHost("127.0.0.1"))
        assertTrue(isLocalTvHost("192.168.1.100"))
        assertTrue(isLocalTvHost("10.0.0.20"))
        assertTrue(isLocalTvHost("172.20.0.5"))
        assertTrue(isLocalTvHost("fc00::1"))
        assertTrue(isLocalTvHost("fe80::1"))
    }

    @Test fun resolverReturnsTheNumericAddressUsedForTheConnection() {
        val resolved = resolveLocalTvAddress("192.168.1.100")
        assertNotNull(resolved)
        assertEquals("192.168.1.100", resolved!!.hostAddress)
    }

    @Test fun rejectsPublicAndMalformedHostsBeforeSendingAudio() {
        assertFalse(isLocalTvHost("8.8.8.8"))
        assertFalse(isLocalTvHost("1.1.1.1"))
        assertFalse(isLocalTvHost(""))
        assertFalse(isLocalTvHost("https://example.com"))
        assertFalse(isLocalTvHost("user@192.168.1.2"))
    }
}
