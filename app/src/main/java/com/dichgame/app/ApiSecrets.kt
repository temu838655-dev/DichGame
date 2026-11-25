package com.dichgame.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-bound AES-256-GCM storage for provider credentials.
 * Legacy SharedPreferences values are migrated only after encrypted storage commits.
 * Credential values are never included in logs.
 */
object ApiSecrets {
    private const val ALIAS = "dg_api_credentials_v1"
    private const val FILE = "dg_api_secrets"
    private val cache = mutableMapOf<String, Pair<String, String>>()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun encode(name: String, value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decode(name: String, value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun cleanupLegacy(context: Context, name: String) {
        val legacy = context.getSharedPreferences("dg", Context.MODE_PRIVATE)
        if (legacy.contains(name)) legacy.edit().remove(name).commit()
    }

    @Synchronized
    fun read(context: Context, name: String): String {
        val encryptedPrefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val encrypted = encryptedPrefs.getString(name, null)
        if (encrypted != null) {
            cache[name]?.takeIf { it.first == encrypted }?.let {
                // Retry cleanup even when the decrypted value is served from the in-memory cache.
                cleanupLegacy(context, name)
                return it.second
            }
            return try {
                val plain = decode(name, encrypted)
                cache[name] = encrypted to plain
                // Retry cleanup if a previous migration committed ciphertext but legacy cleanup failed.
                cleanupLegacy(context, name)
                plain
            } catch (_: Exception) {
                // A device restore or Keystore reset can make ciphertext unreadable. Preserve
                // an old plaintext value if one still exists rather than silently losing it.
                Log.w("DG_SECRETS", "Encrypted credential unavailable; checking legacy migration value.")
                val legacy = context.getSharedPreferences("dg", Context.MODE_PRIVATE)
                    .getString(name, "").orEmpty()
                if (legacy.isNotEmpty()) legacy else ""
            }
        }

        val legacyPrefs = context.getSharedPreferences("dg", Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString(name, "").orEmpty()
        if (legacy.isNotEmpty()) write(context, mapOf(name to legacy))
        return legacy
    }

    @Synchronized
    fun write(context: Context, values: Map<String, String>): Boolean {
        return try {
            val encryptedPrefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val encoded = values.mapValues { (name, value) -> encode(name, value) }
            val editor = encryptedPrefs.edit()
            encoded.forEach { (name, value) -> editor.putString(name, value) }
            if (!editor.commit()) return false

            // Remove plaintext only after every encrypted value has been committed.
            val legacyEditor = context.getSharedPreferences("dg", Context.MODE_PRIVATE).edit()
            encoded.keys.forEach { legacyEditor.remove(it) }
            if (!legacyEditor.commit()) {
                Log.w("DG_SECRETS", "Credential migration committed; legacy cleanup will be retried.")
            }
            encoded.forEach { (name, value) -> cache[name] = value to values.getValue(name) }
            true
        } catch (_: Exception) {
            Log.w("DG_SECRETS", "Could not save encrypted credentials.")
            false
        }
    }
}
