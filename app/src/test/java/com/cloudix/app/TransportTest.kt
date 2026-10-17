package com.cloudix.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class TransportTest {
    @Test fun copiesExactLength() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
        val output = ByteArrayOutputStream()
        copyExactly(input, output, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test fun rejectsTruncatedUpload() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2))
        assertThrows(EOFException::class.java) {
            copyExactly(input, ByteArrayOutputStream(), 6)
        }
    }

    @Test fun supportsEmptyFiles() {
        val output = ByteArrayOutputStream()
        copyExactly(ByteArrayInputStream(byteArrayOf()), output, 0)
        assertArrayEquals(byteArrayOf(), output.toByteArray())
    }
}
