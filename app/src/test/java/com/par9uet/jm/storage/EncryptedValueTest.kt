package com.par9uet.jm.storage

import org.junit.Assert.*
import org.junit.Test

class EncryptedValueTest {
    @Test fun encryptionFallbackNeverWritesCredentials() {
        var written: String? = null
        for (result in listOf("plain:ZmFrZS10b2tlbg==", "fake-token")) {
            assertThrows(IllegalArgumentException::class.java) {
                saveEncryptedValue("fake-token", { result }, { written = it })
            }
        }
        assertNull(written)
    }

    @Test fun successfulEncryptionPersistsCiphertext() {
        var written: String? = null
        saveEncryptedValue("fake-token", { "enc:ciphertext" }, { written = it })
        assertEquals("enc:ciphertext", written)
    }
}
