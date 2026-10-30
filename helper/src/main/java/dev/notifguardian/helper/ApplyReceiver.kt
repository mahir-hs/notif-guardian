package dev.notifguardian.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Adds the requested packages to HyperOS's MILLET_NO_RESTRICT_APP list (apps its freezer leaves
 * alone). Works only because this app targets API 22. Replies through the ordered-broadcast result.
 */
class ApplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wanted = intent.getStringArrayExtra(EXTRA_PACKAGES).orEmpty().toList()
        val result = try {
            val cr = context.contentResolver
            val current = Settings.System.getString(cr, KEY)
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                .orEmpty()
            val missing = wanted.filter { it !in current }
            if (missing.isEmpty()) {
                "ok: nothing to add"
            } else {
                Settings.System.putString(cr, KEY, (current + missing).joinToString(", "))
                val after = Settings.System.getString(cr, KEY).orEmpty()
                if (missing.all { it in after }) "ok: added ${missing.joinToString()}" else "error: write did not stick"
            }
        } catch (e: Exception) {
            "error: ${e.javaClass.simpleName}: ${e.message}"
        }
        Log.i("GuardianHelper", result)
        setResultData(result)
    }

    companion object {
        const val KEY = "MILLET_NO_RESTRICT_APP"
        const val EXTRA_PACKAGES = "packages"
    }
}
