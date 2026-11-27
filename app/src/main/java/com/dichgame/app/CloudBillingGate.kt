package com.dichgame.app

import android.content.SharedPreferences

/**
 * Persistent account-level billing stop. Only a non-secret fingerprint is stored;
 * the API key itself remains in ApiSecrets.
 */
internal class CloudBillingGate(private val preferences: SharedPreferences) {
    fun isBlocked(fingerprint: String): Boolean {
        if (!preferences.getBoolean(KEY_BLOCKED, false)) return false
        if (preferences.getString(KEY_FINGERPRINT, null) == fingerprint) return true
        clear()
        return false
    }

    fun block(fingerprint: String): Boolean =
        preferences.edit()
            .putBoolean(KEY_BLOCKED, true)
            .putString(KEY_FINGERPRINT, fingerprint)
            .commit()

    fun clear(): Boolean =
        preferences.edit()
            .remove(KEY_BLOCKED)
            .remove(KEY_FINGERPRINT)
            .commit()

    private companion object {
        const val KEY_BLOCKED = "cloud_billing_blocked"
        const val KEY_FINGERPRINT = "cloud_billing_fingerprint"
    }
}
