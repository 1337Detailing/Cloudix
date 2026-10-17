package com.cloudix.app

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Private Tailscale/CGNAT-style address range (100.64.0.0/10).
 * This is a network-address check only; the application cannot establish
 * whether the address really belongs to the user's Tailscale network.
 */
object CloudixTailnet {
    fun isTailnetIpv4(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val numbers = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return false
            part.toIntOrNull() ?: return false
        }
        return numbers.all { it in 0..255 } &&
            numbers[0] == 100 && numbers[1] in 64..127
    }

    fun detectOnDevice(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("tun")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull(::isTailnetIpv4)
    } catch (_: Exception) { null }
}
