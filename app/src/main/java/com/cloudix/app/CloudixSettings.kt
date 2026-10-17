package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class CloudixSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloudix", Context.MODE_PRIVATE)
    var role: String
        get() = prefs.getString("role", "") ?: ""
        set(v) = prefs.edit().putString("role", v).apply()
    var rootUri: String
        get() = prefs.getString("root_uri", "") ?: ""
        set(v) = prefs.edit().putString("root_uri", v).apply()
    var peer: String
        get() = secureRead("peer")
        set(v) = secureWrite("peer", v)
    /**
     * Single Compose-observable state. Previously, updating SharedPreferences did not
     * invalidate CloudixTheme; the UI only refreshed after restarting the activity.
     */
    var appearance by mutableStateOf(loadAppearance())
        private set

    private fun loadAppearance(): AppearanceSnapshot {
        val json = runCatching {
            JSONObject(prefs.getString("appearance_overrides", "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val colors = mutableMapOf<String, String>()
        for (role in CloudixColorRole.entries) {
            val color = json.optString(role.key)
            CloudixHex.normalize(color)?.let { colors[role.key] = it }
        }
        return AppearanceSnapshot(
            theme = prefs.getString("theme", "system")?.takeIf {
                it in listOf("system", "light", "dark")
            } ?: "system",
            dynamic = prefs.getBoolean("dynamic", true),
            accent = prefs.getInt("accent", 0).coerceIn(0, CloudixAppearanceCatalog.presets.lastIndex),
            surfaceStyle = prefs.getString("surface_style", "soft")?.takeIf {
                it in CloudixAppearanceCatalog.styles.map { style -> style.first }
            } ?: "soft",
            animations = prefs.getBoolean("animations", true),
            overrides = colors
        )
    }

    private fun updateAppearance(next: AppearanceSnapshot) {
        if (next == appearance) return
        appearance = next
        val json = JSONObject()
        next.overrides.forEach { (role, color) ->
            if (CloudixColorRole.fromKey(role) != null && CloudixHex.normalize(color) != null)
                json.put(role, color)
        }
        prefs.edit().putString("theme", next.theme)
            .putBoolean("dynamic", next.dynamic)
            .putInt("accent", next.accent)
            .putString("surface_style", next.surfaceStyle)
            .putBoolean("animations", next.animations)
            .putString("appearance_overrides", json.toString()).apply()
    }

    var theme: String
        get() = appearance.theme
        set(v) {
            require(v in listOf("system", "light", "dark"))
            val nextStyle = if (v != "dark" && appearance.surfaceStyle.startsWith("amoled"))
                "soft" else appearance.surfaceStyle
            updateAppearance(appearance.copy(theme = v, surfaceStyle = nextStyle))
        }
    var dynamic: Boolean
        get() = appearance.dynamic
        set(v) = updateAppearance(appearance.copy(dynamic = v))
    var surfaceStyle: String
        get() = appearance.surfaceStyle
        set(v) {
            require(CloudixAppearanceCatalog.styles.any { it.first == v })
            updateAppearance(appearance.copy(
                surfaceStyle = v,
                theme = if (v.startsWith("amoled")) "dark" else appearance.theme
            ))
        }
    /** Compatibility with previous numeric intensity preference. */
    var surfaceIntensity: Int
        get() = if (surfaceStyle == "strong") 4 else 1
        set(v) { surfaceStyle = if (v >= 3) "strong" else "soft" }
    var animations: Boolean
        get() = appearance.animations
        set(v) = updateAppearance(appearance.copy(animations = v))
    var sort: String
        get() = prefs.getString("sort", "name") ?: "name"
        set(value) = prefs.edit().putString("sort", value).apply()
    var accent: Int
        get() = appearance.accent
        set(v) {
            require(v in CloudixAppearanceCatalog.presets.indices)
            updateAppearance(appearance.copy(accent = v))
        }

    fun setColorOverride(role: CloudixColorRole, value: String?) {
        val normalized = value?.let { CloudixHex.normalize(it) }
        require(value == null || normalized != null) { "Code couleur invalide : utilise #RRGGBB" }
        val overrides = appearance.overrides.toMutableMap()
        if (normalized == null) overrides.remove(role.key) else overrides[role.key] = normalized
        updateAppearance(appearance.copy(overrides = overrides))
    }
    fun resetColors() = updateAppearance(appearance.copy(overrides = emptyMap()))
    fun resetAppearance() = updateAppearance(AppearanceSnapshot())
    var serverAutoStart: Boolean
        get() = prefs.getBoolean("server_auto_start", false)
        set(value) = prefs.edit().putBoolean("server_auto_start", value).apply()
    var tailscaleAddress: String
        get() = prefs.getString("tailscale_address", "") ?: ""
        set(value) {
            require(value.isBlank() || CloudixTailnet.isTailnetIpv4(value)) {
                "Adresse Tailscale invalide (100.64.0.0/10)"
            }
            prefs.edit().putString("tailscale_address", value).apply()
        }
    var preferDirectIpv6: Boolean
        get() = prefs.getBoolean("prefer_direct_ipv6", false)
        set(value) = prefs.edit().putBoolean("prefer_direct_ipv6", value).apply()
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(v) = prefs.edit().putBoolean("wifi_only", v).apply()
    var lastBackupError: String
        get() = prefs.getString("backup_last_error", "") ?: ""
        set(value) = prefs.edit().putString("backup_last_error", value.take(300)).apply()
    var lastBackupAt: Long
        get() = prefs.getLong("last_backup_at", 0)
        set(v) = prefs.edit().putLong("last_backup_at", v).apply()
    var lastBackupItems: Int
        get() = prefs.getInt("last_backup_items", 0)
        set(v) = prefs.edit().putInt("last_backup_items", v).apply()
    var interval: Long
        get() = prefs.getLong("interval", 60)
        set(v) = prefs.edit().putLong("interval", v).apply()
    var backupFolders: Set<String>
        get() = prefs.getStringSet("backup_folders", emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet("backup_folders", v.toSet()).apply()
    fun clearBackupState() {
        prefs.edit().putString("backup_state", "").apply()
    }
    fun backupState(): org.json.JSONObject = try {
        org.json.JSONObject(prefs.getString("backup_state", "{}") ?: "{}")
    } catch (_: Exception) { org.json.JSONObject() }
    fun saveBackupState(state: org.json.JSONObject) {
        prefs.edit().putString("backup_state", state.toString()).apply()
    }
    private fun cryptoKey(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "cloudix-credentials-v2"
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
    private fun secureWrite(name: String, value: String) {
        if (value.isEmpty()) { prefs.edit().remove(name).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cryptoKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, "v2:" + Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }
    private fun secureRead(name: String): String {
        val stored = prefs.getString(name, "") ?: ""
        if (stored.isEmpty()) return ""
        if (!stored.startsWith("v2:")) {
            secureWrite(name, stored) // migrate legacy credential without changing it
            return stored
        }
        return try {
            val data = Base64.decode(stored.removePrefix("v2:"), Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, cryptoKey(),
                GCMParameterSpec(128, data.copyOfRange(0, 12)))
            cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("Identifiants Cloudix illisibles : réappaire cet appareil", e)
        }
    }
    fun secret(): String {
        val existing = secureRead("server_secret").ifEmpty { null }
        if (existing != null) return existing
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        secureWrite("server_secret", token)
        return token
    }
    fun regenerateSecret() {
        secureWrite("server_secret", "")
        secret()
    }
    fun serverLink(): String {
        val vpn = tailscaleAddress.ifBlank { CloudixTailnet.detectOnDevice().orEmpty() }
            .takeIf(CloudixTailnet::isTailnetIpv4)
        val address = localAddress() ?: vpn ?: globalIpv6Address() ?: return ""
        val alternative = globalIpv6Address()?.let { "&ipv6=${Uri.encode(it)}" } ?: ""
        val tailscale = vpn?.let { "&tailscale=${Uri.encode(it)}" } ?: ""
        return "cloudix://pair?host=${Uri.encode(address)}&port=8731&secret=${Uri.encode(secret())}&fingerprint=${TlsIdentity.fingerprint(context)}${alternative}${tailscale}"
    }
    companion object {
        fun localAddress(): String? {
            val all = NetworkInterface.getNetworkInterfaces() ?: return null
            val interfaces = all.toList()
            return interfaces.filter { it.isUp && !it.isLoopback &&
                    (it.name.startsWith("wlan") || it.name.startsWith("ap")) }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }
        /**
         * This checks for an IPv6 global unicast address, NOT remote reachability.
         * Router firewalls may still drop inbound traffic even if the address exists.
         */
        fun globalIpv6Address(): String? = try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            interfaces.filter { it.isUp && !it.isLoopback &&
                    (it.name.startsWith("wlan") || it.name.startsWith("ap")) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<java.net.Inet6Address>()
                .firstOrNull { a -> isGlobalIpv6(a) }?.hostAddress?.substringBefore('%')
        } catch (_: Exception) { null }

        fun isGlobalIpv6(a: java.net.Inet6Address): Boolean {
            val first = a.address[0].toInt() and 0xff
            return first in 0x20..0x3f && !a.isMulticastAddress &&
                !a.isLinkLocalAddress && !a.isLoopbackAddress
        }
        fun fingerprint(cert: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
    }
}
