package com.example.security.crypto

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val FORMAT_PREFIX = "v1"
    private const val ITERATIONS = 100000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private val secureRandom = SecureRandom()

    fun hashPassword(password: String): String {
        require(password.isNotEmpty()) { "Password to hash cannot be empty" }
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return "$FORMAT_PREFIX:$ITERATIONS:${toHex(salt)}:${toHex(hash)}"
    }

    fun verifyPassword(candidate: String, storedHashRecord: String): Boolean {
        if (candidate.isEmpty() || storedHashRecord.isEmpty()) return false
        val parts = storedHashRecord.split(":")
        if (parts.size != 4) return false
        val version = parts[0]
        val iterations = parts[1].toIntOrNull() ?: return false
        if (version != FORMAT_PREFIX) return false
        val salt = fromHex(parts[2]) ?: return false
        val expectedHash = fromHex(parts[3]) ?: return false
        val computedHash = pbkdf2(candidate.toCharArray(), salt, iterations, expectedHash.size * 8)
        return MessageDigest.isEqual(computedHash, expectedHash)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec: KeySpec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } catch (e: NoSuchAlgorithmException) {
            fallbackDigest(password, salt, iterations)
        } catch (e: InvalidKeySpecException) {
            fallbackDigest(password, salt, iterations)
        }
    }

    private fun fallbackDigest(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        var result = md.digest(String(password).toByteArray(Charsets.UTF_8))
        val loopCount = iterations.coerceAtMost(10000)
        for (i in 1 until loopCount) {
            md.reset()
            result = md.digest(result)
        }
        return result
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            val byteVal = hex.substring(index, index + 2).toIntOrNull(16) ?: return null
            bytes[i] = byteVal.toByte()
        }
        return bytes
    }
}
