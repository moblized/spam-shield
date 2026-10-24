package app.spamshield.data

import app.spamshield.core.CommunityHit
import app.spamshield.core.PhoneNumbers
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject

/** Community spam lookups against https://phoneblock.net (API: /phoneblock/api/num/{number}). */
object PhoneBlockClient {

    suspend fun lookup(number: String, token: String?): CommunityHit? = runInterruptible(Dispatchers.IO) {
        val international = when {
            PhoneNumbers.isNanp(number) -> "+1$number"
            PhoneNumbers.isInternational(number) -> number
            else -> return@runInterruptible null
        }
        val url = URL(
            "https://phoneblock.net/phoneblock/api/num/" +
                URLEncoder.encode(international, "UTF-8") + "?format=json"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 2_000
        conn.readTimeout = 2_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "SpamShield/1.0 (Android)")
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (conn.responseCode != 200) return@runInterruptible null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val rating = json.optString("rating").ifBlank { null }
            if (json.optBoolean("archived") || json.optBoolean("whiteListed") || rating == "A_LEGITIMATE") {
                return@runInterruptible null
            }
            val votes = json.optInt("votes", 0)
            if (votes <= 0) null else CommunityHit(votes, rating, json.optString("label").ifBlank { null })
        } finally {
            conn.disconnect()
        }
    }
}
