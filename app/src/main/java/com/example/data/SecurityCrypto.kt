package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityCrypto {
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashSecret(secret: String, salt: String): String {
        val normalized = secret.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$normalized".toByteArray(Charsets.UTF_8)
        val hash = digest.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifySecret(candidate: String, salt: String, expectedHash: String): Boolean {
        if (salt.isEmpty() || expectedHash.isEmpty()) return false
        val computed = hashSecret(candidate, salt)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}
