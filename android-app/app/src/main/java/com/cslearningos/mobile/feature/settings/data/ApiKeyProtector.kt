package com.cslearningos.mobile.feature.settings.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyProtector {
    fun encrypt(plainText: String): String

    fun decrypt(envelope: String): String?
}

class AndroidKeystoreApiKeyProtector(
    private val secretKeyProvider: (() -> SecretKey)? = null
) : ApiKeyProtector {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val iv = cipher.iv
        require(iv.size == IV_LENGTH_BYTES)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = byteArrayOf(ENVELOPE_VERSION) + iv + cipherText
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(envelope: String): String? =
        runCatching {
            val payload = Base64.decode(envelope, Base64.NO_WRAP)
            require(payload.size >= ENVELOPE_VERSION_LENGTH + IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES)
            require(payload.first() == ENVELOPE_VERSION)

            val iv = payload.copyOfRange(ENVELOPE_VERSION_LENGTH, ENVELOPE_VERSION_LENGTH + IV_LENGTH_BYTES)
            val cipherText = payload.copyOfRange(ENVELOPE_VERSION_LENGTH + IV_LENGTH_BYTES, payload.size)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            cipher.doFinal(cipherText).toString(Charsets.UTF_8)
        }.getOrNull()

    private fun secretKey(): SecretKey = secretKeyProvider?.invoke() ?: loadOrCreateSecretKey()

    private fun loadOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: createKey()
    }

    private fun createKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cs-learning-os-api-key"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENVELOPE_VERSION: Byte = 1
        const val ENVELOPE_VERSION_LENGTH = 1
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
    }
}
