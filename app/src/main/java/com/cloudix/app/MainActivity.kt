package com.cloudix.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming = intent?.dataString ?: ""
        setContent { CloudixApp(incoming) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming = intent.dataString ?: ""
    }
}

@Composable
fun CloudixApp(incoming: String) {
    val context = LocalContext.current
    val settings = remember { CloudixSettings(context) }
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf(settings.role) }
    var page by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var rootUri by remember { mutableStateOf(settings.rootUri) }
    var peerLink by remember { mutableStateOf(settings.peer) }
    var connectionInput by remember { mutableStateOf("") }
    var currentPath by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingDelete by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingRename by remember { mutableStateOf<RemoteFile?>(null) }
    var renameName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var diagnostic by remember { mutableStateOf("") }
    var connectionVerified by remember { mutableStateOf(false) }
    var connectionChecking by remember { mutableStateOf(false) }
    var connectionChecked by remember { mutableStateOf(false) }
    var tailscaleInput by remember { mutableStateOf(settings.tailscaleAddress) }
    var clientTailscaleInput by remember { mutableStateOf("") }
    var showTailscaleDialog by remember { mutableStateOf(false) }

    fun report(error: Throwable) { message = error.message ?: "Une erreur s'est produite." }
    suspend fun refresh() {
        if (role != "client" || peerLink.isBlank()) return
        loading = true
        try {
            files = withContext(Dispatchers.IO) { CloudixClient(Peer.parse(peerLink), context).list(currentPath) }
            connectionVerified = true
            connectionChecked = true
            message = ""
        } catch (e: Exception) {
            connectionVerified = false
            connectionChecked = true
            report(e)
        }
        finally { loading = false }
    }
    val chooseRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                settings.rootUri = uri.toString()
                rootUri = settings.rootUri
            } catch (e: Exception) { report(e) }
        }
    }
    val addBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                settings.backupFolders = settings.backupFolders + uri.toString()
                BackupScheduler.schedule(context)
                revision++
            } catch (e: Exception) { report(e) }
        }
    }
    val chooseUpload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            loading = true
            val folder = currentPath
            try {
                withContext(Dispatchers.IO) {
                    val client = CloudixClient(Peer.parse(peerLink), context)
                    for (uri in uris) {
                        val resolver = context.contentResolver
                        val (name, size) = resolver.query(uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                            null, null, null)?.use { cursor ->
                            if (!cursor.moveToFirst()) throw IllegalStateException("Document introuvable")
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            val n = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                            val s = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex))
                                cursor.getLong(sizeIndex) else -1L
                            (n ?: "Fichier") to s
                        } ?: throw IllegalStateException("Fichier illisible")
                        if (size < 0) throw IllegalStateException("Taille inconnue : $name")
                        val stream = resolver.openInputStream(uri) ?: throw IllegalStateException("Lecture impossible")
                        client.upload(folder, name, size, stream)
                    }
                }
                refresh()
            } catch (e: Exception) { report(e) }
            finally { loading = false }
        }
    }
    val createDownload = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = pendingDownload
        if (uri != null && file != null) scope.launch {
            loading = true
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Impossible de créer le fichier")
                    CloudixClient(Peer.parse(peerLink), context).download(file.path, output)
                }
                message = "Fichier téléchargé : ${file.name}"
            } catch (e: Exception) { report(e) }
            finally { loading = false; pendingDownload = null }
        }
    }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { connectionInput = it }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(incoming) {
        if (incoming.startsWith("cloudix://pair")) {
            connectionInput = incoming
            if (role.isBlank()) { role = "client"; settings.role = role }
        }
    }
    LaunchedEffect(role, peerLink, currentPath) {
        if (role == "client" && peerLink.isNotEmpty()) refresh()
    }
    LaunchedEffect(peerLink) {
        connectionVerified = false
        connectionChecked = false
    }
    BackHandler(enabled = role == "client" && page == 1 && currentPath.isNotBlank()) {
        currentPath = currentPath.substringBeforeLast('/', "")
    }

    CloudixTheme(settings) {
        if (role.isBlank()) {
            WelcomeScreen(onServer = { role = "server"; settings.role = role },
                onClient = { role = "client"; settings.role = role })
        } else {
            CloudixScaffold(role, page, { page = it },
                "Cloudix", if (role == "server") "Ton espace privé" else "Ton cloud, partout") { insets ->
                AnimatedContent(targetState = page,
                    transitionSpec = {
                        if (settings.animations) {
                            (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 7 }) togetherWith
                                (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -it / 7 })
                        } else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }, label = "cloudix-navigation") { tab ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        when (tab) {
                            0 -> Column(Modifier.fillMaxSize().verticalScrollCompat()
                                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (role == "server") {
                                    HeroCard(
                                        if (CloudixStatus.running.value) "Serveur en ligne" else "Serveur en veille",
                                        if (CloudixStatus.running.value)
                                            "Le Xiaomi partage ton dossier en HTTPS sur ton réseau local."
                                        else "Choisis un dossier puis démarre le partage.",
                                        Icons.Rounded.Dns
                                    ) {
                                        Button(onClick = {
                                            if (CloudixStatus.running.value) {
                                                context.startService(Intent(context, CloudixService::class.java)
                                                    .setAction("STOP"))
                                            } else {
                                                if (Build.VERSION.SDK_INT >= 33)
                                                    permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                try { context.startForegroundService(
                                                    Intent(context, CloudixService::class.java)) }
                                                catch (e: Exception) { report(e) }
                                            }
                                        }, enabled = rootUri.isNotBlank()) {
                                            Icon(if (CloudixStatus.running.value) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (CloudixStatus.running.value) "Arrêter" else "Démarrer")
                                        }
                                        OutlinedButton(onClick = { chooseRoot.launch(null) }) { Text("Dossier") }
                                    }
                                    if (CloudixStatus.error.value.isNotBlank())
                                        Text(CloudixStatus.error.value, color = MaterialTheme.colorScheme.error)
                                    if (CloudixStatus.lastConnectionIssue.value.isNotBlank()) {
                                        ElevatedCard(colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                                Text("Dernière erreur réseau", fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                                Text(CloudixStatus.lastConnectionIssue.value,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                    }
                                    SectionTitle("Connexion", "HTTPS + PIN TLS")
                                    SettingItem(Icons.Rounded.HealthAndSafety, "Diagnostic réseau",
                                        if (diagnostic.isBlank()) "Vérifier TLS sur le Xiaomi"
                                        else diagnostic) {
                                        TextButton(enabled = CloudixStatus.running.value, onClick = {
                                            scope.launch {
                                                diagnostic = "Test en cours…"
                                                try {
                                                    val latency = withContext(Dispatchers.IO) {
                                                        CloudixClient(Peer("127.0.0.1", 8731,
                                                            settings.secret(), TlsIdentity.fingerprint(context))).ping()
                                                    }
                                                    diagnostic = "TLS opérationnel · $latency ms"
                                                } catch (e: Exception) {
                                                    diagnostic = "Échec local : ${e.message}"
                                                }
                                            }
                                        }) { Text("Tester") }
                                    }
                                    SettingItem(Icons.Rounded.Wifi, "Adresse sur le réseau",
                                        "${CloudixSettings.localAddress() ?: "Wi-Fi indisponible"}:8731")
                                    SettingItem(Icons.Rounded.Folder, "Dossier partagé",
                                        if (rootUri.isEmpty()) "Aucun dossier sélectionné"
                                        else DocumentFile.fromTreeUri(context, Uri.parse(rootUri))?.name ?: "Sélectionné") {
                                        TextButton(onClick = { chooseRoot.launch(null) }) { Text("Changer") }
                                    }
                                    if (CloudixStatus.running.value) {
                                        val pairing = remember(CloudixStatus.running.value, revision) {
                                            try { settings.serverLink() } catch (_: Exception) { "" }
                                        }
                                        if (pairing.isNotBlank()) {
                                            PairingCard(pairing, onShare = {
                                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, pairing)
                                                }, "Partager l'appairage"))
                                            })
                                        }
                                    }
                                    SectionTitle("Tailscale · Connexion 5G")
                                    val detectedTailscale = remember(revision, CloudixStatus.running.value) {
                                        CloudixTailnet.detectOnDevice()
                                    }
                                    SettingItem(Icons.Rounded.VpnKey, "VPN privé du Xiaomi",
                                        detectedTailscale?.let { "Adresse détectée : $it" }
                                            ?: "Installe Tailscale puis relève l'adresse 100.x du Xiaomi")
                                    OutlinedTextField(
                                        value = tailscaleInput,
                                        onValueChange = { tailscaleInput = it.trim() },
                                        label = { Text("Adresse Tailscale du Xiaomi") },
                                        placeholder = { Text("100.100.100.100") },
                                        supportingText = {
                                            Text("IP de Tailscale, pas l'IP Wi-Fi. Laisse vide si détectée automatiquement.")
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            try {
                                                settings.tailscaleAddress = tailscaleInput
                                                revision++
                                                message = "Adresse VPN enregistrée. Affiche un nouveau QR d'appairage sur le Xiaomi."
                                            } catch (e: Exception) { report(e) }
                                        }) { Text("Enregistrer") }
                                        OutlinedButton(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")))
                                        }) { Text("Installer Tailscale") }
                                    }
                                    Text("Tailscale doit être connecté sur les deux téléphones. Le réseau VPN est gratuit pour un usage personnel et ne stocke pas tes fichiers.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                    SectionTitle("Connexion directe 5G")
                                    val globalV6 = remember(revision, CloudixStatus.running.value) {
                                        CloudixSettings.globalIpv6Address()
                                    }
                                    SettingItem(Icons.Rounded.Public, "Adresse IPv6 publique",
                                        globalV6 ?: "Indisponible sur le Wi-Fi du Xiaomi") {
                                        IconButton(onClick = { revision++ }) {
                                            Icon(Icons.Rounded.Refresh,
                                                contentDescription = "Actualiser l'adresse IPv6")
                                        }
                                    }
                                    Text(if (globalV6 == null)
                                        "Aucune IPv6 publique détectée. Sans accès à la box ni relais, la connexion 5G directe est impossible sur ce réseau."
                                    else
                                        "IPv6 détectée : teste le QR depuis le Vivo en 5G. Le pare-feu de la box peut encore bloquer les connexions entrantes.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                } else {
                                    HeroCard(
                                        when {
                                            peerLink.isBlank() -> "Connecte ton serveur"
                                            connectionChecking -> "Connexion en cours"
                                            connectionVerified -> "Xiaomi connecté"
                                            connectionChecked -> "Xiaomi inaccessible"
                                            else -> "Xiaomi appairé"
                                        },
                                        when {
                                            peerLink.isBlank() -> "Scanne le QR code du Xiaomi pour démarrer."
                                            connectionVerified -> "Connexion TLS active : tes fichiers sont accessibles."
                                            connectionChecking -> "Vérification de la liaison avec le Xiaomi…"
                                            connectionChecked -> "Le Xiaomi n'est pas joignable. Lance le diagnostic pour connaître la cause."
                                            else -> "Le serveur est enregistré, mais sa disponibilité n'a pas été vérifiée."
                                        },
                                        if (connectionVerified) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue
                                    ) {
                                        Button(onClick = { page = if (peerLink.isBlank()) 0 else 1 },
                                            enabled = peerLink.isNotBlank()) {
                                            Text("Mes fichiers"); Icon(Icons.Rounded.ChevronRight, null)
                                        }
                                    }
                                    if (peerLink.isBlank()) {
                                        PairInput(connectionInput, { connectionInput = it },
                                            onScan = {
                                                qrScanner.launch(ScanOptions()
                                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                    .setPrompt("Scanne le code Cloudix du Xiaomi")
                                                    .setBeepEnabled(false))
                                            },
                                            onConnect = {
                                                scope.launch {
                                                    try {
                                                        val link = connectionInput.trim()
                                                        withContext(Dispatchers.IO) {
                                                            val remote = CloudixClient(Peer.parse(link), context)
                                                            remote.ping()
                                                            remote.list("")
                                                        }
                                                        settings.peer = link
                                                        peerLink = link
                                                        message = "Appairage réussi"
                                                    } catch (e: Exception) { report(e) }
                                                }
                                            })
                                    } else {
                                        val paired = remember(peerLink) {
                                            try { Peer.parse(peerLink) } catch (_: Exception) { null }
                                        }
                                        if (paired != null) {
                                            SettingItem(Icons.Rounded.VpnKey, "VPN Tailscale (5G)",
                                                paired.tailscale?.let { "Xiaomi : $it — VPN requis sur les deux appareils" }
                                                    ?: "Configurer l'IP Tailscale 100.x du Xiaomi") {
                                                TextButton(onClick = {
                                                    clientTailscaleInput = paired.tailscale.orEmpty()
                                                    showTailscaleDialog = true
                                                }) { Text("Configurer") }
                                            }
                                            SettingItem(Icons.Rounded.Public, "IPv6 directe",
                                                if (paired.globalIpv6 == null)
                                                    "Aucune IPv6 publique enregistrée."
                                                else if (paired.tailscale != null)
                                                    "Tailscale a la priorité sur l'IPv6 publique."
                                                else "IPv6 enregistrée, mais la box peut bloquer les accès.")
                                            if (paired.tailscale == null) {
                                                OutlinedButton(onClick = {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW,
                                                        Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")))
                                                }) { Text("Installer Tailscale") }
                                            }
                                        }
                                        if (paired?.globalIpv6 != null) {
                                            SettingItem(Icons.Rounded.SettingsEthernet, "Privilégier IPv6",
                                                "Utiliser l'adresse IPv6 même quand le Vivo est en Wi-Fi") {
                                                Switch(settings.preferDirectIpv6, onCheckedChange = {
                                                    settings.preferDirectIpv6 = it
                                                    revision++
                                                })
                                            }
                                        }
                                        SectionTitle("Raccourcis")
                                        SettingItem(Icons.Rounded.CheckCircle, "Tester la connexion",
                                            diagnostic.ifBlank { "Vérifier Wi-Fi, IPv6, TLS et authentification" }) {
                                            TextButton(enabled = !connectionChecking, onClick = {
                                                scope.launch {
                                                    connectionChecking = true
                                                    connectionVerified = false
                                                    diagnostic = "Diagnostic en cours (maximum ~12 s)…"
                                                    try {
                                                        val result = withContext(Dispatchers.IO) {
                                                            CloudixConnectivity.check(context, Peer.parse(peerLink))
                                                        }
                                                        connectionVerified = result.reachable
                                                        connectionChecked = true
                                                        diagnostic = "${result.heading} · ${result.path} · ${result.elapsedMs} ms"
                                                        if (!result.reachable) message = result.explanation
                                                    } catch (e: Exception) {
                                                        connectionVerified = false
                                                        connectionChecked = true
                                                        diagnostic = "Échec du diagnostic"
                                                        report(e)
                                                    } finally {
                                                        connectionChecking = false
                                                    }
                                                }
                                            }) { Text(if (connectionChecking) "Analyse…" else "Tester") }
                                        }
                                        SettingItem(Icons.Rounded.FolderOpen, "Explorer les fichiers",
                                            "Dossiers du serveur") { TextButton(onClick = { page = 1 }) { Text("Ouvrir") } }
                                        SettingItem(Icons.Rounded.Backup, "Sauvegarde automatique",
                                            "${settings.backupFolders.size} dossier(s) configuré(s)") {
                                            TextButton(onClick = { page = 2 }) { Text("Gérer") }
                                        }
                                    }
                                }
                            }
                            1 -> if (role == "server") {
                                Column(Modifier.fillMaxSize().verticalScrollCompat()
                                    .padding(20.dp)) {
                                    SectionTitle("Stockage partagé")
                                    SettingItem(Icons.Rounded.Folder, "Dossier racine", rootUri.ifBlank { "Non sélectionné" }) {
                                        TextButton(onClick = { chooseRoot.launch(null) }) { Text("Choisir") }
                                    }
                                    Text("Seul le dossier choisi via le système Android est exposé au client.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        currentPath = currentPath.substringBeforeLast('/', "")
                                    }, enabled = currentPath.isNotBlank()) { Icon(Icons.Rounded.ArrowBack, "Retour") }
                                    Column(Modifier.weight(1f)) {
                                        Text(if (currentPath.isBlank()) "Mes fichiers" else currentPath,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${files.size} éléments", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { scope.launch { refresh() } }) {
                                        Icon(Icons.Rounded.Refresh, "Actualiser")
                                    }
                                    IconButton(onClick = { showFolderDialog = true }) {
                                        Icon(Icons.Rounded.CreateNewFolder, "Nouveau dossier")
                                    }
                                }
                                OutlinedTextField(value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, "Effacer")
                                        }
                                    }, label = { Text("Rechercher dans ce dossier") },
                                    shape = MaterialTheme.shapes.large)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("name" to "Nom", "date" to "Récents", "size" to "Taille").forEach { (id, label) ->
                                        FilterChip(selected = settings.sort == id,
                                            onClick = { settings.sort = id; revision++ },
                                            label = { Text(label) })
                                    }
                                }
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                val visibleFiles = files.filter {
                                    it.name.contains(searchQuery, ignoreCase = true)
                                }.sortedWith(Comparator { a, b ->
                                    val directoryFirst = b.directory.compareTo(a.directory)
                                    if (directoryFirst != 0) directoryFirst else when (settings.sort) {
                                        "date" -> b.modified.compareTo(a.modified)
                                        "size" -> b.size.compareTo(a.size)
                                        else -> a.name.compareTo(b.name, ignoreCase = true)
                                    }
                                })
                                if (!loading && visibleFiles.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Rounded.FolderOff, null,
                                                modifier = Modifier.size(44.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(10.dp))
                                            Text(if (searchQuery.isNotBlank()) "Aucun résultat" else "Dossier vide",
                                                style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(visibleFiles, key = { it.path }) { file ->
                                        ElevatedCard(onClick = {
                                            if (file.directory) currentPath = file.path
                                            else { pendingDownload = file; createDownload.launch(file.name) }
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Icon(when {
                                                    file.directory -> Icons.Rounded.Folder
                                                    file.mime.startsWith("image/") -> Icons.Rounded.Image
                                                    file.mime.startsWith("video/") -> Icons.Rounded.Movie
                                                    file.mime == "application/pdf" -> Icons.Rounded.PictureAsPdf
                                                    file.mime.startsWith("audio/") -> Icons.Rounded.AudioFile
                                                    else -> Icons.Rounded.InsertDriveFile
                                                }, null, tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(30.dp))
                                                Spacer(Modifier.width(14.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        fontWeight = FontWeight.Medium)
                                                    Text(if (file.directory) "Dossier" else readableBytes(file.size),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(onClick = {
                                                    if (file.directory) currentPath = file.path
                                                    else { pendingDownload = file; createDownload.launch(file.name) }
                                                }) { Icon(if (file.directory) Icons.Rounded.ChevronRight
                                                    else Icons.Rounded.Download, "Ouvrir") }
                                                var expanded by remember(file.path) { mutableStateOf(false) }
                                                Box {
                                                    IconButton(onClick = { expanded = true }) {
                                                        Icon(Icons.Rounded.MoreVert, "Options du fichier")
                                                    }
                                                    DropdownMenu(expanded = expanded, onDismissRequest = {
                                                        expanded = false
                                                    }) {
                                                        DropdownMenuItem(
                                                            text = { Text("Renommer") },
                                                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                                                            onClick = {
                                                                expanded = false; pendingRename = file; renameName = file.name
                                                            })
                                                        DropdownMenuItem(
                                                            text = { Text("Supprimer") },
                                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                                            onClick = { expanded = false; pendingDelete = file })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Button(onClick = { chooseUpload.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Icon(Icons.Rounded.UploadFile, null)
                                    Spacer(Modifier.width(8.dp)); Text("Envoyer des fichiers")
                                }
                            }
                            2 -> if (role == "client") {
                                Column(Modifier.fillMaxSize().verticalScrollCompat().padding(20.dp)) {
                                    SectionTitle("Sauvegardes automatiques")
                                    Text("Cloudix transfère uniquement les dossiers que tu autorises. Android planifie les tâches en arrière-plan (fréquence approximative).",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(16.dp))
                                    SettingItem(Icons.Rounded.Wifi, "Wi-Fi uniquement",
                                        "Éviter les données mobiles") {
                                        Switch(settings.wifiOnly, onCheckedChange = {
                                            settings.wifiOnly = it; BackupScheduler.schedule(context); revision++
                                        })
                                    }
                                    SectionTitle("Fréquence")
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(15L, 60L, 360L, 1440L).forEach { mins ->
                                            FilterChip(selected = settings.interval == mins,
                                                onClick = {
                                                    settings.interval = mins
                                                    BackupScheduler.schedule(context); revision++
                                                },
                                                label = { Text(when (mins) { 15L -> "15 min"
                                                    60L -> "1 h"; 360L -> "6 h"; else -> "24 h" }) })
                                        }
                                    }
                                    SectionTitle("Dossiers surveillés", "${settings.backupFolders.size}")
                                    settings.backupFolders.forEach { uri ->
                                        val name = remember(uri) {
                                            DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name ?: "Dossier"
                                        }
                                        SettingItem(Icons.Rounded.FolderCopy, name,
                                            "Sauvegardé vers Backups/") {
                                            IconButton(onClick = {
                                                settings.backupFolders = settings.backupFolders - uri
                                                BackupScheduler.schedule(context); revision++
                                            }) { Icon(Icons.Rounded.Close, "Retirer") }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    FilledTonalButton(onClick = { addBackup.launch(null) },
                                        modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Rounded.Add, null)
                                        Spacer(Modifier.width(8.dp)); Text("Ajouter un dossier à sauvegarder")
                                    }
                                    OutlinedButton(onClick = {
                                        if (peerLink.isBlank() || settings.backupFolders.isEmpty()) {
                                            message = "Connecte un serveur et sélectionne au moins un dossier."
                                        } else {
                                            BackupScheduler.runNow(context)
                                            message = "Sauvegarde demandée. Le système l'exécutera lorsque le Wi-Fi sera disponible."
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Rounded.Sync, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Sauvegarder maintenant")
                                    }
                                    if (settings.lastBackupAt > 0L) {
                                        SettingItem(Icons.Rounded.History, "Dernier passage",
                                            java.text.DateFormat.getDateTimeInstance().format(
                                                java.util.Date(settings.lastBackupAt)))
                                    }
                                    Text("Si le Xiaomi n'est pas accessible, WorkManager réessaiera plus tard. Les fichiers supprimés ne sont pas effacés du serveur.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            3 -> AppearancePage(settings) { revision++ }
                            4 -> Column(Modifier.fillMaxSize().verticalScrollCompat().padding(20.dp)) {
                                SectionTitle("Configuration")
                                SettingItem(Icons.Rounded.Security, "Chiffrement et appairage",
                                    "TLS avec certificat épinglé et secret aléatoire")
                                if (role == "server") {
                                    SettingItem(Icons.Rounded.PowerSettingsNew,
                                        "Démarrer après redémarrage",
                                        "Lancer automatiquement le serveur au démarrage du Xiaomi") {
                                        Switch(checked = settings.serverAutoStart,
                                            onCheckedChange = {
                                                settings.serverAutoStart = it; revision++
                                            })
                                    }
                                    Text("HyperOS peut bloquer les démarrages automatiques : autorise Cloudix dans les paramètres d'activité en arrière-plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = {
                                        settings.regenerateSecret()
                                        message = "Ancien appairage révoqué. Partage un nouveau QR code."
                                        revision++
                                    }) { Text("Révoquer tous les clients appairés") }
                                } else {
                                    OutlinedButton(onClick = {
                                        settings.peer = ""; peerLink = ""; currentPath = ""; files = emptyList()
                                    }) { Text("Déconnecter ce serveur") }
                                }
                                UpdatesPanel()
                                SectionTitle("Appareil")
                                SettingItem(Icons.Rounded.PhoneAndroid, "Mode actif",
                                    if (role == "server") "Serveur" else "Client")
                                OutlinedButton(onClick = {
                                    if (role == "server") context.startService(
                                        Intent(context, CloudixService::class.java).setAction("STOP"))
                                    settings.role = ""; role = ""; page = 0
                                }) { Text("Changer le rôle de ce téléphone") }
                                Spacer(Modifier.height(16.dp))
                                Text("Cloudix 0.3.0 · IPv6 directe · Signature mise à jour selon l'APK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (showTailscaleDialog) {
            AlertDialog(
                onDismissRequest = { showTailscaleDialog = false },
                title = { Text("Connexion Tailscale") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Entre l'adresse 100.x affichée dans Tailscale SUR LE XIAOMI. Ne saisis pas celle du Vivo.")
                        OutlinedTextField(
                            value = clientTailscaleInput,
                            onValueChange = { clientTailscaleInput = it.trim() },
                            label = { Text("IP Tailscale du Xiaomi") },
                            placeholder = { Text("100.100.100.100") },
                            singleLine = true
                        )
                        Text("La clé d'appairage et la vérification TLS existantes sont conservées.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            val updated = Peer.parse(peerLink)
                                .withTailnetAddress(clientTailscaleInput.trim()).pairingLink()
                            settings.peer = updated
                            peerLink = updated
                            connectionVerified = false
                            connectionChecked = false
                            diagnostic = ""
                            showTailscaleDialog = false
                            message = "Adresse Tailscale enregistrée. Active Tailscale sur les deux téléphones, puis appuie sur Tester."
                        } catch (e: Exception) { report(e) }
                    }) { Text("Enregistrer") }
                },
                dismissButton = {
                    TextButton(onClick = { showTailscaleDialog = false }) { Text("Annuler") }
                }
            )
        }
        if (message.isNotEmpty()) {
            AlertDialog(onDismissRequest = { message = "" }, confirmButton = {
                TextButton(onClick = { message = "" }) { Text("OK") }
            }, title = { Text("Cloudix") }, text = { Text(message) })
        }
        pendingRename?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingRename = null },
                title = { Text("Renommer") },
                text = { OutlinedTextField(renameName, { renameName = it },
                    label = { Text("Nouveau nom") }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRename = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    CloudixClient(Peer.parse(peerLink), context).rename(file.path, renameName)
                                }
                                refresh()
                            } catch (e: Exception) { report(e) }
                        }
                    }, enabled = renameName.isNotBlank()) { Text("Enregistrer") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRename = null }) { Text("Annuler") }
                }
            )
        }
        pendingDelete?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Supprimer ${file.name} ?") },
                text = { Text("Cette suppression est définitive sur le serveur.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    CloudixClient(Peer.parse(peerLink), context).delete(file.path)
                                }
                                refresh()
                            } catch (e: Exception) { report(e) }
                        }
                    }) { Text("Supprimer") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuler") } }
            )
        }
        if (showFolderDialog) {
            AlertDialog(onDismissRequest = { showFolderDialog = false },
                title = { Text("Nouveau dossier") },
                text = { OutlinedTextField(newFolder, { newFolder = it }, label = { Text("Nom") }) },
                confirmButton = {
                    TextButton(onClick = {
                        showFolderDialog = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    CloudixClient(Peer.parse(peerLink), context).mkdir(currentPath, newFolder)
                                }
                                newFolder = ""; refresh()
                            } catch (e: Exception) { report(e) }
                        }
                    }) { Text("Créer") }
                },
                dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Annuler") } }
            )
        }
    }
}

