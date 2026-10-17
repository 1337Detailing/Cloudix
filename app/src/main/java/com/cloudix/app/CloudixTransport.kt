package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.Inet6Address
import java.net.InetAddress
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Android's TLS/Conscrypt engine does not reliably handshake with opaque
 * AndroidKeyStore RSA/EC server keys on all devices.
 *
 * Solution: standard EC P-256 identity in memory; it is persisted only as
 * AES-GCM ciphertext whose wrapping key stays in AndroidKeyStore.
 * Certificate pinning and bearer authentication remain mandatory.
 */
object TlsIdentity {
    private const val PREFS = "cloudix_tls_identity"
    private const val ALIAS = "cloudix-tls-wrap-v3"
    private val lock = Any()
    private data class Identity(val privateKey: PrivateKey, val cert: X509Certificate)
    @Volatile private var cache: Identity? = null

    private fun wrapKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val result = cipher.iv + cipher.doFinal(data)
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): ByteArray {
        val raw = Base64.decode(value, Base64.DEFAULT)
        require(raw.size > 28) { "Identité TLS corrompue" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(),
            GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return cipher.doFinal(raw.copyOfRange(12, raw.size))
    }

    private fun generate(): Identity {
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        }.generateKeyPair()
        val now = System.currentTimeMillis()
        val distinguishedName = X500Name("CN=Cloudix Local")
        val certificateBuilder = JcaX509v3CertificateBuilder(
            distinguishedName,
            BigInteger(128, SecureRandom()).abs().add(BigInteger.ONE),
            Date(now - 86_400_000L),
            Date(now + 10L * 365 * 86_400_000L),
            distinguishedName,
            pair.public
        )
        val signed = certificateBuilder.build(
            JcaContentSignerBuilder("SHA256withECDSA").build(pair.private))
        val cert = JcaX509CertificateConverter().getCertificate(signed)
        cert.verify(pair.public)
        cert.checkValidity()
        return Identity(pair.private, cert)
    }

    private fun identity(context: Context): Identity {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString("wrapped_identity", null)
            val id = if (saved != null) {
                val data = JSONObject(decrypt(saved).toString(Charsets.UTF_8))
                val pkcs8 = Base64.decode(data.getString("private"), Base64.NO_WRAP)
                val der = Base64.decode(data.getString("certificate"), Base64.NO_WRAP)
                val privateKey = KeyFactory.getInstance("EC")
                    .generatePrivate(PKCS8EncodedKeySpec(pkcs8))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(der.inputStream()) as X509Certificate
                cert.checkValidity()
                Identity(privateKey, cert)
            } else {
                val created = generate()
                val data = JSONObject()
                    .put("private", Base64.encodeToString(created.privateKey.encoded, Base64.NO_WRAP))
                    .put("certificate", Base64.encodeToString(created.cert.encoded, Base64.NO_WRAP))
                check(prefs.edit().putString("wrapped_identity",
                    encrypt(data.toString().toByteArray(Charsets.UTF_8))).commit()) {
                    "Impossible de conserver l'identité TLS"
                }
                created
            }
            cache = id
            return id
        }
    }

    fun fingerprint(context: Context): String =
        CloudixSettings.fingerprint(identity(context).cert.encoded)

    fun serverContext(context: Context): SSLContext {
        val identity = identity(context)
        val storePassword = "cloudix-ephemeral-keystore".toCharArray()
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, storePassword)
            setKeyEntry("server", identity.privateKey, storePassword,
                arrayOf(identity.cert))
        }
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, storePassword)
        return SSLContext.getInstance("TLS").apply {
            init(factory.keyManagers, null, SecureRandom())
        }
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

