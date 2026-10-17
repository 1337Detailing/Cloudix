package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.SSLSocket
import java.net.Socket
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

object TlsIdentity {
    private const val ALIAS = "cloudix-server-ec-v2"
    fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (!ks.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512)
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
        // P-256 ECDSA avoids Android Conscrypt RSA-PSS raw-key incompatibilities.
        // Some AndroidKeyStore implementations don't enumerate aliases via the
        // platform's default KeyManagerFactory. Supply the Keystore identity explicitly.
        val ks = keyStore()
        val key = ks.getKey(ALIAS, null) as java.security.PrivateKey
        val chain = ks.getCertificateChain(ALIAS).map { it as X509Certificate }.toTypedArray()
        require(chain.isNotEmpty()) { "Certificat TLS introuvable" }
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<java.security.Principal>?): Array<String> = emptyArray()
            override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<java.security.Principal>?, socket: Socket?): String? = null
            override fun getServerAliases(keyType: String?, issuers: Array<java.security.Principal>?): Array<String> =
                if (keyType.equals("EC", true) || keyType.equals("ECDSA", true)) arrayOf(ALIAS) else emptyArray()
            override fun chooseServerAlias(keyType: String?, issuers: Array<java.security.Principal>?, socket: Socket?): String? =
                if (keyType.equals("EC", true) || keyType.equals("ECDSA", true)) ALIAS else null
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<java.security.Principal>?, engine: SSLEngine?): String? =
                if (keyType.equals("RSA", true)) ALIAS else null
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
                if (alias == ALIAS) chain else null
            override fun getPrivateKey(alias: String?): java.security.PrivateKey? =
                if (alias == ALIAS) key else null
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf<KeyManager>(manager), null, null) }
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

data class RemoteFile(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long = 0L,
    val mime: String = ""
)
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
            if (conn.responseCode !in 200..299) throw CloudixNetworkFailure.http(conn.responseCode, conn.errorStream)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) { throw CloudixNetworkFailure.describe(e) }
        finally { conn.disconnect() }
    }
    fun ping(): Long {
        val started = System.nanoTime()
        request("/api/ping")
        return (System.nanoTime() - started) / 1_000_000
    }
    fun list(path: String): List<RemoteFile> {
        val items = JSONArray(request("/api/list?path=${Uri.encode(path)}"))
        return (0 until items.length()).map { i ->
            val obj = items.getJSONObject(i)
            RemoteFile(obj.getString("name"), obj.getString("path"),
                obj.getBoolean("directory"), obj.optLong("size", 0L),
                obj.optLong("modified", 0L), obj.optString("mime", ""))
        }
    }
    fun mkdir(path: String, name: String) {
        request("/api/mkdir?path=${Uri.encode(path)}&name=${Uri.encode(name)}", "POST")
    }
    fun rename(path: String, name: String) {
        request("/api/rename?path=${Uri.encode(path)}&name=${Uri.encode(name)}", "POST")
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
            if (conn.responseCode !in 200..299) throw CloudixNetworkFailure.http(conn.responseCode, conn.errorStream)
        } catch (e: Exception) { throw CloudixNetworkFailure.describe(e) }
        finally { conn.disconnect() }
    }
    fun download(path: String, sink: OutputStream) {
        val conn = connection("/api/file?path=${Uri.encode(path)}")
        try {
            if (conn.responseCode != 200) throw CloudixNetworkFailure.http(conn.responseCode, conn.errorStream)
            conn.inputStream.use { input -> sink.use { output -> input.copyTo(output, 65536) } }
        } catch (e: Exception) { throw CloudixNetworkFailure.describe(e) }
        finally { conn.disconnect() }
    }
}
object CloudixNetworkFailure {
    fun describe(error: Exception): Exception {
        if (error is CloudixConnectionException) return error
        val message = when (error) {
            is java.net.ConnectException -> "Serveur inaccessible. Vérifie que Cloudix est démarré sur le Xiaomi et que les appareils partagent le même Wi-Fi."
            is java.net.SocketTimeoutException -> "Délai dépassé. Vérifie l’adresse Wi-Fi, le pare-feu et l’isolation des appareils sur le routeur."
            is javax.net.ssl.SSLHandshakeException -> "Échec de la connexion sécurisée TLS : ${error.message ?: "certificat ou protocole"}. Essaie le test local sur le Xiaomi et réappaire les téléphones."
            is java.io.EOFException -> "Connexion fermée avant la réponse. Lance le diagnostic TLS sur le Xiaomi."
            is java.net.SocketException -> "Connexion fermée par le serveur. Lance le diagnostic local sur le Xiaomi (erreur : ${error.message ?: "socket"})."
            else -> error.message ?: "Erreur de connexion inconnue"
        }
        return CloudixConnectionException(message, error)
    }
    fun http(code: Int, stream: InputStream?): Exception {
        val response = try { stream?.bufferedReader()?.use { it.readText().take(300) } } catch (_: Exception) { null }
        return CloudixConnectionException("Serveur HTTP $code : ${response ?: "requête refusée"}")
    }
}
class CloudixConnectionException(message: String, cause: Throwable? = null): IOException(message, cause)
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
