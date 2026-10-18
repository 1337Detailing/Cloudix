package com.cloudix.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailnetAddressTest {
    @Test fun acceptsIpv4WithinSharedTailnetRange() {
        assertTrue(CloudixTailnet.isTailnetIpv4("100.64.0.1"))
        assertTrue(CloudixTailnet.isTailnetIpv4("100.64.1.10"))
        assertTrue(CloudixTailnet.isTailnetIpv4("100.127.255.254"))
    }

    @Test fun rejectsLanAndPublicIpv4AndOutOfRange() {
        assertFalse(CloudixTailnet.isTailnetIpv4("192.0.2.10"))
        assertFalse(CloudixTailnet.isTailnetIpv4("100.63.255.255"))
        assertFalse(CloudixTailnet.isTailnetIpv4("100.128.0.1"))
        assertFalse(CloudixTailnet.isTailnetIpv4("100.300.1.1"))
        assertFalse(CloudixTailnet.isTailnetIpv4("100.64.1"))
        assertFalse(CloudixTailnet.isTailnetIpv4("100.64.1.2.3"))
        assertFalse(CloudixTailnet.isTailnetIpv4("evil.example.com"))
        assertFalse(CloudixTailnet.isTailnetIpv4(""))
    }
}
