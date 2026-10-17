package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom

class CloudixSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloudix", Context.MODE_PRIVATE)
    var role: String
        get() = prefs.getString("role", "") ?: ""
        set(v) = prefs.edit().putString("role", v).apply()
    var rootUri: String
        get() = prefs.getString("root_uri", "") ?: ""
        set(v) = prefs.edit().putString("root_uri", v).apply()
    var peer: String
        get() = prefs.getString("peer", "") ?: ""
        set(v) = prefs.edit().putString("peer", v).apply()
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(v) = prefs.edit().putString("theme", v).apply()
    var dynamic: Boolean
        get() = prefs.getBoolean("dynamic", true)
        set(v) = prefs.edit().putBoolean("dynamic", v).apply()
    var accent: Int
        get() = prefs.getInt("accent", 0)
        set(v) = prefs.edit().putInt("accent", v).apply()
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(v) = prefs.edit().putBoolean("wifi_only", v).apply()
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
    fun secret(): String {
        val existing = prefs.getString("server_secret", null)
        if (existing != null) return existing
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs.edit().putString("server_secret", token).apply()
        return token
    }
    fun regenerateSecret() {
        prefs.edit().remove("server_secret").apply()
        secret()
    }
    fun serverLink(): String {
        val address = localAddress() ?: return ""
        return "cloudix://pair?host=${Uri.encode(address)}&port=8731&secret=${Uri.encode(secret())}&fingerprint=${TlsIdentity.fingerprint(context)}"
    }
    companion object {
        fun localAddress(): String? {
            val all = NetworkInterface.getNetworkInterfaces() ?: return null
            val interfaces = all.toList()
            return interfaces.sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }
        fun fingerprint(cert: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
    }
}
