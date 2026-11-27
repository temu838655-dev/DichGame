package com.dichgame.app

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CloudBillingGateTest {
    @Test fun billingStopSurvivesNewInstanceAndOnlyMatchesSameCredentialFingerprint() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("cloud-billing-gate-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val first = CloudBillingGate(prefs)
        assertFalse(first.isBlocked("account-A"))
        assertTrue(first.block("account-A"))
        assertTrue(CloudBillingGate(prefs).isBlocked("account-A"))

        // A different API key or endpoint produces a different fingerprint and clears the old stop.
        assertFalse(CloudBillingGate(prefs).isBlocked("account-B"))
        assertFalse(CloudBillingGate(prefs).isBlocked("account-A"))

        assertTrue(first.block("account-A"))
        assertTrue(first.clear())
        assertFalse(CloudBillingGate(prefs).isBlocked("account-A"))
    }
}
