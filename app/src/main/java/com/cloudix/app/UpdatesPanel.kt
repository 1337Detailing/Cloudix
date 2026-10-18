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
    var state by remember { mutableStateOf(context.getString(R.string.update_les_nouvelles_builds_github_sont_publiees_aut)) }
    var candidate by remember { mutableStateOf<CloudixUpdateInfo?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var missingChannel by remember { mutableStateOf(false) }
    SectionTitle(context.getString(R.string.update_mises_a_jour), context.getString(R.string.update_canal_debug))
    SettingItem(Icons.Rounded.SystemUpdateAlt,
        context.getString(R.string.update_version_installee), "Build ${CloudixUpdater.currentVersionCode(context)}")
    Text(state, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (progress in 0..99) {
        LinearProgressIndicator(progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth())
        Text(context.getString(R.string.update_download_progress, progress), style = MaterialTheme.typography.labelMedium)
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
                    state = context.getString(R.string.update_new_version, update.title, update.versionCode)
                } else state = context.getString(R.string.update_aucune_build_plus_recente)
            } catch (e: Exception) {
                missingChannel = e.message?.contains("404") == true
                state = e.message ?: context.getString(R.string.update_verification_impossible)
            }
            finally { checking = false }
        }
    }) {
        Icon(Icons.Rounded.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text(context.getString(R.string.update_rechercher_les_builds_github))
    }
    if (missingChannel) {
        OutlinedButton(onClick = {
            uriHandler.openUri("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/actions/workflows/android.yml")
        }) {
            Icon(Icons.Rounded.Info, null)
            Spacer(Modifier.width(8.dp))
            Text(context.getString(R.string.update_ouvrir_les_builds_github))
        }
        Text(
            context.getString(R.string.update_la_release_de_mise_a_jour_n_est_pas_encore_di),
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
                        state = context.getString(R.string.update_apk_verifiee_sha_256_et_certificat_android_co)
                    } catch (e: Exception) {
                        state = e.message ?: context.getString(R.string.update_telechargement_impossible)
                        progress = -1
                    } finally { checking = false }
                }
            }) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.update_telecharger_et_verifier))
            }
        } else {
            Button(onClick = {
                try {
                    val started = CloudixUpdater.install(context, downloaded!!)
                    if (!started) state = context.getString(R.string.update_autorise_les_apk_provenant_de_cloudix_puis_re)
                } catch (e: Exception) { state = e.message ?: context.getString(R.string.update_installation_impossible) }
            }) {
                Icon(Icons.Rounded.InstallMobile, null)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.update_installer_la_mise_a_jour))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
}
