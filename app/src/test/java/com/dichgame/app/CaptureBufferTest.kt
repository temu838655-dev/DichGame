package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureBufferTest {
    private class Reader : AutoCloseable {
        var closeCount = 0
        override fun close() { closeCount++ }
    }

    @Test fun allocationFailureRetriesAtTheSameSize() {
        val buffer = CaptureBuffer<Reader>()
        val portrait = Reader()
        buffer.resize(1080, 2400, { portrait }, {})
        assertThrows(IllegalStateException::class.java) {
            buffer.resize(2400, 1080, { error("allocation failed") }, {})
        }
        assertSame(portrait, buffer.reader)
        assertEquals(1080, buffer.width)
        assertEquals(2400, buffer.height)
        assertEquals(0, portrait.closeCount)

        val landscape = Reader()
        assertTrue(buffer.resize(2400, 1080, { landscape }) { assertSame(landscape, it) })
        assertSame(landscape, buffer.reader)
        assertEquals(1, portrait.closeCount)
        assertFalse(buffer.resize(2400, 1080, { error("unnecessary allocation") }, {}))
        buffer.close()
        buffer.close()
        assertEquals(1, landscape.closeCount)
    }

    @Test fun failedSurfaceSwitchClosesCandidateAndRetriesAfterRotatingBack() {
        val buffer = CaptureBuffer<Reader>()
        val portrait = Reader()
        val failed = Reader()
        buffer.resize(1080, 2400, { portrait }, {})
        assertThrows(IllegalStateException::class.java) {
            buffer.resize(2400, 1080, { failed }) { error("display resized but surface rejected") }
        }
        assertSame(portrait, buffer.reader)
        assertEquals(0, portrait.closeCount)
        assertEquals(1, failed.closeCount)

        val replacement = Reader()
        assertTrue(buffer.resize(1080, 2400, { replacement }) { assertSame(replacement, it) })
        assertSame(replacement, buffer.reader)
        assertEquals(1, portrait.closeCount)
        buffer.close()
        assertEquals(1, failed.closeCount)
    }
}
