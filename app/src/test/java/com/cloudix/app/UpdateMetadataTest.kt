package com.cloudix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateMetadataTest {
    @Test fun validVersionAndDigest() {
        val candidate = CloudixUpdateInfo(1023, "a".repeat(64), CloudixUpdater.APK_URL, "test")
        assertEquals(1023L, candidate.versionCode)
        if (BuildConfig.UPDATE_REPOSITORY.isNotBlank()) candidate.validate()
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

    @Test fun rejectsNonMonotonicOrImpossibleVersionCodes() {
        val official = CloudixUpdater.APK_URL
        for (code in listOf(0L, -1L, Int.MAX_VALUE.toLong() + 1L)) {
            assertThrows(IllegalArgumentException::class.java) {
                CloudixUpdateInfo(code, "a".repeat(64), official, "beta").validate()
            }
        }
    }

    @Test fun preventsTamperedPathsAndUnexpectedCertificateManifests() {
        val official = CloudixUpdater.APK_URL
        for (url in listOf(
            official + "?redirect=evil",
            official.replace("https://", "http://"),
            official.replace("cloudix-debug.apk", "cloudix-other.apk")
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                CloudixUpdateInfo(1080, "b".repeat(64), url, "beta").validate()
            }
        }
    }

    @Test fun rejectsOversizedTitlesAndUppercaseDigest() {
        val official = CloudixUpdater.APK_URL
        assertThrows(IllegalArgumentException::class.java) {
            CloudixUpdateInfo(1080, "A".repeat(64), official, "beta").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudixUpdateInfo(1080, "a".repeat(64), official, "x".repeat(161)).validate()
        }
    }
}
