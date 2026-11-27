package com.dichgame.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationModelCatalogTest {
    @Test fun geminiUsesReturnedIdsAcrossPagesAndFiltersEmbeddingOnlyModels() {
        val pages = mutableListOf<String>()
        val models = TranslationModelCatalog.pages(true) { token ->
            pages += token
            if (token.isEmpty()) JSONObject("""{"models":[{"name":"models/vector","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"next"}""")
            else JSONObject("""{"models":[{"name":"models/request-id","displayName":"Friendly","supportedGenerationMethods":["generateContent"]}]}""")
        }
        assertEquals(listOf("", "next"), pages)
        assertEquals(listOf(TranslationModelCatalog.Model("request-id", "Friendly")), models)
    }

    @Test fun compatibleEndpointRetainsItsApiPrefixAndModelId() {
        val base = "https://example.org/service/v1"
        assertEquals("$base/models", TranslationModelCatalog.cloudModelsUrl("$base/chat/completions"))
        val models = TranslationModelCatalog.pages(false) { JSONObject("""{"data":[{"id":"vendor/model-v2"}]}""") }
        assertEquals("vendor/model-v2", models.single().id)
    }

    @Test fun preferredModelMustExistInTheProviderResponse() {
        val listed = TranslationModelCatalog.Model("real-id", "Gemini 3.5 Flash-Lite")
        assertEquals(listed, TranslationModelCatalog.preferred(listOf(listed), true, false))
        assertEquals(null, TranslationModelCatalog.preferred(listOf(listed), false, true))
        val mini = TranslationModelCatalog.Model("gpt-5-mini", "GPT-5 Mini")
        assertEquals(null, TranslationModelCatalog.preferred(listOf(mini), false, false))
        assertEquals(mini, TranslationModelCatalog.preferred(listOf(mini), false, true))
    }

    @Test(expected = IllegalStateException::class)
    fun repeatedPageTokenFails() {
        TranslationModelCatalog.pages(true) { JSONObject("""{"models":[],"nextPageToken":"same"}""") }
    }
}