data class Peer(
    val host: String,
    val port: Int,
    val secret: String,
    val pin: String,
    val globalIpv6: String? = null,
    val tailscale: String? = null
) {
    companion object {
        fun isNumericIp(value: String): Boolean {
            if (value.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}"))) {
                return value.split(".").all { it.toIntOrNull() in 0..255 }
            }
            if (!value.contains(':') || !value.matches(Regex("[a-fA-F0-9:]+"))) return false
            return try { InetAddress.getByName(value) is Inet6Address } catch (_: Exception) { false }
        }
        fun isPublicV6(value: String): Boolean {
            if (!value.contains(':') || !value.matches(Regex("[a-fA-F0-9:]+"))) return false
            return try {
                val parsed = InetAddress.getByName(value)
                parsed is Inet6Address && CloudixSettings.isGlobalIpv6(parsed)
            } catch (_: Exception) { false }
        }
        fun parse(link: String): Peer {
            val uri = Uri.parse(link.trim())
            require(uri.scheme == "cloudix" && uri.host == "pair") { "Lien d'appairage invalide" }
            val host = uri.getQueryParameter("host") ?: ""
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
            val key = uri.getQueryParameter("secret") ?: ""
            val pin = uri.getQueryParameter("fingerprint") ?: ""
            val ipv6 = uri.getQueryParameter("ipv6")
            val tailscale = uri.getQueryParameter("tailscale")
            require(isNumericIp(host) && port in 1..65535 &&
                key.matches(Regex("[A-Za-z0-9_-]{43}")) &&
                pin.matches(Regex("[a-f0-9]{64}")) &&
                (ipv6 == null || isPublicV6(ipv6)) &&
                (tailscale == null || CloudixTailnet.isTailnetIpv4(tailscale))) {
                "Paramètres d'appairage invalides"
            }
            return Peer(host, port, key, pin, ipv6, tailscale)
        }
    }

    fun withTailnetAddress(address: String): Peer {
        require(CloudixTailnet.isTailnetIpv4(address)) { "Adresse Tailscale invalide" }
        return copy(tailscale = address)
    }

    fun pairingLink(): String = "cloudix://pair?host=${Uri.encode(host)}" +
        "&port=${port}&secret=${Uri.encode(secret)}&fingerprint=${pin}" +
        (globalIpv6?.let { "&ipv6=${Uri.encode(it)}" } ?: "") +
        (tailscale?.let { "&tailscale=${Uri.encode(it)}" } ?: "")

    fun endpoints(preferIpv6: Boolean): List<String> {
        val ips = listOfNotNull(globalIpv6, host).distinct()
        return if (preferIpv6) ips else ips.reversed()
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
class CloudixClient(
    val peer: Peer,
    private val context: Context? = null,
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 60_000
) {
    private fun preferIpv6(): Boolean {
        if (peer.globalIpv6 == null) return false
        if (context != null && CloudixSettings(context).preferDirectIpv6) return true
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    // The VPN route is stable across Wi-Fi/5G and avoids a blocked home IPv6 firewall.
    fun selectedAddress(): String = peer.tailscale ?: peer.endpoints(preferIpv6()).first()

    private fun hostname(): String = selectedAddress()

    private fun describeFailure(e: Exception): Exception {
        if (peer.tailscale != null &&
            (e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
             e is java.net.NoRouteToHostException)) {
            return CloudixConnectionException(
                "Tailscale ne répond pas. Active le VPN Tailscale sur les deux téléphones, vérifie qu'ils sont sur le même compte, puis relance le test.", e)
        }
        if (preferIpv6() && peer.globalIpv6 != null &&
            (e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
             e is java.net.NoRouteToHostException)) {
            return CloudixConnectionException(
                "Connexion IPv6 directe impossible. Le Xiaomi a une adresse IPv6 publique, mais le réseau peut bloquer les connexions entrantes. Sans accès au routeur ni relais, Cloudix ne peut pas contourner ce filtrage.", e)
        }
        return CloudixNetworkFailure.describe(e)
    }

    private fun connection(route: String, method: String = "GET"): HttpsURLConnection {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            peer.globalIpv6 == null && peer.tailscale == null && peer.host.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}"))) {
            throw CloudixConnectionException("Aucune IPv6 publique enregistrée : le réseau local du Xiaomi n'est pas joignable depuis la 5G sans relais ni accès à la box.")
        }
        val ip = hostname()
        val authority = if (ip.contains(':')) "[$ip]" else ip
        val url = URL("https://$authority:${peer.port}$route")
        return (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = TlsIdentity.clientContext(peer.pin).socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true } // pin is verified by TrustManager
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
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
        } catch (e: Exception) { throw describeFailure(e) }
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
        } catch (e: Exception) { throw describeFailure(e) }
        finally { conn.disconnect() }
    }
    fun download(path: String, sink: OutputStream) {
        val conn = connection("/api/file?path=${Uri.encode(path)}")
        try {
            if (conn.responseCode != 200) throw CloudixNetworkFailure.http(conn.responseCode, conn.errorStream)
            conn.inputStream.use { input -> sink.use { output -> input.copyTo(output, 65536) } }
        } catch (e: Exception) { throw describeFailure(e) }
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
            is java.net.SocketException -> "Connexion fermée par le serveur. Vérifie que les deux appareils utilisent la dernière version de Cloudix, puis lance le test TLS local sur le Xiaomi. Détail : ${error.message ?: "socket"}."
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
