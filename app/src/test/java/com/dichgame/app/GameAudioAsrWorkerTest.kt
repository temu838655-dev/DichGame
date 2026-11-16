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

        // A continuous voiced segment is emitted only when stop flushes the segmenter.
        // Wait for the worker to drain and flush before expecting a transcript.
        assertTrue(worker.stopAndJoin())
        assertTrue(transcriptLatch.await(2, TimeUnit.SECONDS))
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
    @Test
    fun stopWaitsForInFlightRecognitionBeforeReturning() {
        val queue = GameAudioPcmQueue()
        val recognitionStarted = CountDownLatch(1)
        val allowRecognitionToFinish = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val worker = GameAudioAsrWorker(
            queue = queue,
            modelDirectory = File("unused-test-model"),
            onTranscript = {},
            onError = {},
            engineFactory = { _, _ ->
                object : GameAudioTranscriber {
                    override fun transcribe(samples: ShortArray, sampleRate: Int, channels: Int): String {
                        recognitionStarted.countDown()
                        assertTrue(allowRecognitionToFinish.await(2, TimeUnit.SECONDS))
                        return "final line"
                    }
                    override fun close() {}
                }
            },
        )

        assertTrue(worker.start())
        repeat(10) { queue.offer(ShortArray(320) { 6_000 }, 16_000, 1) }
        val stopper = Thread {
            worker.stopAndJoinUntilStopped()
            stopReturned.countDown()
        }
        stopper.start()

        assertTrue(recognitionStarted.await(2, TimeUnit.SECONDS))
        // Whisper is still processing: teardown must not continue to queue.close().
        assertEquals(1L, stopReturned.count)
        allowRecognitionToFinish.countDown()
        assertTrue(stopReturned.await(2, TimeUnit.SECONDS))
        stopper.join(2_000L)
        queue.close()
    }


    @Test
    fun interruptedTeardownStillWaitsAndRestoresInterruptFlag() {
        val queue = GameAudioPcmQueue()
        val recognitionStarted = CountDownLatch(1)
        val allowRecognitionToFinish = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val interruptRestored = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = GameAudioAsrWorker(
            queue = queue,
            modelDirectory = File("unused-test-model"),
            onTranscript = {},
            onError = {},
            engineFactory = { _, _ ->
                object : GameAudioTranscriber {
                    override fun transcribe(samples: ShortArray, sampleRate: Int, channels: Int): String {
                        recognitionStarted.countDown()
                        assertTrue(allowRecognitionToFinish.await(2, TimeUnit.SECONDS))
                        return "final line"
                    }
                    override fun close() {}
                }
            },
        )

        assertTrue(worker.start())
        repeat(10) { queue.offer(ShortArray(320) { 6_000 }, 16_000, 1) }
        assertTrue(recognitionStarted.await(2, TimeUnit.SECONDS))
        val stopper = Thread {
            worker.stopAndJoinUntilStopped()
            interruptRestored.set(Thread.currentThread().isInterrupted)
            stopReturned.countDown()
        }
        stopper.start()
        stopper.interrupt()

        assertEquals(1L, stopReturned.count)
        allowRecognitionToFinish.countDown()
        assertTrue(stopReturned.await(2, TimeUnit.SECONDS))
        stopper.join(2_000L)
        assertTrue(interruptRestored.get())
        queue.close()
    }

}
