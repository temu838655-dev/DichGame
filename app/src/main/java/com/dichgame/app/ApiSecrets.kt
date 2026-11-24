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

/** Device-bound AES-GCM storage. Legacy values are removed only after an encrypted commit succeeds. */
object ApiSecrets {
    private const val ALIAS = "dg_api_credentials_v1"
    private const val FILE = "dg_api_secrets"
    private val cache = mutableMapOf<String, Pair<String, String>>()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
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
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    @Synchronized fun read(ctx: Context, name: String): String {
        val old = ctx.getSharedPreferences("dg", Context.MODE_PRIVATE)
        val store = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val encrypted = store.getString(name, null)
        if (encrypted != null) {
            cache[name]?.takeIf { it.first == encrypted }?.let { return it.second }
            return try {
                decode(name, encrypted).also { cache[name] = encrypted to it }
            } catch (_: Exception) {
                Log.w("DG_SECRETS", "Cannot read encrypted credential")
                ""
            }
        }
        val legacy = old.getString(name, "").orEmpty()
        if (legacy.isNotEmpty()) write(ctx, mapOf(name to legacy))
        return legacy
    }
    @Synchronized fun write(ctx: Context, values: Map<String, String>): Boolean {
        return try {
            val store = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val edit = store.edit()
            val encoded = values.mapValues { (name, value) -> encode(name, value) }
            for ((name, value) in encoded) edit.putString(name, value)
            if (!edit.commit()) return false
            val old = ctx.getSharedPreferences("dg", Context.MODE_PRIVATE).edit()
            for ((name, value) in encoded) {
                old.remove(name)
                cache[name] = value to values.getValue(name)
            }
            old.commit()
            true
        } catch (_: Exception) {
            Log.w("DG_SECRETS", "Cannot save encrypted credential")
            false
        }
    }
}
