package com.par9uet.jm.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun personalRevisionIsAnUpdate() {
        assertTrue(compareReleaseVersions("1.2.6-personal.3", "1.2.6-personal.2") > 0)
        assertTrue(compareReleaseVersions("1.2.6-personal.10", "1.2.6-personal.9") > 0)
    }

    @Test
    fun mainVersionTakesPrecedence() {
        assertTrue(compareReleaseVersions("1.3.0-personal.1", "1.2.6-personal.99") > 0)
        assertTrue(compareReleaseVersions("1.2.6-personal.2", "1.2.6-personal.3") < 0)
    }

    @Test
    fun releaseTagKeepsPersonalRevision() {
        assertEquals("1.2.6-personal.3", normalizeReleaseVersion(" v1.2.6-personal.3 "))
        assertEquals(0, compareReleaseVersions("v1.2.6-personal.3", "1.2.6-personal.3"))
    }
}
