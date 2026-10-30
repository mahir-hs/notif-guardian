package dev.notifguardian.ui

import dev.notifguardian.status.AppStatus
import dev.notifguardian.status.Tri
import dev.notifguardian.xiaomi.XiaomiSettings.Screen

enum class CheckState { CLEAR, BROKEN, UNKNOWN }

/** One link in an app's notification delivery path, with plain-language copy and its fix. */
enum class CheckKind(
    val clear: String,
    val broken: String,
    val unknown: String,
    val why: String,
    val fixLabel: String,
    /** null = the fix is opening the app itself */
    val fixScreen: Screen?,
) {
    RUNNING(
        clear = "Running",
        broken = "Stopped by the system",
        unknown = "Running state unknown",
        why = "A stopped app gets no notifications until you open it again.",
        fixLabel = "Open app",
        fixScreen = null,
    ),
    AUTOSTART(
        clear = "Autostart on",
        broken = "Autostart off",
        unknown = "Autostart can't be checked",
        why = "Without autostart, HyperOS won't let it start in the background to receive a message.",
        fixLabel = "Turn on autostart",
        fixScreen = Screen.AUTOSTART,
    ),
    BATTERY(
        clear = "No battery restrictions",
        broken = "Battery saver limits it",
        unknown = "Battery state unknown",
        why = "Battery saver delays its background work. Set it to \"No restrictions\".",
        fixLabel = "Open battery saver",
        fixScreen = Screen.BATTERY_SAVER,
    ),
    BACKGROUND(
        clear = "Background activity allowed",
        broken = "Background activity blocked",
        unknown = "Background activity can't be checked",
        why = "Android is blocking it from running in the background.",
        fixLabel = "Open app info",
        fixScreen = Screen.APP_INFO,
    ),
    FREEZE(
        clear = "Not frozen when the screen is off",
        broken = "Frozen when the screen is off",
        unknown = "Freezing can't be checked",
        why = "HyperOS pauses it while the screen is off, so messages wait until you unlock. " +
            "Setting its battery saver to \"No restrictions\" should exempt it.",
        fixLabel = "Open battery saver",
        fixScreen = Screen.BATTERY_SAVER,
    ),
    ;

    fun text(state: CheckState) = when (state) {
        CheckState.CLEAR -> clear
        CheckState.BROKEN -> broken
        CheckState.UNKNOWN -> unknown
    }
}

data class Check(val kind: CheckKind, val state: CheckState)

fun AppStatus.checks(): List<Check> = listOf(
    Check(CheckKind.RUNNING, if (stopped) CheckState.BROKEN else CheckState.CLEAR),
    Check(CheckKind.AUTOSTART, autostart.state()),
    Check(CheckKind.BATTERY, if (batteryUnrestricted) CheckState.CLEAR else CheckState.BROKEN),
    Check(CheckKind.BACKGROUND, backgroundAllowed.state()),
    Check(CheckKind.FREEZE, milletExempt.state()),
)

fun AppStatus.problems(): List<Check> = checks().filter { it.state == CheckState.BROKEN }

private fun Tri.state() = when (this) {
    Tri.YES -> CheckState.CLEAR
    Tri.NO -> CheckState.BROKEN
    Tri.UNKNOWN -> CheckState.UNKNOWN
}
