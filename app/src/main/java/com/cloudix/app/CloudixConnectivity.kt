package com.cloudix.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLHandshakeException
import kotlin.system.measureTimeMillis

/**
 * Synchronous, bounded diagnostic. Invoke from Dispatchers.IO.
 *
 * A saved QR and a public IPv6 address alone do not prove reachability:
 * first test the TCP port, then perform a fully authenticated TLS ping.
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
        val label = if (ipv6) "IPv6 directe" else "Wi-Fi local (IPv4)"
        if (mobile && !ipv6 && peer.host.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}"))) {
            return ConnectionDiagnostic(false, "Pas de route 5G",
                "Le QR ne contient pas d'IPv6 publique. Le réseau privé du Xiaomi est inaccessible en 5G sans relais ou ouverture du routeur.",
                path = label)
        }
        val started = System.nanoTime()
        try {
            Socket().use { socket ->
                socket.soTimeout = 4_500
                socket.connect(InetSocketAddress(address, peer.port), 4_500)
            }
        } catch (e: Exception) {
            return ConnectionDiagnostic(false, "Connexion réseau bloquée",
                if (ipv6) {
                    "L'adresse IPv6 existe, mais le port ${peer.port} ne répond pas. Le pare-feu de la box ou un filtrage réseau est probable. Aucun réglage de Cloudix ne peut ouvrir ce port sans accès à la box."
                } else {
                    "Impossible de joindre le Xiaomi en Wi-Fi. Vérifie que le serveur est démarré et que les deux téléphones sont sur le même réseau."
                }, elapsedMs(started), label)
        }
        return try {
            client.ping()
            ConnectionDiagnostic(true, "Xiaomi joignable",
                "Connexion chiffrée TLS et authentification Cloudix vérifiées.",
                elapsedMs(started), label)
        } catch (e: Exception) {
            ConnectionDiagnostic(false, "Serveur atteint, réponse refusée",
                when (e) {
                    is SSLHandshakeException -> "Le port répond, mais la connexion TLS échoue. Actualise le QR et réappaire le Vivo."
                    else -> "Le port répond, mais le test TLS/authentification n'a pas réussi : ${e.message?.take(190) ?: "aucune réponse"}."
                }, elapsedMs(started), label)
        }
    }

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000
}
