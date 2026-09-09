package com.example.calcalc.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Holds the user's Gemini API key on-device only — it is never written to Firestore, so it
 * does not follow the account to a new phone (by design; re-enter it there).
 *
 * The value is encrypted with a hardware-backed AES key from the Android Keystore, so a
 * DataStore file lifted off the device is useless on its own.
 */
class ApiKeyStore(private val context: Context) {

    private val keyPref = stringPreferencesKey("gemini_api_key_encrypted")

    val apiKeyFlow: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[keyPref]?.let { decryptOrNull(it) }
    }

    suspend fun currentKey(): String? = apiKeyFlow.first()

    suspend fun setApiKey(key: String) {
        val trimmed = key.trim()
        context.settingsDataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(keyPref) else prefs[keyPref] = encrypt(trimmed)
        }
    }

    suspend fun clear() {
        context.settingsDataStore.edit { it.remove(keyPref) }
    }

    // --- Keystore-backed AES-GCM ---

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // iv | ciphertext, both base64 so the whole thing survives as a preference string.
        return "${cipher.iv.base64()}:${cipherText.base64()}"
    }

    /**
     * Returns null rather than throwing: the Keystore key is wiped by a factory reset or a
     * lock-screen change, and a stale ciphertext should just look like "no key set".
     */
    private fun decryptOrNull(stored: String): String? = runCatching {
        val (ivPart, cipherPart) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, ivPart.decodeBase64()))
        String(cipher.doFinal(cipherPart.decodeBase64()), Charsets.UTF_8)
    }.getOrNull()

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "calcalc_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
