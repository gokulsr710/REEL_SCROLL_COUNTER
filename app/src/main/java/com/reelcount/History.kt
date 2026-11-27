package com.reelcount

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** One day of stats. */
data class DayStats(
    val date: LocalDate,
    val count: Int,
    val seconds: Long,
    val apps: Map<String, Pair<Int, Long>>,   // package -> (reels, seconds)
    val hours: IntArray,                     // reels per hour of day, 0..23
    val screen: Long = 0,                    // seconds the phone was unlocked and in use
)

/**
 * Today's detail lives in individual keys (cheap to bump every second).
 * At midnight it's folded into a JSON history of the last 90 days.
 */
object History {
    private const val HISTORY = "history"
    private const val APP_C = "ac_"
    private const val APP_S = "as_"
    private const val HOUR = "h_"
    private const val KEEP_DAYS = 90L

    /** +1 reel. Returns today's new total. */
    fun recordReel(p: SharedPreferences, pkg: String?): Int {
        Prefs.rollDay(p)
        val c = p.getInt(Prefs.COUNT, 0) + 1
        val h = LocalTime.now().hour
        val e = p.edit().putInt(Prefs.COUNT, c).putInt(HOUR + h, p.getInt(HOUR + h, 0) + 1)
        if (pkg != null) e.putInt(APP_C + pkg, p.getInt(APP_C + pkg, 0) + 1)
        e.apply()
        return c
    }

    /** +1 second of scrolling. Returns today's new total. */
    fun recordSecond(p: SharedPreferences, pkg: String?): Long {
        val s = p.getLong(Prefs.SECONDS, 0) + 1
        val e = p.edit().putLong(Prefs.SECONDS, s)
        if (pkg != null) e.putLong(APP_S + pkg, p.getLong(APP_S + pkg, 0) + 1)
        e.apply()
        return s
    }

    /** +1 second of screen time (phone unlocked, screen on). Returns today's new total. */
    fun recordScreenSecond(p: SharedPreferences): Long {
        val s = p.getLong(Prefs.SCREEN_SECONDS, 0) + 1
        p.edit().putLong(Prefs.SCREEN_SECONDS, s).apply()
        return s
    }

    fun today(p: SharedPreferences): DayStats {
        val apps = Prefs.SUPPORTED.keys.mapNotNull { k ->
            val c = p.getInt(APP_C + k, 0); val s = p.getLong(APP_S + k, 0)
            if (c > 0 || s > 0) k to (c to s) else null
        }.toMap()
        val date = try { LocalDate.parse(p.getString(Prefs.DAY, null)) } catch (_: Exception) { LocalDate.now() }
        return DayStats(date, p.getInt(Prefs.COUNT, 0), p.getLong(Prefs.SECONDS, 0), apps,
            IntArray(24) { p.getInt(HOUR + it, 0) }, p.getLong(Prefs.SCREEN_SECONDS, 0))
    }

    fun clearToday(e: SharedPreferences.Editor) {
        e.putInt(Prefs.COUNT, 0).putLong(Prefs.SECONDS, 0).remove(Prefs.WARNED_LEVEL)
            .putLong(Prefs.SCREEN_SECONDS, 0).remove(Prefs.SCREEN_WARNED)
        for (k in Prefs.SUPPORTED.keys) e.remove(APP_C + k).remove(APP_S + k)
        for (h in 0..23) e.remove(HOUR + h)
    }

    fun archive(p: SharedPreferences, e: SharedPreferences.Editor, d: DayStats) {
        val json = load(p)
        val apps = JSONObject()
        d.apps.forEach { (k, v) -> apps.put(k, JSONArray().put(v.first).put(v.second)) }
        json.put(d.date.toString(), JSONObject()
            .put("c", d.count).put("s", d.seconds).put("sc", d.screen).put("a", apps)
            .put("h", JSONArray().apply { d.hours.forEach { put(it) } }))
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS)
        json.keys().asSequence().toList().forEach { k ->
            if (runCatching { LocalDate.parse(k).isBefore(cutoff) }.getOrDefault(true)) json.remove(k)
        }
        e.putString(HISTORY, json.toString())
    }

    /** The last [days] days, oldest first, ending with today. Missing days are zero. */
    fun lastDays(p: SharedPreferences, days: Int): List<DayStats> {
        Prefs.rollDay(p)
        val json = load(p)
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            if (back == 0) today(p) else parse(date, json.optJSONObject(date.toString()))
        }
    }

    private fun parse(date: LocalDate, o: JSONObject?): DayStats {
        if (o == null) return DayStats(date, 0, 0, emptyMap(), IntArray(24))
        val apps = HashMap<String, Pair<Int, Long>>()
        o.optJSONObject("a")?.let { a ->
            a.keys().forEach { k -> val v = a.getJSONArray(k); apps[k] = v.getInt(0) to v.getLong(1) }
        }
        val h = o.optJSONArray("h")
        return DayStats(date, o.optInt("c"), o.optLong("s"), apps,
            IntArray(24) { h?.optInt(it) ?: 0 }, o.optLong("sc"))
    }

    private fun load(p: SharedPreferences): JSONObject =
        try { JSONObject(p.getString(HISTORY, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
}
