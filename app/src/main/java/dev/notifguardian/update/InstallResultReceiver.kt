package dev.notifguardian.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the PackageInstaller session result: confirm screen needed, success, or failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallResult(context, intent)
}
