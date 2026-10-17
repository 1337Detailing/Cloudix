package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.util.Base64
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
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(v) = prefs.edit().putString("theme", v).apply()
    var dynamic: Boolean
        get() = prefs.getBoolean("dynamic", true)
        set(v) = prefs.edit().putBoolean("dynamic", v).apply()
    var animations: Boolean
        get() = prefs.getBoolean("animations", true)
        set(value) = prefs.edit().putBoolean("animations", value).apply()
    var sort: String
        get() = prefs.getString("sort", "name") ?: "name"
        set(value) = prefs.edit().putString("sort", value).apply()
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
        val address = localAddress() ?: return ""
        return "cloudix://pair?host=${Uri.encode(address)}&port=8731&secret=${Uri.encode(secret())}&fingerprint=${TlsIdentity.fingerprint(context)}"
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
        fun fingerprint(cert: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
    }
}
