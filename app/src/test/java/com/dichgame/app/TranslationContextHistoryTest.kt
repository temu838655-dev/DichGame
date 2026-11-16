package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TranslationContextHistoryTest {
    @Test
    fun snapshotContainsOnlyEarlierDialogue() {
        val history = TranslationContextHistory()

        assertEquals(emptyList<String>(), history.appendAndSnapshot("A", 3))
        assertEquals(listOf("A"), history.appendAndSnapshot("B", 3))
        assertEquals(listOf("A", "B"), history.appendAndSnapshot("C", 3))
        assertEquals(listOf("B", "C"), history.appendAndSnapshot("D", 2))
    }

    @Test
    fun zeroContextDoesNotSendEarlierLines() {
        val history = TranslationContextHistory()
        history.appendAndSnapshot("Earlier line", 3)

        assertEquals(emptyList<String>(), history.appendAndSnapshot("Current line", 0))
    }

    @Test
    fun historyRemainsBoundedToTenEntries() {
        val history = TranslationContextHistory()
        repeat(12) { history.appendAndSnapshot("line-$it", 10) }

        assertEquals(
            (2..11).map { "line-$it" },
            history.appendAndSnapshot("line-12", 10)
        )
    }

    @Test
    fun concurrentUpdatesKeepSnapshotsConsistentAndBounded() {
        val history = TranslationContextHistory()
        val workers = 8
        val updatesPerWorker = 100
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)

        try {
            val futures = (0 until workers).map { worker ->
                executor.submit {
                    ready.countDown()
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    repeat(updatesPerWorker) { index ->
                        val current = "worker-$worker-line-$index"
                        val previous = history.appendAndSnapshot(current, 10)
                        assertTrue(previous.size <= 10)
                        assertFalse(previous.contains(current))
                    }
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }

            val finalSnapshot = history.appendAndSnapshot("final-line", 10)
            assertEquals(10, finalSnapshot.size)
            assertFalse(finalSnapshot.contains("final-line"))
        } finally {
            executor.shutdownNow()
        }
    }
}
