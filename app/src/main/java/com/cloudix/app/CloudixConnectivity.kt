package com.cloudix.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

/**
 * Synchronous, bounded diagnostic. Invoke from Dispatchers.IO.
 *
 * A saved QR and a public IPv6 address alone do not prove reachability:
 * verify reachability, TLS certificate pinning and authentication in ONE request.
 */
data class ConnectionDiagnostic(
    val reachable: Boolean,
    val heading: String,
    val explanation: String,
    val elapsedMs: Long = 0,
    val path: String = ""
)

object CloudixConnectivity {
    fun check(context: Context, peer: Peer): ConnectionDiagnostic {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val mobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true
        val client = CloudixClient(peer, context, connectTimeoutMs = 4_500, readTimeoutMs = 7_500)
        val address = client.selectedAddress()
        val ipv6 = address.contains(':')
        val tailscale = CloudixTailnet.isTailnetIpv4(address)
        val label = when {
            tailscale -> context.getString(R.string.net_vpn)
            ipv6 -> context.getString(R.string.net_ipv6)
            else -> context.getString(R.string.net_wifi)
        }
        if (mobile && !ipv6 && !tailscale && peer.host.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}"))) {
            return ConnectionDiagnostic(false, context.getString(R.string.net_no_mobile_route),
                context.getString(R.string.net_remote_missing),
                path = label)
        }
        val started = System.nanoTime()
        return try {
            client.ping()
            ConnectionDiagnostic(true, context.getString(R.string.net_reachable),
                context.getString(R.string.net_tls_ok),
                elapsedMs(started), label)
        } catch (e: Exception) {
            // CloudixClient wraps low-level networking exceptions to provide localized messages.
            // Unwrap them to distinguish a filtered network from authentication/TLS errors.
            var cause: Throwable = e
            repeat(6) {
                if (cause.cause != null) cause = cause.cause!!
            }
            val unreachable = cause is ConnectException || cause is SocketTimeoutException ||
                cause is NoRouteToHostException
            val invalidTls = cause is SSLHandshakeException
            val heading = when {
                unreachable -> context.getString(R.string.net_blocked)
                invalidTls -> context.getString(R.string.net_tls_fail)
                else -> context.getString(R.string.net_no_response)
            }
            val explanation = when {
                unreachable && tailscale ->
                    context.getString(R.string.net_vpn_off)
                unreachable && ipv6 ->
                    context.getString(R.string.net_port_unreachable, peer.port)
                unreachable ->
                    context.getString(R.string.net_inaccessible)
                invalidTls ->
                    context.getString(R.string.net_pair_again)
                else -> e.message ?: context.getString(R.string.net_unknown)
            }
            ConnectionDiagnostic(false, heading, explanation, elapsedMs(started), label)
        }
    }

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000
}
