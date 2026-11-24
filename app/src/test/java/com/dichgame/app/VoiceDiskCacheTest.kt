package com.dichgame.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceDiskCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun survivesNewInstanceAndSeparatesVoices() {
        val dir = tmp.newFolder()
        val key = VoiceDiskCache.key("voice-a", "model", "style", "xin chào")
        val other = VoiceDiskCache.key("voice-b", "model", "style", "xin chào")
        val pcm = Pcm(byteArrayOf(1, 2, 3, 4), 24000, 1)
        VoiceDiskCache(dir).put(key, pcm)
        val cache = VoiceDiskCache(dir)
        val result = cache.get(key)!!
        assertArrayEquals(pcm.data, result.data)
        assertEquals(24000, result.rate)
        assertEquals(1, result.channels)
        assertNull(cache.get(other))
        assertNotEquals(VoiceDiskCache.key("a|b", "c"), VoiceDiskCache.key("a", "b|c"))
    }

    @Test fun rejectsCorruptEntriesAndLimitsSize() {
        val dir = tmp.newFolder()
        val cache = VoiceDiskCache(dir, 24)
        val a = VoiceDiskCache.key("a")
        val b = VoiceDiskCache.key("b")
        cache.put(a, Pcm(ByteArray(8), 24000, 1))
        cache.put(b, Pcm(ByteArray(8), 24000, 1))
        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 24)
        File(dir, a + ".pcm").writeBytes(byteArrayOf(1))
        assertNull(cache.get(a))
        assertFalse(File(dir, a + ".pcm").exists())
    }
}
