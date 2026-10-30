package dev.notifguardian.monitor

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Append-only JSON-lines record of what we observed (notification posted, app stopped, ...).
 * Feeds both the in-app stats and the diagnostics export.
 */
object EventStore {
    const val POSTED = "posted"
    const val STOPPED = "stopped"
    const val REVIVED = "revived"

    private const val MAX_BYTES = 1024 * 1024
    private const val KEEP_LINES = 3000
    private val lock = Any()

    fun file(context: Context) = File(context.filesDir, "events.jsonl")

    fun add(
        context: Context,
        type: String,
        pkg: String,
        delayMs: Long? = null,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        val json = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("type", type)
            .put("pkg", pkg)
        if (delayMs != null) json.put("delayMs", delayMs)
        extra.forEach { (k, v) -> json.put(k, v) }
        synchronized(lock) {
            val f = file(context)
            f.appendText(json.toString() + "\n")
            if (f.length() > MAX_BYTES) f.writeText(f.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
        }
    }

    /** Newest last. */
    fun read(context: Context, sinceMs: Long = 0): List<JSONObject> = synchronized(lock) {
        val f = file(context)
        if (!f.exists()) return emptyList()
        f.readLines().mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()?.takeIf { it.optLong("ts") >= sinceMs }
        }
    }
}
