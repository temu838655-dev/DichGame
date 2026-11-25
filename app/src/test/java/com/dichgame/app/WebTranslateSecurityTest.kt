package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTranslateSecurityTest {
    @Test fun onlyHttpAndHttpsPagesAreAllowed() {
        assertTrue(isAllowedWebTranslateUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(isAllowedWebTranslateUrl("http://192.168.1.20:8080/player"))
        assertFalse(isAllowedWebTranslateUrl("file:///sdcard/private.txt"))
        assertFalse(isAllowedWebTranslateUrl("content://com.example.provider/item"))
        assertFalse(isAllowedWebTranslateUrl("javascript:alert(1)"))
        assertFalse(isAllowedWebTranslateUrl("intent://open#Intent;scheme=app;end"))
        assertFalse(isAllowedWebTranslateUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(isAllowedWebTranslateUrl("https:///missing-host"))
        assertFalse(isAllowedWebTranslateUrl("https://user:pass@example.com"))
        assertFalse(isAllowedWebTranslateUrl("https://example.com:0"))
    }

    @Test fun captionGateTrimsAndRejectsDuplicates() {
        val gate = WebCaptionGate()
        assertEquals("Hello world", gate.accept("  Hello world  ", 1000))
        assertNull(gate.accept("Hello world", 2000))
    }

    @Test fun captionGateLimitsRapidDistinctCaptions() {
        val gate = WebCaptionGate(minIntervalMs = 500)
        assertEquals("First", gate.accept("First", 1000))
        assertNull(gate.accept("Second", 1200))
        assertEquals("Second", gate.accept("Second", 1500))
    }

    @Test fun captionGateRejectsEmptyOversizedAndControlText() {
        val gate = WebCaptionGate(maxLength = 10)
        assertNull(gate.accept("   ", 1000))
        assertNull(gate.accept("12345678901", 1000))
        assertNull(gate.accept("bad\u0000text", 1000))
    }

    @Test fun resetAllowsSameCaptionAfterNavigation() {
        val gate = WebCaptionGate()
        assertEquals("Loading", gate.accept("Loading", 1000))
        gate.reset()
        assertEquals("Loading", gate.accept("Loading", 1100))
    }
}
