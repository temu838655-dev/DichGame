package com.dichgame.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LgDeveloperKeyTest {
    @Test fun privateAddressesAccepted() {
        listOf("10.0.0.2", "172.16.0.1", "172.31.255.254", "192.168.1.20").forEach {
            assertTrue(it, LgDeveloperKey.isPrivateIpv4(it))
        }
    }
    @Test fun invalidOrPublicAddressesRejected() {
        listOf("", "localhost", "127.0.0.1", "8.8.8.8", "172.15.0.1", "172.32.0.1",
            "192.168.1.256", "192.168.01.1", "192.168.1.1:9991", "192.168.1.1/path",
            "192.168.1", "192.168.1.1 ", "::1", "169.254.1.1").forEach {
            assertFalse(it, LgDeveloperKey.isPrivateIpv4(it))
        }
    }
}
