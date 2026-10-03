package com.vedica.labs.ind.app.docora.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM via the Android Keystore.
 *
 * Used for the (optional) at-rest encryption of recognised text and for the metadata
 * backup payload. Key material never leaves the Keystore: only the wrapped ciphertext is
 * persisted, and the key is invalidated by the OS if the device is reset.
 */
class KeystoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val provider: () -> java.security.KeyStore? = { defaultKeyStore() },
) {

    /**
     * Encrypts [plainText] and returns `base64(iv) : base64(cipherText)`.
     * A fresh random IV is generated per call by the Keystore, as required for GCM.
     */
    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        encode(cipher.iv) + IV_SEPARATOR + encode(cipherText)
    }.onFailure { DocoraLog.e(TAG, "encrypt_failed", it) }.getOrNull()

    /** Reverses [encrypt]. Returns null when the payload is malformed or the key is gone. */
    fun decrypt(payload: String): String? = runCatching {
        val separator = payload.indexOf(IV_SEPARATOR)
        if (separator <= 0) return null
        val iv = decode(payload.substring(0, separator))
        val cipherText = decode(payload.substring(separator + 1))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.onFailure { DocoraLog.e(TAG, "decrypt_failed", it) }.getOrNull()

    /** Removes the key: previously encrypted payloads become permanently unreadable. */
    fun deleteKey() {
        runCatching {
            retainedKeyStore()?.deleteEntry(keyAlias)
        }.onFailure { DocoraLog.w(TAG, "delete_key_failed", it) }
    }

    fun hasKey(): Boolean = runCatching {
        retainedKeyStore()?.containsAlias(keyAlias) == true
    }.getOrDefault(false)

    private fun getOrCreateKey(): SecretKey = retainedKeyStore().let { keyStore ->
        val existing = keyStore?.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            existing.secretKey
        } else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    // Text must be readable by background workers without a user prompt,
                    // so the key is not bound to user authentication. App Lock protects the
                    // UI, and the key is still non-exportable hardware-backed material.
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generator.generateKey()
        }
    }

    private var cachedKeyStore: java.security.KeyStore? = null

    private fun retainedKeyStore(): java.security.KeyStore? {
        cachedKeyStore?.let { return it }
        val store = provider()
        cachedKeyStore = store
        return store
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val TAG = "KeystoreCipher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val IV_SEPARATOR = ":"
        const val DEFAULT_KEY_ALIAS = "docora.text.v1"

        private fun defaultKeyStore(): java.security.KeyStore? = runCatching {
            java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        }.getOrNull()
    }
}
