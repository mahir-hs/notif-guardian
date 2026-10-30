package dev.notifguardian.support

import dev.notifguardian.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One way to donate, e.g. bKash: a number to send money to. [url] is set for link-based methods
 * (Patreon, Buy Me a Coffee); [value] for number-based ones.
 */
data class SupportMethod(
    val name: String,
    /** ISO country code (e.g. "BD") for local-only methods; null for ones that work anywhere. */
    val region: String?,
    val value: String?,
    val url: String?,
    val note: String?,
)

data class Region(val code: String, val name: String, val about: String?) {
    /** Flag emoji built from the country code's regional-indicator letters. */
    val flag: String
        get() = code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

data class SupportInfo(
    val message: String,
    val regions: Map<String, Region>,
    val methods: List<SupportMethod>,
    /** Shown to people the listed regions don't cover. */
    val elsewhere: String?,
)

/**
 * Donation details live in docs/support.json on the website, so they can change without an app
 * update. Fetched only when the user opens the support section.
 */
object Support {
    val url: String? = BuildConfig.UPDATE_REPO.takeIf { it.isNotBlank() }?.let { repo ->
        val (owner, name) = repo.split('/', limit = 2)
        "https://$owner.github.io/$name/support.json"
    }

    suspend fun fetch(): SupportInfo = withContext(Dispatchers.IO) {
        val conn = URL(url ?: error("no support page configured")).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "NotifGuardian/${BuildConfig.VERSION_NAME}")
        if (conn.responseCode !in 200..299) error("the support page answered ${conn.responseCode}")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val methods = json.optJSONArray("methods")
        val regions = json.optJSONObject("regions")
        SupportInfo(
            message = json.optString("message"),
            regions = regions?.keys()?.asSequence()?.associateWith { code ->
                val r = regions.getJSONObject(code)
                Region(code, r.getString("name"), r.optString("about").ifBlank { null })
            }.orEmpty(),
            elsewhere = json.optString("elsewhere").ifBlank { null },
            methods = (0 until (methods?.length() ?: 0)).map { i ->
                val m = methods!!.getJSONObject(i)
                SupportMethod(
                    name = m.getString("name"),
                    region = m.optString("region").ifBlank { null },
                    value = m.optString("value").ifBlank { null },
                    url = m.optString("url").ifBlank { null },
                    note = m.optString("note").ifBlank { null },
                )
            },
        )
    }
}
