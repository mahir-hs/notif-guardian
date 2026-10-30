package dev.notifguardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.notifguardian.log.DiagLog
import dev.notifguardian.update.Updater

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DiagLog.i("Boot", "received ${intent.action}")
        KeepAliveService.start(context, intent.action ?: "boot")
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Updater.consumeCompletedUpdate(context)?.let { Notifier.updated(context, it) }
        }
    }
}
