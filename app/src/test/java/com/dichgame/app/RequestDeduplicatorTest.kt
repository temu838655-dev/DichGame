package com.dichgame.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestDeduplicatorTest {
    @Test fun identicalConcurrentRequestsShareOneComputation() {
        val dedup = RequestDeduplicator<String, String>()
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val secondCalling = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<String> {
                dedup.execute("same-prompt") {
                    calls.incrementAndGet()
                    started.countDown()
                    assertTrue(finish.await(3, TimeUnit.SECONDS))
                    "translated"
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val second = pool.submit<String> {
                secondCalling.countDown()
                dedup.execute("same-prompt") {
                    calls.incrementAndGet()
                    "should-not-run"
                }
            }
            assertTrue(secondCalling.await(2, TimeUnit.SECONDS))
            // Give the second worker time to join the already-running FutureTask.
            Thread.sleep(50)
            finish.countDown()
            assertEquals("translated", first.get(3, TimeUnit.SECONDS))
            assertEquals("translated", second.get(3, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
            assertEquals(0, dedup.inFlightCount())
        } finally {
            finish.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun failedRequestsAreRemovedSoTheNextRequestCanRetry() {
        val dedup = RequestDeduplicator<String, String>()
        try {
            dedup.execute("same-prompt") { throw IllegalStateException("temporary") }
            throw AssertionError("expected failure")
        } catch (e: IllegalStateException) {
            assertEquals("temporary", e.message)
        }
        assertEquals(0, dedup.inFlightCount())
        assertEquals("recovered", dedup.execute("same-prompt") { "recovered" })
    }

    @Test fun differentRequestKeysDoNotShareResults() {
        val dedup = RequestDeduplicator<String, String>()
        assertEquals("one", dedup.execute("prompt-one") { "one" })
        assertEquals("two", dedup.execute("prompt-two") { "two" })
    }
}
