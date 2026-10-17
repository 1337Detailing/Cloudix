package com.cloudix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateMetadataTest {
    @Test fun validVersionAndDigest() {
        val candidate = CloudixUpdateInfo(1023, "a".repeat(64), "https://github.com/1337Detailing/Cloudix/releases/download/debug-latest/cloudix-debug.apk", "test")
        assertEquals(1023L, candidate.versionCode)
        candidate.validate()
    }

    @Test fun rejectsWeakDigest() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudixUpdateInfo(1023, "oops", "https://github.com/test", "test").validate()
        }
    }

    @Test fun rejectsUntrustedDownloadHosts() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudixUpdateInfo(1023, "a".repeat(64), "https://evil.invalid/malicious.apk", "test").validate()
        }
    }
}
