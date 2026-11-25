package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudRefreshPolicyTest {
    @Test fun configuredCatalogIsTheOnlySource() {
        var savedLookups = 0
        val source = CloudRefreshPolicy.resolveSource(
            catalogConfigured = true,
            catalog = { null },
            saved = { savedLookups++; "https://old.example.org/gta.dichgame.json" }
        )
        assertNull(source)
        assertEquals(0, savedLookups)
    }

    @Test fun allowedCatalogManifestIsSelected() {
        val source = CloudRefreshPolicy.resolveSource(
            catalogConfigured = true,
            catalog = { "https://packs.example.org/gta.dichgame.json" },
            saved = { "https://old.example.org/gta.dichgame.json" }
        )
        assertEquals("https://packs.example.org/gta.dichgame.json", source)
    }

    @Test fun savedSourceIsUsedOnlyWithoutConfiguredCatalog() {
        val source = CloudRefreshPolicy.resolveSource(
            catalogConfigured = false,
            catalog = { "https://packs.example.org/gta.dichgame.json" },
            saved = { "https://saved.example.org/gta.dichgame.json" }
        )
        assertEquals("https://saved.example.org/gta.dichgame.json", source)
    }
}
