package com.cloudix.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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

    fun report(error: Throwable) { message = error.message ?: "Une erreur s'est produite." }
    suspend fun refresh() {
        if (role != "client" || peerLink.isBlank()) return
        loading = true
        try {
            files = withContext(Dispatchers.IO) { CloudixClient(Peer.parse(peerLink)).list(currentPath) }
            message = ""
        } catch (e: Exception) { report(e) }
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
                    val client = CloudixClient(Peer.parse(peerLink))
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
                    CloudixClient(Peer.parse(peerLink)).download(file.path, output)
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
    BackHandler(enabled = role == "client" && page == 1 && currentPath.isNotBlank()) {
        currentPath = currentPath.substringBeforeLast('/', "")
    }

    CloudixTheme(settings, revision) {
        if (role.isBlank()) {
            WelcomeScreen(onServer = { role = "server"; settings.role = role },
                onClient = { role = "client"; settings.role = role })
        } else {
            CloudixScaffold(role, page, { page = it },
                "Cloudix", if (role == "server") "Ton espace privé" else "Ton cloud, partout") { insets ->
                Crossfade(page, label = "cloudix-navigation") { tab ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        when (tab) {
                            0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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
                                    SectionTitle("Connexion", "Chiffrée TLS")
                                    SettingItem(Icons.Rounded.Wifi, "Adresse sur le réseau",
                                        "${CloudixSettings.localAddress() ?: "Wi-Fi indisponible"}:8731")
                                    SettingItem(Icons.Rounded.Folder, "Dossier partagé",
                                        if (rootUri.isEmpty()) "Aucun dossier sélectionné"
                                        else DocumentFile.fromTreeUri(context, Uri.parse(rootUri))?.name ?: "Sélectionné") {
                                        TextButton(onClick = { chooseRoot.launch(null) }) { Text("Changer") }
                                    }
                                    if (CloudixStatus.running.value) {
                                        val pairing = remember(CloudixStatus.running.value) {
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
                                    Text("Le serveur doit rester actif. L'accès Internet/5G hors Wi-Fi n'est pas encore intégré.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                } else {
                                    HeroCard(if (peerLink.isBlank()) "Connecte ton serveur" else "Cloud connecté",
                                        if (peerLink.isBlank()) "Scanne le QR code du Xiaomi pour démarrer."
                                        else "Parcours tes fichiers et automatise tes sauvegardes.",
                                        Icons.Rounded.CloudDone
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
                                                            CloudixClient(Peer.parse(link)).list("")
                                                        }
                                                        settings.peer = link
                                                        peerLink = link
                                                        message = "Appairage réussi"
                                                    } catch (e: Exception) { report(e) }
                                                }
                                            })
                                    } else {
                                        SectionTitle("Raccourcis")
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
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Spacer(Modifier.height(12.dp))
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(files.sortedWith(compareByDescending<RemoteFile> { it.directory }
                                        .thenBy { it.name.lowercase() }), key = { it.path }) { file ->
                                        ElevatedCard(onClick = {
                                            if (file.directory) currentPath = file.path
                                            else { pendingDownload = file; createDownload.launch(file.name) }
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Icon(if (file.directory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                                    null, tint = MaterialTheme.colorScheme.primary,
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
                                                IconButton(onClick = {
                                                    scope.launch {
                                                        try {
                                                            withContext(Dispatchers.IO) {
                                                                CloudixClient(Peer.parse(peerLink)).delete(file.path)
                                                            }
                                                            refresh()
                                                        } catch (e: Exception) { report(e) }
                                                    }
                                                }) { Icon(Icons.Rounded.DeleteOutline, "Supprimer") }
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
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
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
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val result = BackupWorker(context,
                                                    androidx.work.WorkerParameters::class.java)
                                            } catch (_: Exception) { }
                                        }
                                        BackupScheduler.schedule(context)
                                        message = "Planification actualisée"
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Actualiser la planification")
                                    }
                                    Text("Si le Xiaomi n'est pas accessible, WorkManager réessaiera plus tard. Les fichiers supprimés ne sont pas effacés du serveur.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            3 -> AppearancePage(settings) { revision++ }
                            4 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                                SectionTitle("Configuration")
                                SettingItem(Icons.Rounded.Security, "Chiffrement et appairage",
                                    "TLS avec certificat épinglé et secret aléatoire")
                                if (role == "server") {
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
                                SectionTitle("Appareil")
                                SettingItem(Icons.Rounded.PhoneAndroid, "Mode actif",
                                    if (role == "server") "Serveur" else "Client")
                                OutlinedButton(onClick = {
                                    if (role == "server") context.startService(
                                        Intent(context, CloudixService::class.java).setAction("STOP"))
                                    settings.role = ""; role = ""; page = 0
                                }) { Text("Changer le rôle de ce téléphone") }
                                Spacer(Modifier.height(16.dp))
                                Text("Cloudix 0.1.0 · Version initiale · Accès Wi-Fi local uniquement",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (message.isNotEmpty()) {
            AlertDialog(onDismissRequest = { message = "" }, confirmButton = {
                TextButton(onClick = { message = "" }) { Text("OK") }
            }, title = { Text("Cloudix") }, text = { Text(message) })
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
                                    CloudixClient(Peer.parse(peerLink)).mkdir(currentPath, newFolder)
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
