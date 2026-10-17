package com.cloudix.app

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    fun schedule(context: Context) {
        val settings = CloudixSettings(context)
        if (settings.backupFolders.isEmpty() || settings.peer.isBlank()) {
            cancel(context)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(
            if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        ).build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            settings.interval.coerceAtLeast(15), TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "cloudix_backup", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun runNow(context: Context) {
        val settings = CloudixSettings(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(
            if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "cloudix_manual_backup", androidx.work.ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(constraints).build()
        )
    }
    fun cancel(context: Context) =
        WorkManager.getInstance(context).cancelUniqueWork("cloudix_backup")
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = CloudixSettings(applicationContext)
        if (settings.peer.isBlank() || settings.backupFolders.isEmpty()) return Result.success()
        return try {
            settings.lastBackupError = ""
            val client = CloudixClient(Peer.parse(settings.peer), applicationContext)
            val state = settings.backupState()
            val device = safeName(Build.MODEL.ifBlank { "Android" })
            ensure(client, "", "Backups")
            ensure(client, "Backups", device)
            var uploaded = 0
            var inaccessibleDepth = false
            var skippedLargeOrUnknown = 0
            for (str in settings.backupFolders) {
                if (isStopped) return Result.retry()
                val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(str)) ?: continue
                if (!tree.isDirectory) continue
                val folder = safeName(tree.name ?: "Dossier")
                ensure(client, "Backups/$device", folder)
                val dest = "Backups/$device/$folder"
                val pending = ArrayDeque<Pair<DocumentFile, Pair<String, Int>>>()
                pending.addLast(tree to (dest to 0))
                while (pending.isNotEmpty()) {
                    if (isStopped) return Result.retry()
                    val (dir, node) = pending.removeFirst()
                    val (remoteDir, depth) = node
                    if (depth >= 28) {
                        inaccessibleDepth = true
                        continue
                    }
                    for (item in dir.listFiles()) {
                        val name = safeName(item.name ?: continue)
                        if (item.isDirectory) {
                            ensure(client, remoteDir, name)
                            pending.addLast(item to ("$remoteDir/$name" to depth + 1))
                        } else if (item.isFile) {
                            val size = item.length()
                            if (size < 0 || size > 4_294_967_296L) {
                                skippedLargeOrUnknown++
                                continue
                            }
                            val id = item.uri.toString()
                            val stamp = "$size:${item.lastModified()}"
                            if (state.optString(id) == stamp) continue
                            // A batch is limited by UPLOADS, not items scanned.
                            // Persist every completed file and retry for the next batch,
                            // so folders with >4000 existing files are never silently skipped.
                            if (uploaded >= 4000) {
                                settings.lastBackupItems = uploaded
                                settings.lastBackupError =
                                    "Sauvegarde partielle : $uploaded fichiers transférés. Reprise prévue automatiquement."
                                return Result.retry()
                            }
                            val input = applicationContext.contentResolver.openInputStream(item.uri) ?: continue
                            client.upload(remoteDir, name, size, input)
                            uploaded++
                            state.put(id, stamp)
                            settings.saveBackupState(state)
                        }
                    }
                }
            }
            settings.lastBackupItems = uploaded
            if (skippedLargeOrUnknown > 0 || inaccessibleDepth) {
                settings.lastBackupError =
                    "Sauvegarde incomplète : $skippedLargeOrUnknown fichiers trop volumineux ou de taille inconnue" +
                    (if (inaccessibleDepth) ", et certains sous-dossiers sont trop profonds." else ".")
                android.util.Log.w("CloudixBackup",
                    settings.lastBackupError)
                Result.failure()
            } else {
                settings.lastBackupAt = System.currentTimeMillis()
                settings.lastBackupError = ""
                Result.success()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            settings.lastBackupError = "Échec temporaire de sauvegarde : ${e.message ?: "connexion indisponible"}"
            Result.retry()
        }
    }
    private fun safeName(name: String): String = CloudixBackupNames.safeName(name)
    private fun ensure(client: CloudixClient, directory: String, name: String) {
        try {
            client.mkdir(directory, name)
        } catch (e: Exception) {
            if (!e.message.orEmpty().contains("409")) throw e
        }
    }
}
