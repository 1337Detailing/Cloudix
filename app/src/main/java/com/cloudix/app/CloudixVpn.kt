package com.cloudix.app

import android.content.Context
import android.net.VpnService
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * WireGuard VPN embedded directly in Cloudix.
 *
 * This tunnel connects to a VPN server *owned by the user*, e.g. their Freebox.
 * No vendor relay, Cloudflare worker, account, or third-party file hosting.
 *
 * Android requires a one-time VpnService.prepare() consent from the user.
 */
object CloudixVpn {
    val connected = mutableStateOf(false)
    val details = mutableStateOf("")
    private val mutex = Any()
    private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "cloudix-home"
        override fun onStateChange(newState: Tunnel.State) {
            connected.value = newState == Tunnel.State.UP
        }
    }

    fun permissionIntent(context: Context) = VpnService.prepare(context)

    fun validate(configuration: String): Config {
        require(configuration.length in 50..32768) { "Fichier WireGuard invalide ou trop volumineux" }
        val config = Config.parse(ByteArrayInputStream(configuration.toByteArray(Charsets.UTF_8)))
        require(config.peers.isNotEmpty()) { "Le fichier ne contient aucun serveur WireGuard" }
        return config
    }

    fun activate(context: Context) {
        check(permissionIntent(context) == null) { "Autorisation VPN Android nécessaire" }
        val settings = CloudixSettings(context.applicationContext)
        val configuration = settings.wireguardConfig
        require(configuration.isNotBlank()) { "Importe d'abord un fichier .conf de ta box" }
        val parsed = validate(configuration)
        synchronized(mutex) {
            val engine = backend ?: GoBackend(context.applicationContext).also { backend = it }
            engine.setState(tunnel, Tunnel.State.UP, parsed)
            connected.value = true
            details.value = "VPN maison actif : le Vivo peut accéder au réseau de la box."
        }
    }

    fun deactivate(context: Context) {
        synchronized(mutex) {
            val engine = backend
            if (engine != null) engine.setState(tunnel, Tunnel.State.DOWN, null)
            connected.value = false
            details.value = "VPN arrêté"
        }
    }
}
