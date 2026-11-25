package com.dichgame.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ModelChainTest {
    private val keys = listOf("A", "B")

    @Before fun clean() { KeyRing.reset(); ModelChain.reset() }
    @After fun clean2() { KeyRing.reset(); ModelChain.reset() }

    private fun exhaust(model: String) = keys.forEach { KeyRing.blockQuota(it, model, 60_000L) }

    @Test fun chosenModelComesFirstWithoutDuplicates() {
        assertEquals("my-model", ModelChain.models("my-model").first())
        assertEquals(ModelChain.models(ModelChain.DEFAULT).size, ModelChain.models(ModelChain.DEFAULT).toSet().size)
    }

    @Test fun movesToNextModelOnQuota() {
        val used = ArrayList<String>()
        val r = ModelChain.run(keys, "flash") { m ->
            used.add(m)
            if (m == "flash") { exhaust(m); throw AiException(429, "q") }
            "ok"
        }
        assertEquals("ok", r)
        assertEquals(listOf("flash", ModelChain.DEFAULT), used)
        used.clear()
        ModelChain.run(keys, "flash") { used.add(it); "ok" }
        assertEquals(listOf(ModelChain.DEFAULT), used)   // model đang nghỉ không bị gọi lại
    }

    @Test fun allModelsExhaustedThrows429WithWait() {
        ModelChain.models("flash").forEach { exhaust(it) }
        assertTrue(ModelChain.waitMs(keys, "flash") > 0L)
        try { ModelChain.run(keys, "flash") { "không được gọi" }; fail() }
        catch (e: AiException) { assertEquals(429, e.code); assertTrue(e.retryAfterMs > 0) }
    }

    @Test fun allBadKeysReportBadKeyNotQuota() {
        keys.forEach { KeyRing.blockBad(it) }
        try { ModelChain.run(keys, "flash") { "không được gọi" }; fail() }
        catch (e: AiException) { assertEquals(401, e.code); assertTrue(e.badKey) }
    }

    @Test fun wrongChosenModelIsReportedButMissingFallbackIsSkipped() {
        try { ModelChain.run(keys, "typo") { throw AiException(404, "x") }; fail() }
        catch (e: AiException) { assertEquals(404, e.code) }
        exhaust("flash")
        val r = ModelChain.run(keys, "flash") { if (it == ModelChain.DEFAULT) throw AiException(404, "x"); it }
        assertEquals("gemini-3.1-flash-lite", r)
        assertFalse(ModelChain.DEFAULT in ModelChain.models("flash"))
    }

    @Test fun otherErrorsDoNotChangeModel() {
        var n = 0
        try { ModelChain.run(keys, "flash") { n++; throw AiException(503, "x") }; fail() }
        catch (e: AiException) { assertEquals(503, e.code) }
        assertEquals(1, n)
    }

    @Test fun splitsKeysPastedTogether() {
        assertEquals(listOf("AIzaA", "AIzaB", "AIzaC", "AQ.d-_e"), KeyRing.splitKeys(" AIzaA AIzaB,AIzaC;\n\"AQ.d-_e\"\nAIzaA "))
        assertTrue(KeyRing.splitKeys("  \n ").isEmpty())
    }

    @Test fun projectLevel403RotatesKey() {
        assertTrue(KeyRing.isBadKey(403, """{"error":{"message":"Your project has been denied access. Please contact support.","status":"PERMISSION_DENIED"}}"""))
        assertTrue(KeyRing.isBadKey(403, """{"error":{"message":"Generative Language API has not been used in project 1 before or it is disabled.","details":[{"reason":"SERVICE_DISABLED"}]}}"""))
        assertTrue(KeyRing.isBadKey(403, """{"error":{"details":[{"reason":"CONSUMER_SUSPENDED"}]}}"""))
        assertFalse(KeyRing.isBadKey(400, """{"error":{"message":"billing account"}}"""))
    }
}
