package com.cloudix.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class CloudixBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val settings = CloudixSettings(context)
            if (settings.role == "server" && settings.serverAutoStart &&
                settings.rootUri.isNotBlank()) {
                ContextCompat.startForegroundService(context,
                    Intent(context, CloudixService::class.java))
            }
        } catch (_: Exception) {
            CloudixStatus.error.value = context.getString(R.string.server_start_failed)
        }
    }
}
