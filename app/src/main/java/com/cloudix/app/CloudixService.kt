package com.cloudix.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import android.util.Log
import kotlin.concurrent.thread

object CloudixStatus {
    val running = mutableStateOf(false)
    val error = mutableStateOf("")
    val lastConnectionIssue = mutableStateOf("")
    val successfulRequests = mutableStateOf(0)
}
class CloudixService : Service() {
    private var server: SSLServerSocket? = null
    private val pool = ThreadPoolExecutor(2, 4, 30L, TimeUnit.SECONDS,
        ArrayBlockingQueue(16)) { runnable ->
        Thread(runnable, "cloudix-transfer").apply { isDaemon = true }
    }
    @Volatile private var active = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (active) return START_STICKY
        if (CloudixSettings(this).rootUri.isBlank()) {
            CloudixStatus.error.value = "Choisis un dossier serveur avant de démarrer."
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("cloudix_service",
            "Serveur Cloudix", NotificationManager.IMPORTANCE_LOW))
        val stop = android.app.PendingIntent.getService(
            this, 1, Intent(this, CloudixService::class.java).setAction("STOP"),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, "cloudix_service")
            .setContentTitle("Cloudix · serveur actif")
            .setContentText("Fichiers disponibles sur ton réseau local")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Arrêter", stop).build())
            .build()
        startForeground(75, notification)
        active = true
        thread(name = "cloudix-listener", isDaemon = true) {
            try {
                val socket = TlsIdentity.serverContext(this).serverSocketFactory
                    .createServerSocket(8731, 12) as SSLServerSocket
                server = socket
                socket.enabledProtocols = socket.supportedProtocols.filter {
                    it == "TLSv1.3" || it == "TLSv1.2"
                }.toTypedArray()
                CloudixStatus.error.value = ""
                CloudixStatus.lastConnectionIssue.value = ""
                CloudixStatus.running.value = true
                while (active) {
                    val client = socket.accept()
                    try {
                        pool.execute { client.use {
                            try {
                                val ssl = it as SSLSocket
                                ssl.soTimeout = 15_000
                                ssl.startHandshake()
                                ssl.soTimeout = 70_000
                                serve(ssl)
                            } catch (e: Exception) {
                                CloudixStatus.lastConnectionIssue.value =
                                    "${e.javaClass.simpleName}: ${e.message?.take(140) ?: "Négociation interrompue"}"
                                Log.w("Cloudix", "Connexion TLS/HTTP refusée", e)
                            }
                        } }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        client.close()
                    }
                }
            } catch (e: Exception) {
                if (active) CloudixStatus.error.value = e.message ?: "Impossible de démarrer le serveur."
            } finally {
                CloudixStatus.running.value = false
                active = false
                stopSelf()
            }
        }
        return START_STICKY
    }
    override fun onDestroy() {
        active = false
        server?.close()
        pool.shutdownNow()
        CloudixStatus.running.value = false
        super.onDestroy()
    }
    private fun root(): DocumentFile {
        val uri = Uri.parse(CloudixSettings(this).rootUri)
        return DocumentFile.fromTreeUri(this, uri)
            ?.takeIf { it.isDirectory } ?: throw IOException("Dossier serveur inaccessible")
    }
    private fun segments(path: String): List<String> {
        require(!path.startsWith("/")) { "Chemin absolu interdit" }
        if (path.isBlank()) return emptyList()
        return path.split("/").also {
            require(it.size <= 32 && it.all { name -> validName(name) }) { "Chemin invalide" }
        }
    }
    private fun validName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.length <= 180 &&
            !name.contains('/') && !name.contains('\\') &&
            !name.contains('\u0000') &&
            !name.any { it.code < 32 }
    private fun locate(path: String): DocumentFile {
        var item = root()
        for (part in segments(path)) {
            if (!item.isDirectory) throw FileNotFoundException("Pas un dossier")
            item = item.findFile(part) ?: throw FileNotFoundException("Introuvable")
        }
        return item
    }
    private fun readLine(input: BufferedInputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        while (buffer.size() <= 8192) {
            val b = input.read()
            if (b < 0) throw IOException("Connexion interrompue")
            if (b == 10) return buffer.toString("UTF-8").trimEnd('\r')
            buffer.write(b)
        }
        throw IOException("En-tête trop long")
    }
    private fun response(out: BufferedOutputStream, code: Int, body: ByteArray,
                         type: String = "application/json; charset=utf-8") {
        val reason = when(code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            404 -> "Not Found"; 405 -> "Method Not Allowed"
            409 -> "Conflict"; 413 -> "Payload Too Large"
            else -> "Server Error"
        }
        out.write(("HTTP/1.1 $code $reason\r\n" +
                "Content-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }
    private fun json(out: BufferedOutputStream, code: Int, message: String) {
        response(out, code, JSONObject().put("message", message).toString().toByteArray())
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 70_000
        val input = BufferedInputStream(socket.getInputStream())
        val out = BufferedOutputStream(socket.getOutputStream())
        try {
            val parts = readLine(input).split(' ')
            require(parts.size == 3 && parts[2].startsWith("HTTP/1.")) { "Requête invalide" }
            val method = parts[0]
            val target = parts[1]
            require(target.startsWith("/api/") && target.length < 2048) { "Route invalide" }
            val headers = mutableMapOf<String, String>()
            var size = 0
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                size += line.length
                require(size < 16000 && headers.size < 50) { "Trop d'en-têtes" }
                val colon = line.indexOf(':')
                require(colon > 0) { "En-tête invalide" }
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val secret = CloudixSettings(this).secret().toByteArray(StandardCharsets.UTF_8)
            val presented = headers["authorization"]?.removePrefix("Bearer ")
                ?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
            if (!MessageDigest.isEqual(secret, presented)) {
                json(out, 401, "Non appairé")
                return
            }
            val url = Uri.parse("https://localhost$target")
            fun arg(name: String): String = url.getQueryParameter(name) ?: ""
            val path = arg("path")
            when {
                method == "GET" && url.path == "/api/ping" -> {
                    response(out, 200, JSONObject().put("status", "ok").toString().toByteArray())
                    CloudixStatus.successfulRequests.value += 1
                }
                method == "GET" && url.path == "/api/list" -> {
                    val dir = locate(path)
                    require(dir.isDirectory) { "Pas un dossier" }
                    val array = JSONArray()
                    for (file in dir.listFiles()) {
                        val name = file.name ?: continue
                        array.put(JSONObject().put("name", name)
                            .put("path", if (path.isEmpty()) name else "$path/$name")
                            .put("directory", file.isDirectory).put("size", file.length())
                            .put("modified", file.lastModified()).put("mime", file.type ?: ""))
                    }
                    response(out, 200, array.toString().toByteArray())
                    CloudixStatus.successfulRequests.value += 1
                }
                method == "GET" && url.path == "/api/file" -> {
                    val file = locate(path)
                    require(file.isFile) { "Pas un fichier" }
                    val length = file.length()
                    require(length >= 0) { "Taille inconnue" }
                    val stream = contentResolver.openInputStream(file.uri) ?: throw IOException("Lecture impossible")
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                        "Content-Length: $length\r\nCache-Control: no-store\r\n" +
                        "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").toByteArray())
                    stream.use { copyExactly(it, out, length) }
                    out.flush()
                }
                method == "POST" && url.path == "/api/mkdir" -> {
                    val name = arg("name")
                    require(validName(name)) { "Nom invalide" }
                    val dir = locate(path)
                    require(dir.isDirectory) { "Dossier introuvable" }
                    if (dir.findFile(name) != null) { json(out, 409, "Existe déjà"); return }
                    require(dir.createDirectory(name) != null) { "Création impossible" }
                    json(out, 200, "Dossier créé")
                }
                method == "POST" && url.path == "/api/rename" -> {
                    require(path.isNotBlank()) { "Racine non modifiable" }
                    val name = arg("name")
                    require(validName(name)) { "Nom invalide" }
                    val file = locate(path)
                    val parent = path.substringBeforeLast('/', "")
                    val sibling = locate(parent).findFile(name)
                    if (sibling != null) {
                        json(out, 409, "Ce nom existe déjà")
                        return
                    }
                    require(file.renameTo(name)) { "Renommage impossible" }
                    json(out, 200, "Renommé")
                }
                method == "POST" && url.path == "/api/delete" -> {
                    require(path.isNotBlank()) { "Suppression de la racine interdite" }
                    val file = locate(path)
                    require(file.delete()) { "Suppression impossible" }
                    json(out, 200, "Supprimé")
                }
                method == "POST" && url.path == "/api/upload" -> {
                    val name = arg("name")
                    require(validName(name)) { "Nom invalide" }
                    val length = headers["content-length"]?.toLongOrNull() ?: -1L
                    require(length in 0..4_294_967_296L) { "Taille invalide" }
                    val dir = locate(path)
                    require(dir.isDirectory) { "Destination introuvable" }
                    var filename = name
                    var i = 1
                    while (dir.findFile(filename) != null && i < 1000) {
                        filename = "${name.substringBeforeLast('.', name)} ($i)" +
                            if ('.' in name) ".${name.substringAfterLast('.')}" else ""
                        i++
                    }
                    require(dir.findFile(filename) == null) { "Trop de doublons" }
                    val newFile = dir.createFile("application/octet-stream", filename)
                        ?: throw IOException("Création impossible")
                    try {
                        val output = contentResolver.openOutputStream(newFile.uri, "w")
                            ?: throw IOException("Ecriture impossible")
                        output.use { copyExactly(input, it, length) }
                    } catch (e: Exception) {
                        newFile.delete()
                        throw e
                    }
                    json(out, 200, "Fichier reçu")
                }
                else -> json(out, 405, "Route ou méthode inconnue")
            }
        } catch (e: Exception) {
            try {
                val status = when (e) {
                    is FileNotFoundException -> 404
                    is IllegalArgumentException -> 400
                    else -> 500
                }
                CloudixStatus.lastConnectionIssue.value = "${e.javaClass.simpleName}: ${e.message?.take(140) ?: "erreur serveur"}"
                Log.w("Cloudix", "Requête refusée", e)
                json(out, status, if (status == 500) "Erreur interne" else (e.message ?: "Erreur"))
            } catch (_: Exception) { /* client disconnected */ }
        }
    }
}
