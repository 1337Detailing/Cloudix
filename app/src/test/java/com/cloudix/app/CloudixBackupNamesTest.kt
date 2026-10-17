package com.cloudix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudixBackupNamesTest {
    @Test fun keepsAccentsPunctuationAndEmoji() {
        assertEquals("Vacances 50% & été 📸.jpg",
            CloudixBackupNames.safeName("Vacances 50% & été 📸.jpg"))
    }

    @Test fun sanitizedNamesCannotAccidentallyCollide() {
        val x = CloudixBackupNames.safeName("a/b.jpg")
        val y = CloudixBackupNames.safeName("a\\b.jpg")
        assertNotEquals(x, y)
        assertTrue(x.length <= 120 && y.length <= 120)
        assertEquals(x, CloudixBackupNames.safeName("a/b.jpg"))
    }

    @Test fun longNamesRemainDeterministicAndDistinct() {
        val prefix = "x".repeat(135)
        val a = CloudixBackupNames.safeName(prefix + "1")
        val b = CloudixBackupNames.safeName(prefix + "2")
        assertNotEquals(a, b)
        assertTrue(a.length <= 120)
        assertTrue(b.length <= 120)
    }

    @Test fun rejectsReservedDirectoryNamesByEscaping() {
        val a = CloudixBackupNames.safeName("..")
        assertNotEquals("..", a)
        assertTrue(a.length <= 120)
    }
}
