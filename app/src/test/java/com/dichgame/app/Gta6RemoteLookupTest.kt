package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gta6RemoteLookupTest {
    @Test fun remoteLookupIsLimitedToEnglishGrandTheftAutoSix() {
        assertTrue(Gta6RemoteLookup.supports("Grand Theft Auto VI", "en"))
        assertTrue(Gta6RemoteLookup.supports("grand theft auto vi", "en"))
        assertFalse(Gta6RemoteLookup.supports("Grand Theft Auto V", "en"))
        assertFalse(Gta6RemoteLookup.supports("Grand Theft Auto VI", "fr"))
    }
}
