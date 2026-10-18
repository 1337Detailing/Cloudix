package com.cloudix.app

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupNetworkPolicyTest {
    @Test fun wifiOnlyRequiresUnmeteredNetwork() {
        assertEquals(NetworkType.UNMETERED, BackupNetworkPolicy.requiredNetworkType(true))
    }

    @Test fun mobileModeDoesNotBlockUnvalidatedTailscale() {
        // VPN or mobile carrier can be available to Cloudix while Android
        // WorkManager thinks the default network is not validated.
        assertNull(BackupNetworkPolicy.requiredNetworkType(false))
    }
}
