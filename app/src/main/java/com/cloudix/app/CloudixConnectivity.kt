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
        val label = if (ipv6) "IPv6 directe" else "Wi-Fi local (IPv4)"
        if (mobile && !ipv6 && peer.host.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}"))) {
            return ConnectionDiagnostic(false, "Pas de route 5G",
                "Le QR ne contient pas d'IPv6 publique. Le réseau privé du Xiaomi est inaccessible en 5G sans relais ou ouverture du routeur.",
                path = label)
        }
        val started = System.nanoTime()
        return try {
            client.ping()
            ConnectionDiagnostic(true, "Xiaomi joignable",
                "Connexion chiffrée TLS et authentification Cloudix vérifiées.",
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
                unreachable -> "Connexion réseau bloquée"
                invalidTls -> "Erreur de connexion sécurisée"
                else -> "Le Xiaomi n'a pas répondu"
            }
            val explanation = when {
                unreachable && ipv6 ->
                    "Le Xiaomi a une IPv6 publique, mais le port ${peer.port} ne répond pas depuis ce réseau. Le pare-feu de la box ou un filtrage de l'opérateur peut bloquer la connexion entrante. Sans accès au routeur ni relais, Cloudix ne peut pas contourner ce blocage."
                unreachable ->
                    "Le Xiaomi est inaccessible sur le Wi-Fi. Vérifie que son serveur Cloudix est démarré et que les appareils partagent le même réseau."
                invalidTls ->
                    "Le certificat TLS ne correspond pas ou la négociation a échoué. Actualise le QR sur le Xiaomi et réappaire le Vivo."
                else -> e.message ?: "Erreur réseau inconnue. Vérifie le serveur et l'appairage."
            }
            ConnectionDiagnostic(false, heading, explanation, elapsedMs(started), label)
        }
    }

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000
}
