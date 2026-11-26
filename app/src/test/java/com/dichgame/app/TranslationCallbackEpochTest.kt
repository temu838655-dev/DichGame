package com.dichgame.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCallbackEpochTest {
    @Test fun timedOutRequestCannotPublishAfterNextRequestStarts() {
        val epoch = TranslationCallbackEpoch()
        val oldRequest = epoch.begin()

        // Watchdog hết 30 giây: vô hiệu hóa callback của request cũ.
        epoch.invalidate()
        val nextRequest = epoch.begin()

        assertFalse(epoch.isCurrent(oldRequest))
        assertTrue(epoch.isCurrent(nextRequest))
    }

    @Test fun switchingGameInvalidatesInFlightTranslation() {
        val epoch = TranslationCallbackEpoch()
        val request = epoch.begin()

        epoch.invalidate()

        assertFalse(epoch.isCurrent(request))
    }

    @Test fun onlyLatestRequestMayPublish() {
        val epoch = TranslationCallbackEpoch()
        val first = epoch.begin()
        val second = epoch.begin()

        assertFalse(epoch.isCurrent(first))
        assertTrue(epoch.isCurrent(second))
    }
}
