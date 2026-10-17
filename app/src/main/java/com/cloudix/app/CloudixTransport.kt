package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

object TlsIdentity {
    private const val ALIAS = "cloudix-server-v1"
    fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (!ks.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=Cloudix"))
                    .setCertificateSerialNumber(java.math.BigInteger.ONE)
                    .setCertificateNotBefore(java.util.Date(System.currentTimeMillis() - 86_400_000))
                    .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 3650L * 86_400_000))
                    .build()
            )
            generator.generateKeyPair()
        }
        return ks
    }
    fun fingerprint(context: Context): String {
        val cert = keyStore().getCertificate(ALIAS)
        return CloudixSettings.fingerprint(cert.encoded)
    }
    fun serverContext(): SSLContext {
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        km.init(keyStore(), null)
        return SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
    }
    fun clientContext(pin: String): SSLContext {
        require(pin.matches(Regex("[0-9a-f]{64}"))) { "Empreinte TLS invalide" }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                throw java.security.cert.CertificateException("Client certificate not supported")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || !MessageDigest.isEqual(
                        CloudixSettings.fingerprint(chain[0].encoded).toByteArray(),
                        pin.toByteArray()
                    )
                ) throw java.security.cert.CertificateException("Empreinte serveur incorrecte")
                chain[0].checkValidity()
            }
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
    }
}

data class Peer(val host: String, val port: Int, val secret: String, val pin: String) {
    companion object {
        fun parse(link: String): Peer {
            val uri = Uri.parse(link.trim())
            require(uri.scheme == "cloudix" && uri.host == "pair") { "Lien d'appairage invalide" }
            val host = uri.getQueryParameter("host") ?: ""
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
            val key = uri.getQueryParameter("secret") ?: ""
            val pin = uri.getQueryParameter("fingerprint") ?: ""
            require(host.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}")) &&
                host.split(".").all { it.toInt() in 0..255 } && port in 1..65535 &&
                key.matches(Regex("[A-Za-z0-9_-]{43}")) && pin.matches(Regex("[a-f0-9]{64}"))) {
                "Paramètres d'appairage invalides"
            }
            return Peer(host, port, key, pin)
        }
    }
}

data class RemoteFile(val name: String, val path: String, val directory: Boolean, val size: Long)
class CloudixClient(val peer: Peer) {
    private fun connection(route: String, method: String = "GET"): HttpsURLConnection {
        val url = URL("https://${peer.host}:${peer.port}$route")
        return (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = TlsIdentity.clientContext(peer.pin).socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true } // pin is verified by TrustManager
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${peer.secret}")
            setRequestProperty("User-Agent", "Cloudix/0.1")
            useCaches = false
        }
    }
    private fun request(route: String, method: String = "GET"): String {
        val conn = connection(route, method)
        try {
            if (conn.responseCode !in 200..299) throw Exception("Serveur: HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
    fun list(path: String): List<RemoteFile> {
        val items = JSONArray(request("/api/list?path=${Uri.encode(path)}"))
        return (0 until items.length()).map { i ->
            val obj = items.getJSONObject(i)
            RemoteFile(obj.getString("name"), obj.getString("path"),
                obj.getBoolean("directory"), obj.optLong("size", 0L))
        }
    }
    fun mkdir(path: String, name: String) {
        request("/api/mkdir?path=${Uri.encode(path)}&name=${Uri.encode(name)}", "POST")
    }
    fun delete(path: String) {
        request("/api/delete?path=${Uri.encode(path)}", "POST")
    }
    fun upload(path: String, name: String, length: Long, source: InputStream) {
        val conn = connection("/api/upload?path=${Uri.encode(path)}&name=${Uri.encode(name)}", "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setFixedLengthStreamingMode(length)
            conn.outputStream.use { out -> source.use { copyExactly(it, out, length) } }
            if (conn.responseCode !in 200..299) throw Exception("Upload refusé (HTTP ${conn.responseCode})")
        } finally { conn.disconnect() }
    }
    fun download(path: String, sink: OutputStream) {
        val conn = connection("/api/file?path=${Uri.encode(path)}")
        try {
            if (conn.responseCode != 200) throw Exception("Téléchargement impossible (HTTP ${conn.responseCode})")
            conn.inputStream.use { input -> sink.use { output -> input.copyTo(output, 65536) } }
        } finally { conn.disconnect() }
    }
}
fun copyExactly(source: InputStream, target: OutputStream, length: Long) {
    var remain = length
    val buffer = ByteArray(65536)
    while (remain > 0) {
        val n = source.read(buffer, 0, minOf(remain, buffer.size.toLong()).toInt())
        if (n < 0) throw java.io.EOFException("Fichier incomplet")
        target.write(buffer, 0, n)
        remain -= n
    }
}
