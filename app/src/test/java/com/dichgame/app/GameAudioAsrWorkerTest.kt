package com.dichgame.app

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioAsrWorkerTest {
    @Test
    fun consumesPcmOnWorkerAndFlushesOnStop() {
        val queue = GameAudioPcmQueue()
        val transcriptLatch = CountDownLatch(1)
        val transcripts = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val worker = GameAudioAsrWorker(
            queue = queue,
            modelDirectory = File("unused-test-model"),
            onTranscript = { text ->
                synchronized(transcripts) { transcripts.add(text) }
                transcriptLatch.countDown()
            },
            onError = { message -> synchronized(errors) { errors.add(message) } },
            engineFactory = { _, _ ->
                object : GameAudioTranscriber {
                    override fun transcribe(samples: ShortArray, sampleRate: Int, channels: Int) = "short line"
                    override fun close() {}
                }
            },
        )

        assertTrue(worker.start())
        assertFalse(worker.start())
        repeat(10) { queue.offer(ShortArray(320) { 6_000 }, 16_000, 1) }
        assertTrue(transcriptLatch.await(2, TimeUnit.SECONDS))
        assertTrue(worker.stopAndJoin())
        queue.close()

        synchronized(transcripts) { assertEquals(listOf("short line"), transcripts) }
        synchronized(errors) { assertTrue(errors.isEmpty()) }
    }

    @Test
    fun missingModelReportsErrorWithoutCrashingCaller() {
        val queue = GameAudioPcmQueue()
        val errorLatch = CountDownLatch(1)
        var message = ""
        val worker = GameAudioAsrWorker(
            queue = queue,
            modelDirectory = File("missing"),
            onTranscript = {},
            onError = { message = it; errorLatch.countDown() },
            engineFactory = { _, _ -> throw IllegalStateException("model missing") },
        )

        assertTrue(worker.start())
        assertTrue(errorLatch.await(2, TimeUnit.SECONDS))
        assertTrue(worker.stopAndJoin())
        queue.close()
        assertTrue(message.contains("model missing"))
    }
}
