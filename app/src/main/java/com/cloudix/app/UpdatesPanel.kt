package com.cloudix.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UpdatesPanel() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("Les builds debug sont distribuées via GitHub Releases après signature permanente.") }
    var candidate by remember { mutableStateOf<CloudixUpdateInfo?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var missingChannel by remember { mutableStateOf(false) }
    SectionTitle("Mises à jour", "Canal debug")
    SettingItem(Icons.Rounded.SystemUpdateAlt,
        "Version installée", "Build ${CloudixUpdater.currentVersionCode(context)}")
    Text(state, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (progress in 0..99) {
        LinearProgressIndicator(progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth())
        Text("Téléchargement : $progress %", style = MaterialTheme.typography.labelMedium)
    }
    FilledTonalButton(enabled = !checking, onClick = {
        scope.launch {
            checking = true
            candidate = null
            downloaded = null
            missingChannel = false
            try {
                val update = withContext(Dispatchers.IO) { CloudixUpdater.check() }
                if (update.versionCode > CloudixUpdater.currentVersionCode(context)) {
                    candidate = update
                    state = "Nouvelle build : ${update.title} (n° ${update.versionCode})."
                } else state = "Aucune build plus récente."
            } catch (e: Exception) {
                missingChannel = e.message?.contains("404") == true
                state = e.message ?: "Vérification impossible."
            }
            finally { checking = false }
        }
    }) {
        Icon(Icons.Rounded.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text("Rechercher les builds GitHub")
    }
    if (missingChannel) {
        OutlinedButton(onClick = {
            uriHandler.openUri("https://github.com/1337Detailing/Cloudix/blob/main/SIGNING.md")
        }) {
            Icon(Icons.Rounded.Info, null)
            Spacer(Modifier.width(8.dp))
            Text("Configurer la signature permanente")
        }
        Text(
            "La compilation GitHub peut être verte sans APK publiée. Une fois les secrets de signature ajoutés, une nouvelle build publiera la Release et les mises à jour fonctionneront sans désinstaller l'application.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    candidate?.let { info ->
        Spacer(Modifier.height(8.dp))
        if (downloaded == null) {
            Button(enabled = !checking, onClick = {
                scope.launch {
                    checking = true
                    progress = 0
                    try {
                        downloaded = withContext(Dispatchers.IO) {
                            CloudixUpdater.download(context, info) { progress = it }
                        }
                        state = "APK vérifiée : SHA-256 et certificat Android conformes."
                    } catch (e: Exception) {
                        state = e.message ?: "Téléchargement impossible"
                        progress = -1
                    } finally { checking = false }
                }
            }) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Télécharger et vérifier")
            }
        } else {
            Button(onClick = {
                try {
                    val started = CloudixUpdater.install(context, downloaded!!)
                    if (!started) state = "Autorise les APK provenant de Cloudix, puis reviens toucher Installer."
                } catch (e: Exception) { state = e.message ?: "Installation impossible" }
            }) {
                Icon(Icons.Rounded.InstallMobile, null)
                Spacer(Modifier.width(8.dp))
                Text("Installer la mise à jour")
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
}
