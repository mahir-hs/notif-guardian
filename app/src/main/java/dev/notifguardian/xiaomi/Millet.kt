package dev.notifguardian.xiaomi

import android.content.Context
import android.provider.Settings
import dev.notifguardian.log.DiagLog
import dev.notifguardian.status.Tri

/**
 * Millet is HyperOS's own process freezer (separate from Android's cached-apps freezer). It
 * cgroup-freezes background apps when the screen goes off, ignoring Doze / battery whitelists.
 * Apps listed in Settings.System MILLET_NO_RESTRICT_APP are left alone. If Google Play services
 * is missing from it, FCM is frozen with the screen off and every app's push stalls.
 */
object Millet {
    const val KEY = "MILLET_NO_RESTRICT_APP"

    /** null when the list can't be read (not HyperOS, or blocked). */
    fun noRestrictList(context: Context): List<String>? = try {
        Settings.System.getString(context.contentResolver, KEY)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
    } catch (e: Exception) {
        DiagLog.w("Millet", "can't read $KEY: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    fun isExempt(context: Context, pkg: String): Tri {
        val list = noRestrictList(context) ?: return Tri.UNKNOWN
        return if (pkg in list) Tri.YES else Tri.NO
    }
}
