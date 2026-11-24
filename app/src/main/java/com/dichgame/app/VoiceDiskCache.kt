package com.dichgame.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/** Private, disposable cache. No API keys or subtitle text in filenames. */
class VoiceDiskCache(private val dir: File, private val maxBytes: Long = 48L * 1024 * 1024) {
    companion object {
        private val lock = Any()
        private const val MAGIC = 0x44475031
        private const val MAX_ENTRY = 4 * 1024 * 1024
        fun key(vararg parts: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (part in parts) {
                val bytes = part.toByteArray(Charsets.UTF_8)
                digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
                digest.update(0.toByte())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        private fun valid(rate: Int, channels: Int, size: Int) =
            rate in 8000..96000 && channels in 1..2 && size in 1..MAX_ENTRY && size % (channels * 2) == 0
    }

    private fun file(key: String): File = File(dir, key + ".pcm")
    fun get(key: String): Pcm? = synchronized(lock) {
        if (!key.matches(Regex("[a-f0-9]{64}"))) return@synchronized null
        val f = file(key)
        if (!f.isFile) return@synchronized null
        try {
            val pcm = DataInputStream(f.inputStream().buffered()).use { input ->
                require(input.readInt() == MAGIC)
                val rate = input.readInt()
                val channels = input.readInt()
                val size = input.readInt()
                require(valid(rate, channels, size) && f.length() == size.toLong() + 16)
                Pcm(ByteArray(size).also { input.readFully(it) }, rate, channels)
            }
            f.setLastModified(System.currentTimeMillis())
            pcm
        } catch (_: Exception) {
            f.delete()
            null
        }
    }

    fun put(key: String, pcm: Pcm) = synchronized(lock) {
        if (!key.matches(Regex("[a-f0-9]{64}")) || !valid(pcm.rate, pcm.channels, pcm.data.size) ||
            pcm.data.size.toLong() + 16 > maxBytes) return@synchronized
        var temp: File? = null
        try {
            dir.mkdirs()
            val tmp = File.createTempFile("voice-", ".tmp", dir)
            temp = tmp
            DataOutputStream(tmp.outputStream().buffered()).use {
                it.writeInt(MAGIC)
                it.writeInt(pcm.rate)
                it.writeInt(pcm.channels)
                it.writeInt(pcm.data.size)
                it.write(pcm.data)
            }
            if (tmp.renameTo(file(key))) {
                val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".pcm") }
                    ?.sortedBy { it.lastModified() }.orEmpty()
                var total = files.sumOf { it.length() }
                for (f in files) {
                    if (total <= maxBytes) break
                    val size = f.length()
                    if (f.delete()) total -= size
                }
            }
        } catch (_: Exception) {
            // Cache failure must not prevent playback.
        } finally {
            temp?.delete()
        }
    }
}
