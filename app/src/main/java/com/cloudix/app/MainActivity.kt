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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    var backupError by remember { mutableStateOf(settings.lastBackupError) }
    var backupLastAt by remember { mutableLongStateOf(settings.lastBackupAt) }
    var backupItems by remember { mutableIntStateOf(settings.lastBackupItems) }
    var manualBackupId by remember { mutableStateOf(settings.lastManualBackupId) }
    var manualBackupInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var backupNotice by remember { mutableStateOf("") }
    var manualBackupStarting by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf("") }
    var connectionVerified by remember { mutableStateOf(false) }
    var connectionChecking by remember { mutableStateOf(false) }
    var connectionChecked by remember { mutableStateOf(false) }
    var tailscaleInput by remember { mutableStateOf(settings.tailscaleAddress) }
    var clientTailscaleInput by remember { mutableStateOf("") }
    var backupDeviceNameInput by remember { mutableStateOf(settings.backupDeviceName) }
    var showTailscaleDialog by remember { mutableStateOf(false) }

    fun report(error: Throwable) { message = error.message ?: context.getString(R.string.main_une_erreur_s_est_produite) }
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
                            if (!cursor.moveToFirst()) throw IllegalStateException(context.getString(R.string.main_document_introuvable))
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            val n = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                            val s = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex))
                                cursor.getLong(sizeIndex) else -1L
                            (n ?: context.getString(R.string.main_fichier)) to s
                        } ?: throw IllegalStateException(context.getString(R.string.main_fichier_illisible))
                        if (size < 0) throw IllegalStateException("Taille inconnue : $name")
                        val stream = resolver.openInputStream(uri) ?: throw IllegalStateException(context.getString(R.string.main_lecture_impossible))
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
                        ?: throw IllegalStateException(context.getString(R.string.main_impossible_de_creer_le_fichier))
                    CloudixClient(Peer.parse(peerLink), context).download(file.path, output)
                }
                message = "Fichier téléchargé : ${file.name}"
            } catch (e: Exception) {
                // CreateDocument allocated a brand-new URI. Remove the partial file
                // on transfer failure instead of leaving a corrupted "successful" download.
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                report(e)
            }
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
    LaunchedEffect(role) {
        if (role == "client" && settings.backupFolders.isNotEmpty()) {
            // Migrate existing WorkManager schedules after an APK update:
            // older Cloudix versions required NETWORK_CONNECTED even when
            // the user had authorized VPN/4G/5G. Keep user's folder selections.
            runCatching { BackupScheduler.schedule(context) }
                .onFailure { backupNotice = "Programmation automatique : ${it.message}" }
        }
    }
    LaunchedEffect(role, peerLink, currentPath) {
        if (role == "client" && peerLink.isNotEmpty()) refresh()
    }
    LaunchedEffect(role, page, manualBackupId) {
        if (role == "client" && page == 2) {
            // Worker runs independently of Compose. Observe its actual WorkInfo ID,
            // not merely whether the request was accepted by WorkManager.
            while (true) {
                backupError = settings.lastBackupError
                backupLastAt = settings.lastBackupAt
                backupItems = settings.lastBackupItems
                manualBackupInfo = withContext(Dispatchers.IO) {
                    runCatching {
                        val id = UUID.fromString(manualBackupId)
                        WorkManager.getInstance(context).getWorkInfoById(id)
                            .get(3, TimeUnit.SECONDS)
                    }.getOrNull()
                }
                delay(1_500)
            }
        }
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
                "Cloudix") { insets ->
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
                                        if (CloudixStatus.running.value) context.getString(R.string.main_serveur_en_ligne) else context.getString(R.string.main_serveur_en_veille),
                                        if (CloudixStatus.running.value)
                                            context.getString(R.string.main_le_xiaomi_partage_ton_dossier_en_https_sur_to)
                                        else context.getString(R.string.main_choisis_un_dossier_puis_demarre_le_partage),
                                        Icons.Rounded.Dns
                                    ) {
                                        Button(onClick = {
                                            if (CloudixStatus.running.value) {
                                                context.startService(Intent(context, CloudixService::class.java)
                                                    .setAction(context.getString(R.string.main_stop)))
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
                                            Text(if (CloudixStatus.running.value) context.getString(R.string.main_arreter) else context.getString(R.string.main_demarrer))
                                        }
                                        OutlinedButton(onClick = { chooseRoot.launch(null) }) { Text(context.getString(R.string.main_dossier)) }
                                    }
                                    if (CloudixStatus.error.value.isNotBlank())
                                        Text(CloudixStatus.error.value, color = MaterialTheme.colorScheme.error)
                                    if (CloudixStatus.lastConnectionIssue.value.isNotBlank()) {
                                        ElevatedCard(colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                                Text(context.getString(R.string.main_derniere_erreur_reseau), fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                                Text(CloudixStatus.lastConnectionIssue.value,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                    }
                                    SectionTitle(context.getString(R.string.main_connexion), context.getString(R.string.main_https_pin_tls))
                                    SettingItem(Icons.Rounded.HealthAndSafety, context.getString(R.string.main_diagnostic_reseau),
                                        if (diagnostic.isBlank()) context.getString(R.string.main_verifier_tls_sur_le_xiaomi)
                                        else diagnostic) {
                                        TextButton(enabled = CloudixStatus.running.value, onClick = {
                                            scope.launch {
                                                diagnostic = context.getString(R.string.main_test_en_cours)
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
                                        }) { Text(context.getString(R.string.main_tester)) }
                                    }
                                    SettingItem(Icons.Rounded.Wifi, context.getString(R.string.main_adresse_sur_le_reseau),
                                        "${CloudixSettings.localAddress() ?: "Wi-Fi indisponible"}:8731")
                                    SettingItem(Icons.Rounded.Folder, context.getString(R.string.main_dossier_partage),
                                        if (rootUri.isEmpty()) context.getString(R.string.main_aucun_dossier_selectionne)
                                        else DocumentFile.fromTreeUri(context, Uri.parse(rootUri))?.name ?: "Sélectionné") {
                                        TextButton(onClick = { chooseRoot.launch(null) }) { Text(context.getString(R.string.main_changer)) }
                                    }
                                    if (CloudixStatus.running.value) {
                                        val pairing = remember(CloudixStatus.running.value, revision) {
                                            try { settings.serverLink() } catch (_: Exception) { "" }
                                        }
                                        if (pairing.isNotBlank()) {
                                            PairingCard(pairing, onShare = {
                                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, pairing)
                                                }, context.getString(R.string.main_partager_l_appairage)))
                                            })
                                        }
                                    }
                                    SectionTitle(context.getString(R.string.main_tailscale_connexion_5g))
                                    val detectedTailscale = remember(revision, CloudixStatus.running.value) {
                                        CloudixTailnet.detectOnDevice()
                                    }
                                    SettingItem(Icons.Rounded.VpnKey, context.getString(R.string.main_vpn_prive_du_xiaomi),
                                        detectedTailscale?.let { "Adresse détectée : $it" }
                                            ?: context.getString(R.string.main_installe_tailscale_puis_releve_l_adresse_100_))
                                    OutlinedTextField(
                                        value = tailscaleInput,
                                        onValueChange = { tailscaleInput = it.trim() },
                                        label = { Text(context.getString(R.string.main_adresse_tailscale_du_xiaomi)) },
                                        placeholder = { Text("100.100.100.100") },
                                        supportingText = {
                                            Text(context.getString(R.string.main_ip_de_tailscale_pas_l_ip_wi_fi_laisse_vide_si))
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            try {
                                                settings.tailscaleAddress = tailscaleInput
                                                revision++
                                                message = context.getString(R.string.main_adresse_vpn_enregistree_affiche_un_nouveau_qr)
                                            } catch (e: Exception) { report(e) }
                                        }) { Text(context.getString(R.string.main_enregistrer)) }
                                        OutlinedButton(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")))
                                        }) { Text(context.getString(R.string.main_installer_tailscale)) }
                                    }

                                    SectionTitle(context.getString(R.string.main_connexion_directe_5g))
                                    val globalV6 = remember(revision, CloudixStatus.running.value) {
                                        CloudixSettings.globalIpv6Address()
                                    }
                                    SettingItem(Icons.Rounded.Public, context.getString(R.string.main_adresse_ipv6_publique),
                                        globalV6 ?: context.getString(R.string.main_indisponible_sur_le_wi_fi_du_xiaomi)) {
                                        IconButton(onClick = { revision++ }) {
                                            Icon(Icons.Rounded.Refresh,
                                                contentDescription = context.getString(R.string.main_actualiser_l_adresse_ipv6))
                                        }
                                    }
                                    Text(if (globalV6 == null)
                                        context.getString(R.string.main_aucune_ipv6_publique_detectee_sans_acces_a_la)
                                    else
                                        context.getString(R.string.main_ipv6_detectee_teste_le_qr_depuis_le_vivo_en_5),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                } else {
                                    HeroCard(
                                        when {
                                            peerLink.isBlank() -> context.getString(R.string.main_connecte_ton_serveur)
                                            connectionChecking -> context.getString(R.string.main_connexion_en_cours)
                                            connectionVerified -> context.getString(R.string.main_xiaomi_connecte)
                                            connectionChecked -> context.getString(R.string.main_xiaomi_inaccessible)
                                            else -> context.getString(R.string.main_xiaomi_appaire)
                                        },
                                        when {
                                            peerLink.isBlank() -> context.getString(R.string.main_scanne_le_qr_code_du_xiaomi_pour_demarrer)
                                            connectionVerified -> context.getString(R.string.main_connexion_tls_active_tes_fichiers_sont_access)
                                            connectionChecking -> context.getString(R.string.main_verification_de_la_liaison_avec_le_xiaomi)
                                            connectionChecked -> context.getString(R.string.main_le_xiaomi_n_est_pas_joignable_lance_le_diagno)
                                            else -> context.getString(R.string.main_le_serveur_est_enregistre_mais_sa_disponibili)
                                        },
                                        if (connectionVerified) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue
                                    ) {
                                        Button(onClick = { page = if (peerLink.isBlank()) 0 else 1 },
                                            enabled = peerLink.isNotBlank()) {
                                            Text(context.getString(R.string.main_mes_fichiers)); Icon(Icons.Rounded.ChevronRight, null)
                                        }
                                    }
                                    if (peerLink.isBlank()) {
                                        PairInput(connectionInput, { connectionInput = it },
                                            onScan = {
                                                qrScanner.launch(ScanOptions()
                                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                    .setPrompt(context.getString(R.string.main_scanne_le_code_cloudix_du_xiaomi))
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
                                                        message = context.getString(R.string.main_appairage_reussi)
                                                    } catch (e: Exception) { report(e) }
                                                }
                                            })
                                    } else {
                                        val paired = remember(peerLink) {
                                            try { Peer.parse(peerLink) } catch (_: Exception) { null }
                                        }
                                        if (paired != null) {
                                            SettingItem(Icons.Rounded.VpnKey, context.getString(R.string.main_vpn_tailscale_5g),
                                                paired.tailscale?.let { "Xiaomi : $it — VPN requis sur les deux appareils" }
                                                    ?: context.getString(R.string.main_configurer_l_ip_tailscale_100_x_du_xiaomi)) {
                                                TextButton(onClick = {
                                                    clientTailscaleInput = paired.tailscale.orEmpty()
                                                    showTailscaleDialog = true
                                                }) { Text(context.getString(R.string.main_configurer)) }
                                            }
                                            SettingItem(Icons.Rounded.Public, context.getString(R.string.main_ipv6_directe),
                                                if (paired.globalIpv6 == null)
                                                    context.getString(R.string.main_aucune_ipv6_publique_enregistree)
                                                else if (paired.tailscale != null)
                                                    context.getString(R.string.main_tailscale_a_la_priorite_sur_l_ipv6_publique)
                                                else context.getString(R.string.main_ipv6_enregistree_mais_la_box_peut_bloquer_les))
                                            if (paired.tailscale == null) {
                                                OutlinedButton(onClick = {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW,
                                                        Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")))
                                                }) { Text(context.getString(R.string.main_installer_tailscale)) }
                                            }
                                        }
                                        if (paired?.globalIpv6 != null) {
                                            SettingItem(Icons.Rounded.SettingsEthernet, context.getString(R.string.main_privilegier_ipv6),
                                                context.getString(R.string.main_utiliser_l_adresse_ipv6_meme_quand_le_vivo_es)) {
                                                Switch(settings.preferDirectIpv6, onCheckedChange = {
                                                    settings.preferDirectIpv6 = it
                                                    revision++
                                                })
                                            }
                                        }
                                        SectionTitle(context.getString(R.string.main_raccourcis))
                                        SettingItem(Icons.Rounded.CheckCircle, context.getString(R.string.main_tester_la_connexion),
                                            diagnostic.ifBlank { context.getString(R.string.main_verifier_wi_fi_ipv6_tls_et_authentification) }) {
                                            TextButton(enabled = !connectionChecking, onClick = {
                                                scope.launch {
                                                    connectionChecking = true
                                                    connectionVerified = false
                                                    diagnostic = context.getString(R.string.main_diagnostic_en_cours_maximum_12_s)
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
                                                        diagnostic = context.getString(R.string.main_echec_du_diagnostic)
                                                        report(e)
                                                    } finally {
                                                        connectionChecking = false
                                                    }
                                                }
                                            }) { Text(if (connectionChecking) context.getString(R.string.main_analyse) else context.getString(R.string.main_tester)) }
                                        }
                                        SettingItem(Icons.Rounded.FolderOpen, context.getString(R.string.main_explorer_les_fichiers),
                                            context.getString(R.string.main_dossiers_du_serveur)) { TextButton(onClick = { page = 1 }) { Text(context.getString(R.string.main_ouvrir)) } }
                                        SettingItem(Icons.Rounded.Backup, context.getString(R.string.main_sauvegarde_automatique),
                                            "${settings.backupFolders.size} dossier(s) configuré(s)") {
                                            TextButton(onClick = { page = 2 }) { Text(context.getString(R.string.main_gerer)) }
                                        }
                                    }
                                }
                            }
                            1 -> if (role == "server") {
                                Column(Modifier.fillMaxSize().verticalScrollCompat()
                                    .padding(20.dp)) {
                                    SectionTitle(context.getString(R.string.main_stockage_partage))
                                    SettingItem(Icons.Rounded.Folder, context.getString(R.string.main_dossier_racine), rootUri.ifBlank { context.getString(R.string.main_non_selectionne) }) {
                                        TextButton(onClick = { chooseRoot.launch(null) }) { Text(context.getString(R.string.main_choisir)) }
                                    }
                                    Text(context.getString(R.string.main_seul_le_dossier_choisi_via_le_systeme_android),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        currentPath = currentPath.substringBeforeLast('/', "")
                                    }, enabled = currentPath.isNotBlank()) { Icon(Icons.Rounded.ArrowBack, context.getString(R.string.main_retour)) }
                                    Column(Modifier.weight(1f)) {
                                        Text(if (currentPath.isBlank()) context.getString(R.string.main_mes_fichiers) else currentPath,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${files.size} éléments", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { scope.launch { refresh() } }) {
                                        Icon(Icons.Rounded.Refresh, context.getString(R.string.main_actualiser))
                                    }
                                    IconButton(onClick = { showFolderDialog = true }) {
                                        Icon(Icons.Rounded.CreateNewFolder, context.getString(R.string.main_nouveau_dossier))
                                    }
                                }
                                OutlinedTextField(value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, context.getString(R.string.main_effacer))
                                        }
                                    }, label = { Text(context.getString(R.string.main_rechercher_dans_ce_dossier)) },
                                    shape = MaterialTheme.shapes.large)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("name" to context.getString(R.string.main_nom), "date" to context.getString(R.string.main_recents), "size" to context.getString(R.string.main_taille)).forEach { (id, label) ->
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
                                            Text(if (searchQuery.isNotBlank()) context.getString(R.string.main_aucun_resultat) else context.getString(R.string.main_dossier_vide),
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
                                                    Text(if (file.directory) context.getString(R.string.main_dossier) else readableBytes(file.size),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(onClick = {
                                                    if (file.directory) currentPath = file.path
                                                    else { pendingDownload = file; createDownload.launch(file.name) }
                                                }) { Icon(if (file.directory) Icons.Rounded.ChevronRight
                                                    else Icons.Rounded.Download, context.getString(R.string.main_ouvrir)) }
                                                var expanded by remember(file.path) { mutableStateOf(false) }
                                                Box {
                                                    IconButton(onClick = { expanded = true }) {
                                                        Icon(Icons.Rounded.MoreVert, context.getString(R.string.main_options_du_fichier))
                                                    }
                                                    DropdownMenu(expanded = expanded, onDismissRequest = {
                                                        expanded = false
                                                    }) {
                                                        DropdownMenuItem(
                                                            text = { Text(context.getString(R.string.main_renommer)) },
                                                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                                                            onClick = {
                                                                expanded = false; pendingRename = file; renameName = file.name
                                                            })
                                                        DropdownMenuItem(
                                                            text = { Text(context.getString(R.string.main_supprimer)) },
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
                                    Spacer(Modifier.width(8.dp)); Text(context.getString(R.string.main_envoyer_des_fichiers))
                                }
                            }
                            2 -> if (role == "client") {
                                Column(Modifier.fillMaxSize().verticalScrollCompat().padding(20.dp)) {
                                    SectionTitle(context.getString(R.string.main_sauvegardes_automatiques))
                                    Spacer(Modifier.height(8.dp))
                                    SettingItem(Icons.Rounded.Wifi, context.getString(R.string.main_wi_fi_uniquement),
                                        context.getString(R.string.main_desactive_pour_autoriser_les_sauvegardes_en_4)) {
                                        Switch(settings.wifiOnly, onCheckedChange = {
                                            settings.wifiOnly = it; BackupScheduler.schedule(context); revision++
                                        })
                                    }
                                    SectionTitle(context.getString(R.string.main_nom_de_l_appareil))
                                    OutlinedTextField(
                                        value = backupDeviceNameInput,
                                        onValueChange = { backupDeviceNameInput = it.take(60) },
                                        label = { Text(context.getString(R.string.main_nom_des_sauvegardes)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    TextButton(onClick = {
                                        try {
                                            settings.backupDeviceName = backupDeviceNameInput
                                            backupNotice = context.getString(R.string.main_nom_enregistre_les_prochaines_sauvegardes_uti)
                                            revision++
                                        } catch (e: Exception) { report(e) }
                                    }) { Text(context.getString(R.string.main_enregistrer)) }
                                    SectionTitle(context.getString(R.string.main_frequence))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(15L, 60L, 360L, 1440L).forEach { mins ->
                                            FilterChip(selected = settings.interval == mins,
                                                onClick = {
                                                    settings.interval = mins
                                                    BackupScheduler.schedule(context); revision++
                                                },
                                                label = { Text(when (mins) { 15L -> context.getString(R.string.main_15_min)
                                                    60L -> context.getString(R.string.main_1_h); 360L -> context.getString(R.string.main_6_h); else -> context.getString(R.string.main_24_h) }) })
                                        }
                                    }
                                    SectionTitle(context.getString(R.string.main_dossiers_surveilles), "${settings.backupFolders.size}")
                                    settings.backupFolders.forEach { uri ->
                                        val name = remember(uri) {
                                            DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name ?: "Dossier"
                                        }
                                        SettingItem(Icons.Rounded.FolderCopy, name,
                                            context.getString(R.string.main_sauvegarde_vers_backups)) {
                                            IconButton(onClick = {
                                                settings.backupFolders = settings.backupFolders - uri
                                                BackupScheduler.schedule(context); revision++
                                            }) { Icon(Icons.Rounded.Close, context.getString(R.string.main_retirer)) }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    FilledTonalButton(onClick = { addBackup.launch(null) },
                                        modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Rounded.Add, null)
                                        Spacer(Modifier.width(8.dp)); Text(context.getString(R.string.main_ajouter_un_dossier_a_sauvegarder))
                                    }
                                    OutlinedButton(
                                        enabled = !manualBackupStarting &&
                                            manualBackupInfo?.state != WorkInfo.State.RUNNING,
                                        onClick = {
                                            if (peerLink.isBlank() || settings.backupFolders.isEmpty()) {
                                                backupNotice = context.getString(R.string.main_selectionne_au_moins_un_dossier_et_connecte_l)
                                            } else {
                                                scope.launch {
                                                    manualBackupStarting = true
                                                    backupNotice = context.getString(R.string.main_verification_de_la_connexion_securisee_au_xia)
                                                    try {
                                                        // Distinguish a reachable Xiaomi from a queued request:
                                                        // this ping uses exactly the same peer/Tailscale route as the Worker.
                                                        withContext(Dispatchers.IO) {
                                                            CloudixClient(Peer.parse(peerLink), context,
                                                                connectTimeoutMs = 6_000,
                                                                readTimeoutMs = 10_000).ping()
                                                        }
                                                        manualBackupId = BackupScheduler.runNow(context).toString()
                                                        manualBackupInfo = null
                                                        backupError = ""
                                                        backupNotice = if (settings.wifiOnly)
                                                            context.getString(R.string.main_serveur_joignable_la_sauvegarde_attend_un_res)
                                                        else context.getString(R.string.main_serveur_joignable_sauvegarde_demandee_sur_wi_)
                                                    } catch (e: Exception) {
                                                        backupNotice =
                                                            "Sauvegarde non lancée : ${e.message ?: "connexion impossible"}"
                                                    } finally {
                                                        manualBackupStarting = false
                                                    }
                                                }
                                            }
                                        }, modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Sync, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (manualBackupStarting) context.getString(R.string.main_connexion_2) else context.getString(R.string.main_sauvegarder_maintenant))
                                    }
                                    val work = manualBackupInfo
                                    val working = work?.state == WorkInfo.State.RUNNING
                                    val queued = work?.state == WorkInfo.State.ENQUEUED ||
                                        work?.state == WorkInfo.State.BLOCKED
                                    val savedNow = if (work?.state == WorkInfo.State.SUCCEEDED)
                                        work.outputData.getInt("uploaded", backupItems)
                                    else work?.progress?.getInt("uploaded", backupItems) ?: backupItems
                                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(context.getString(R.string.main_etat_de_la_sauvegarde),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold)
                                            Text(when {
                                                manualBackupStarting -> context.getString(R.string.main_connexion_au_serveur)
                                                working -> work?.progress?.getString("phase")
                                                    ?: context.getString(R.string.main_sauvegarde_en_cours)
                                                queued && backupError.isNotBlank() ->
                                                    "Nouvelle tentative : $backupError"
                                                queued -> if (settings.wifiOnly)
                                                    context.getString(R.string.main_en_attente_d_un_reseau_non_facture_ou_d_andro)
                                                else context.getString(R.string.main_en_attente_du_lancement_par_android_wi_fi_4g_)
                                                work?.state == WorkInfo.State.SUCCEEDED ->
                                                    if (savedNow == 0) context.getString(R.string.main_termine_les_fichiers_sont_deja_sauvegardes)
                                                    else "Terminé : $savedNow fichier(s) transféré(s) au Xiaomi."
                                                work?.state == WorkInfo.State.FAILED ->
                                                    "Sauvegarde échouée : ${work.outputData.getString("error")
                                                        ?: backupError.ifBlank { context.getString(R.string.main_consulte_le_diagnostic) }}"
                                                work?.state == WorkInfo.State.CANCELLED ->
                                                    context.getString(R.string.main_sauvegarde_interrompue)
                                                backupNotice.isNotBlank() -> backupNotice
                                                else -> context.getString(R.string.main_aucune_sauvegarde_manuelle_recente)
                                            }, style = MaterialTheme.typography.bodyMedium)
                                            if (working || manualBackupStarting) {
                                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                            }
                                            if (working && savedNow > 0) {
                                                Text("$savedNow fichier(s) transféré(s)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (backupNotice.isNotBlank() && work?.state != WorkInfo.State.SUCCEEDED) {
                                                Text(backupNotice,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (backupError.isNotBlank() &&
                                        manualBackupInfo?.state != WorkInfo.State.FAILED) {
                                        Text(backupError,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (backupLastAt > 0L) {
                                        SettingItem(Icons.Rounded.History, context.getString(R.string.main_dernier_passage),
                                            java.text.DateFormat.getDateTimeInstance().format(
                                                java.util.Date(backupLastAt)))
                                    }

                                }
                            }
                            3 -> AppearancePage(settings) { revision++ }
                            4 -> Column(Modifier.fillMaxSize().verticalScrollCompat().padding(20.dp)) {
                                SectionTitle(context.getString(R.string.main_configuration))
                                SettingItem(Icons.Rounded.Security, context.getString(R.string.main_chiffrement_et_appairage),
                                    context.getString(R.string.main_tls_avec_certificat_epingle_et_secret_aleatoi))
                                if (role == "server") {
                                    SettingItem(Icons.Rounded.PowerSettingsNew,
                                        context.getString(R.string.main_demarrer_apres_redemarrage),
                                        context.getString(R.string.main_lancer_automatiquement_le_serveur_au_demarrag)) {
                                        Switch(checked = settings.serverAutoStart,
                                            onCheckedChange = {
                                                settings.serverAutoStart = it; revision++
                                            })
                                    }
                                    Text(context.getString(R.string.main_hyperos_peut_bloquer_les_demarrages_automatiq),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = {
                                        settings.regenerateSecret()
                                        message = context.getString(R.string.main_ancien_appairage_revoque_partage_un_nouveau_q)
                                        revision++
                                    }) { Text(context.getString(R.string.main_revoquer_tous_les_clients_appaires)) }
                                } else {
                                    OutlinedButton(onClick = {
                                        settings.peer = ""; peerLink = ""; currentPath = ""; files = emptyList()
                                    }) { Text(context.getString(R.string.main_deconnecter_ce_serveur)) }
                                }
                                UpdatesPanel()
                                SectionTitle(context.getString(R.string.main_appareil))
                                SettingItem(Icons.Rounded.PhoneAndroid, context.getString(R.string.main_mode_actif),
                                    if (role == "server") context.getString(R.string.main_serveur) else context.getString(R.string.main_client))
                                OutlinedButton(onClick = {
                                    if (role == "server") context.startService(
                                        Intent(context, CloudixService::class.java).setAction(context.getString(R.string.main_stop)))
                                    settings.role = ""; role = ""; page = 0
                                }) { Text(context.getString(R.string.main_changer_le_role_de_ce_telephone)) }
                                Spacer(Modifier.height(16.dp))
                                Text("Cloudix ${BuildConfig.VERSION_NAME} · VPN Tailscale · Signature Android",
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
                title = { Text(context.getString(R.string.main_connexion_tailscale)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(context.getString(R.string.main_entre_l_adresse_100_x_affichee_dans_tailscale))
                        OutlinedTextField(
                            value = clientTailscaleInput,
                            onValueChange = { clientTailscaleInput = it.trim() },
                            label = { Text(context.getString(R.string.main_ip_tailscale_du_xiaomi)) },
                            placeholder = { Text("100.100.100.100") },
                            singleLine = true
                        )
                        Text(context.getString(R.string.main_la_cle_d_appairage_et_la_verification_tls_exi),
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
                            message = context.getString(R.string.main_adresse_tailscale_enregistree_active_tailscal)
                        } catch (e: Exception) { report(e) }
                    }) { Text(context.getString(R.string.main_enregistrer)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTailscaleDialog = false }) { Text(context.getString(R.string.main_annuler)) }
                }
            )
        }
        if (message.isNotEmpty()) {
            AlertDialog(onDismissRequest = { message = "" }, confirmButton = {
                TextButton(onClick = { message = "" }) { Text(context.getString(R.string.main_ok)) }
            }, title = { Text("Cloudix") }, text = { Text(message) })
        }
        pendingRename?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingRename = null },
                title = { Text(context.getString(R.string.main_renommer)) },
                text = { OutlinedTextField(renameName, { renameName = it },
                    label = { Text(context.getString(R.string.main_nouveau_nom)) }, singleLine = true) },
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
                    }, enabled = renameName.isNotBlank()) { Text(context.getString(R.string.main_enregistrer)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRename = null }) { Text(context.getString(R.string.main_annuler)) }
                }
            )
        }
        pendingDelete?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Supprimer ${file.name} ?") },
                text = { Text(context.getString(R.string.main_cette_suppression_est_definitive_sur_le_serve)) },
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
                    }) { Text(context.getString(R.string.main_supprimer)) }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(context.getString(R.string.main_annuler)) } }
            )
        }
        if (showFolderDialog) {
            AlertDialog(onDismissRequest = { showFolderDialog = false },
                title = { Text(context.getString(R.string.main_nouveau_dossier)) },
                text = { OutlinedTextField(newFolder, { newFolder = it }, label = { Text(context.getString(R.string.main_nom)) }) },
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
                    }) { Text(context.getString(R.string.main_creer)) }
                },
                dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text(context.getString(R.string.main_annuler)) } }
            )
        }
    }
}

