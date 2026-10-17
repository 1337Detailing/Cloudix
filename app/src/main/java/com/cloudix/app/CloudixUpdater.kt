package com.cloudix.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class CloudixUpdateInfo(
    val versionCode: Long,
    val sha256: String,
    val url: String,
    val title: String
) {
    fun validate() {
        require(versionCode in 1..Int.MAX_VALUE.toLong()) { "Version invalide" }
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "Empreinte SHA-256 absente ou invalide" }
        require(url == "https://github.com/1337Detailing/Cloudix/releases/download/debug-latest/cloudix-debug.apk") {
            "Adresse de téléchargement non autorisée"
        }
        require(title.length <= 160) { "Notes de version trop longues" }
    }
}

object CloudixUpdater {
    const val CHECK_URL = "https://github.com/1337Detailing/Cloudix/releases/download/debug-latest/update.json"
    private const val MAX_APK_BYTES = 150L * 1024 * 1024
    fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun open(url: String): HttpsURLConnection {
        require(url.startsWith("https://")) { "HTTPS requis" }
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Cloudix-Android-Updater")
            setRequestProperty("Accept", "application/octet-stream,application/json")
        }
    }

    fun check(): CloudixUpdateInfo {
        val conn = open(CHECK_URL)
        try {
            if (conn.responseCode != 200)
                throw IOException(
                    if (conn.responseCode == 404)
                        "La première APK de mise à jour n'est pas encore publiée sur GitHub (404). " +
                        "Réessaie après la prochaine compilation Cloudix réussie."
                    else "Vérification GitHub impossible (HTTP ${conn.responseCode})."
                )
            val length = conn.contentLengthLong
            require(length in -1..16_384) { "Métadonnées de mise à jour trop volumineuses" }
            val data = conn.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(2048)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                    require(buffer.size() <= 16_384) { "Réponse GitHub trop volumineuse" }
                }
                JSONObject(buffer.toString("UTF-8"))
            }
            return CloudixUpdateInfo(data.getLong("versionCode"),
                data.getString("sha256"), data.getString("url"), data.optString("title", "Build debug")).also {
                it.validate()
            }
        } finally { conn.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: android.content.pm.PackageInfo): List<ByteArray> {
        return if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptyList()
            signing.apkContentsSigners.map { it.toByteArray() }
        } else info.signatures.orEmpty().map { it.toByteArray() }
    }

    private fun verifyApk(context: Context, file: File, expected: CloudixUpdateInfo) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw SecurityException("APK non reconnue par Android")
        val current = pm.getPackageInfo(context.packageName, flags)
        require(apk.packageName == context.packageName) { "Nom de package différent de Cloudix" }
        val code = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        require(code == expected.versionCode && code > currentVersionCode(context)) {
            "Cette APK n'est pas une version plus récente."
        }
        val originalCerts = signers(current).map { CloudixSettings.fingerprint(it) }.toSet()
        val updatedCerts = signers(apk).map { CloudixSettings.fingerprint(it) }.toSet()
        if (originalCerts.isEmpty() || originalCerts != updatedCerts) {
            throw SecurityException("Signature Android différente. Impossible de mettre à jour cette APK sans réinstallation : utilise une clé de signature stable.")
        }
    }

    fun download(context: Context, info: CloudixUpdateInfo, onProgress: (Int) -> Unit): File {
        info.validate()
        require(info.versionCode > currentVersionCode(context)) { "Cloudix est déjà à jour." }
        val outputDir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val target = File(outputDir, "cloudix-update.apk")
        target.delete()
        val conn = open(info.url)
        try {
            if (conn.responseCode != 200) throw IOException("APK signée indisponible sur GitHub Releases (HTTP ${conn.responseCode}).")
            val total = conn.contentLengthLong
            require(total in -1..MAX_APK_BYTES) { "APK trop volumineuse" }
            val digest = MessageDigest.getInstance("SHA-256")
            var readTotal = 0L
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        val n = input.read(bytes)
                        if (n < 0) break
                        readTotal += n
                        if (readTotal > MAX_APK_BYTES) throw IOException("APK dépasse la limite de taille")
                        digest.update(bytes, 0, n)
                        output.write(bytes, 0, n)
                        if (total > 0) onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            if (total >= 0 && readTotal != total) throw IOException("Téléchargement incomplet")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!MessageDigest.isEqual(actual.toByteArray(), info.sha256.toByteArray()))
                throw SecurityException("SHA-256 incorrecte : fichier rejeté")
            verifyApk(context, target, info)
            onProgress(100)
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally { conn.disconnect() }
    }

    /**
     * Android alone controls APK installation. Never silently installs.
     * User must grant permission to install packages from Cloudix.
     */
    fun install(context: Context, apk: File): Boolean {
        require(apk.isFile && apk.parentFile?.name == "updates") { "Fichier APK inconnu" }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context,
            "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
