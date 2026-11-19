package com.example

import com.example.data.SecurityCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun saltedPinHashingVerifiesCorrectly() {
        val salt = SecurityCrypto.generateSalt()
        val hash = SecurityCrypto.hashSecret("1357", salt)
        assertTrue(SecurityCrypto.verifySecret("1357", salt, hash))
        assertFalse(SecurityCrypto.verifySecret("1358", salt, hash))
    }
}
