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
    val value: String?,
    val url: String?,
    val note: String?,
)

data class SupportInfo(val message: String, val methods: List<SupportMethod>)

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
        SupportInfo(
            message = json.optString("message"),
            methods = (0 until (methods?.length() ?: 0)).map { i ->
                val m = methods!!.getJSONObject(i)
                SupportMethod(
                    name = m.getString("name"),
                    value = m.optString("value").ifBlank { null },
                    url = m.optString("url").ifBlank { null },
                    note = m.optString("note").ifBlank { null },
                )
            },
        )
    }
}
