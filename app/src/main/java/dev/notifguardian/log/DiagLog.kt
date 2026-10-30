package dev.notifguardian.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent app log. Everything goes to logcat and to files/logs/app.log (rotated at 512 KB,
 * one backup kept) so it survives process death and can be exported with the diagnostics zip.
 * Never log notification text: only package names, ids, timings and flags.
 */
object DiagLog {
    private const val MAX_BYTES = 512 * 1024
    private val lock = Any()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "logs").apply { mkdirs() }
    }

    fun i(tag: String, msg: String) = write(Log.INFO, "I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write(Log.WARN, "W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write(Log.ERROR, "E", tag, msg, t)

    fun logFiles(): List<File> {
        val d = dir ?: return emptyList()
        return listOf(File(d, "app.log.1"), File(d, "app.log")).filter { it.exists() }
    }

    private fun write(priority: Int, level: String, tag: String, msg: String, t: Throwable?) {
        val full = if (t != null) msg + "\n" + Log.getStackTraceString(t) else msg
        Log.println(priority, "NG/$tag", full)
        val d = dir ?: return
        synchronized(lock) {
            try {
                val file = File(d, "app.log")
                if (file.length() > MAX_BYTES) file.renameTo(File(d, "app.log.1"))
                file.appendText("${fmt.format(Date())} $level/$tag: $full\n")
            } catch (_: Exception) {
                // Logging must never crash the app
            }
        }
    }
}
