package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreEncryptionManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "rr_master_key_aes256"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    private var inMemorySecretKey: SecretKey? = null

    private fun isAndroidRuntime(): Boolean {
        return try {
            Build.VERSION.SDK_INT > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        if (!isAndroidRuntime()) {
            return inMemorySecretKey ?: synchronized(this) {
                inMemorySecretKey ?: run {
                    val kg = KeyGenerator.getInstance("AES")
                    kg.init(256)
                    val key = kg.generateKey()
                    inMemorySecretKey = key
                    key
                }
            }
        }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return if (isAndroidRuntime()) {
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } else {
            java.util.Base64.getEncoder().encodeToString(combined)
        }
    }

    fun decrypt(encryptedTextBase64: String): String {
        if (encryptedTextBase64.isEmpty()) return ""
        val combined = if (isAndroidRuntime()) {
            Base64.decode(encryptedTextBase64, Base64.NO_WRAP)
        } else {
            java.util.Base64.getDecoder().decode(encryptedTextBase64)
        }
        if (combined.size <= GCM_IV_LENGTH) throw IllegalArgumentException("Ciphertext too short")

        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }
}
