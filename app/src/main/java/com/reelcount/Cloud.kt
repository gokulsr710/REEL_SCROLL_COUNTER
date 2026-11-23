package com.reelcount

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Minimal Firebase client over plain HTTPS (no SDK):
 * anonymous sign-in + Realtime Database reads/writes.
 * Used only by Challenge mode; nothing here runs unless the user starts or joins a challenge.
 */
object Cloud {
    private const val K_TOKEN = "fb_token"
    private const val K_REFRESH = "fb_refresh"
    private const val K_UID = "fb_uid"
    private const val K_EXP = "fb_exp"

    private val apiKey get() = BuildConfig.FIREBASE_API_KEY
    private val dbUrl get() = BuildConfig.FIREBASE_DB_URL.trimEnd('/')

    /** True when this build was made with a Firebase project connected. */
    val configured: Boolean get() = apiKey.isNotBlank() && dbUrl.isNotBlank()

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Run network work off the main thread; deliver the result on the main thread. */
    fun <T> run(work: () -> T, done: (Result<T>) -> Unit = {}) {
        io.execute {
            val r = runCatching(work)
            main.post { done(r) }
        }
    }

    class CloudException(val code: Int, body: String) : IOException("HTTP $code: $body")

    private data class Session(val idToken: String, val refresh: String, val expiresInSec: Long, val uid: String)

    fun uid(c: Context): String {
        token(c)
        return Prefs.get(c).getString(K_UID, "") ?: ""
    }

    fun uidOrNull(c: Context): String? = Prefs.get(c).getString(K_UID, null)

    /** A valid ID token, signing in anonymously or refreshing as needed. */
    @Synchronized
    fun token(c: Context): String {
        val p = Prefs.get(c)
        val cached = p.getString(K_TOKEN, null)
        if (cached != null && System.currentTimeMillis() < p.getLong(K_EXP, 0) - 60_000) return cached

        val s = p.getString(K_REFRESH, null)?.let { refresh ->
            try {
                val o = JSONObject(http(
                    "POST", "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                    "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"),
                    "application/x-www-form-urlencoded",
                ))
                Session(o.getString("id_token"), o.getString("refresh_token"),
                    o.getString("expires_in").toLong(), o.getString("user_id"))
            } catch (_: CloudException) { null }
        } ?: run {
            val o = JSONObject(http(
                "POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
                JSONObject().put("returnSecureToken", true).toString(),
            ))
            Session(o.getString("idToken"), o.getString("refreshToken"),
                o.getString("expiresIn").toLong(), o.getString("localId"))
        }

        p.edit()
            .putString(K_TOKEN, s.idToken)
            .putString(K_REFRESH, s.refresh)
            .putString(K_UID, s.uid)
            .putLong(K_EXP, System.currentTimeMillis() + s.expiresInSec * 1000)
            .commit()
        return s.idToken
    }

    /** GET a database path. Returns null when nothing is stored there. */
    fun get(c: Context, path: String): String? {
        val body = http("GET", "$dbUrl/$path.json?auth=${token(c)}")
        return if (body == "null") null else body
    }

    fun put(c: Context, path: String, json: String) {
        http("PUT", "$dbUrl/$path.json?auth=${token(c)}", json)
    }

    fun patch(c: Context, path: String, json: String) {
        http("PATCH", "$dbUrl/$path.json?auth=${token(c)}", json)
    }

    private fun http(method: String, url: String, body: String? = null, type: String = "application/json"): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (method == "PATCH") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", type)
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code >= 400) throw CloudException(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }
}