@Composable
private fun PairInput(link: String, onLink: (String) -> Unit, onScan: () -> Unit, onConnect: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(context.getString(R.string.main_associer_ton_xiaomi))
            OutlinedTextField(link, onLink, modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.main_lien_cloudix)) },
                placeholder = { Text("cloudix://pair?...") }, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.main_scanner))
                }
                Button(onClick = onConnect, enabled = link.isNotBlank()) { Text(context.getString(R.string.main_se_connecter)) }
            }
        }
    }
}
@Composable
private fun PairingCard(link: String, onShare: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(context.getString(R.string.main_appairer_un_appareil), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(context.getString(R.string.main_le_qr_code_est_confidentiel_il_donne_acces_a_),
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
                Image(bitmap.asImageBitmap(), context.getString(R.string.main_code_qr_d_appairage),
                    modifier = Modifier.size(220.dp))
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { reveal = !reveal }) {
                Text(if (reveal) context.getString(R.string.main_masquer_le_qr) else context.getString(R.string.main_afficher_le_qr))
            }
            TextButton(onClick = onShare) { Icon(Icons.Rounded.Share, null)
                Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.main_partager_le_lien)) }
        }
    }
}
private fun readableBytes(n: Long): String = when {
    n < 1024 -> "$n o"
    n < 1024 * 1024 -> "${n / 1024} Ko"
    n < 1024L * 1024L * 1024L -> "${n / (1024 * 1024)} Mo"
    else -> "${"%.1fcontext.getString(R.string.main_format_n_todouble_1024_0_1024_1024_go)
}
