package dev.notifguardian

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TargetApp(val pkg: String, val label: String)

/** The user-chosen set of apps we protect, persisted in SharedPreferences. */
object ProtectedApps {
    /** Carries FCM push for almost every app, so it is always protected. */
    const val GMS = "com.google.android.gms"

    /** Shown first in the picker, and protected by default on first launch if installed. */
    val SUGGESTED = listOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.google.android.gm", "com.facebook.orca", "com.instagram.android", "com.viber.voip",
        "com.imo.android.imoim", "com.discord", "org.thoughtcrime.securesms", "com.microsoft.teams",
        "com.Slack", "com.skype.raider", "jp.naver.line.android", "com.google.android.apps.messaging",
    )
    private val DEFAULTS = listOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.google.android.gm", "com.facebook.orca",
    )

    private const val PREFS = "protected_apps"
    private const val KEY = "packages"

    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY, null)
        val set = stored ?: DEFAULTS.filter { isInstalled(context, it) }.toSet().also {
            prefs.edit().putStringSet(KEY, it).apply()
        }
        _packages.value = set + GMS
    }

    fun isProtected(pkg: String) = pkg in _packages.value

    fun add(context: Context, pkg: String) = update(context, _packages.value + pkg)

    fun remove(context: Context, pkg: String) {
        if (pkg != GMS) update(context, _packages.value - pkg)
    }

    /** Installed protected apps, Google Play services first, then by name. */
    fun list(context: Context): List<TargetApp> = _packages.value
        .filter { isInstalled(context, it) }
        .map { TargetApp(it, label(context, it)) }
        .sortedWith(compareBy({ it.pkg != GMS }, { it.label.lowercase() }))

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun update(context: Context, set: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set - GMS).apply()
        _packages.value = set + GMS
    }
}