@Composable
private fun PairInput(link: String, onLink: (String) -> Unit, onScan: () -> Unit, onConnect: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle("Associer ton Xiaomi")
            OutlinedTextField(link, onLink, modifier = Modifier.fillMaxWidth(),
                label = { Text("Lien Cloudix") },
                placeholder = { Text("cloudix://pair?...") }, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(8.dp))
                    Text("Scanner")
                }
                Button(onClick = onConnect, enabled = link.isNotBlank()) { Text("Se connecter") }
            }
        }
    }
}
@Composable
private fun PairingCard(link: String, onShare: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Appairer un appareil", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text("Le QR code est confidentiel : il donne accès à tes fichiers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (reveal) {
                val bitmap = remember(link) {
                    val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 450, 450)
                    android.graphics.Bitmap.createBitmap(450, 450, android.graphics.Bitmap.Config.ARGB_8888)
                        .apply {
                            for (y in 0 until 450) for (x in 0 until 450)
                                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK
                                else android.graphics.Color.WHITE)
                        }
                }
                Image(bitmap.asImageBitmap(), "Code QR d'appairage",
                    modifier = Modifier.size(220.dp))
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { reveal = !reveal }) {
                Text(if (reveal) "Masquer le QR" else "Afficher le QR")
            }
            TextButton(onClick = onShare) { Icon(Icons.Rounded.Share, null)
                Spacer(Modifier.width(6.dp)); Text("Partager le lien") }
        }
    }
}
private fun readableBytes(n: Long): String = when {
    n < 1024 -> "$n o"
    n < 1024 * 1024 -> "${n / 1024} Ko"
    n < 1024L * 1024L * 1024L -> "${n / (1024 * 1024)} Mo"
    else -> "${"%.1f".format(n.toDouble() / (1024.0 * 1024 * 1024))} Go"
}
