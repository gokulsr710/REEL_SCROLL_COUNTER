package com.reelcount

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import java.time.LocalDate

/** All settings + today's stats live in one SharedPreferences file. */
object Prefs {
    const val COUNT = "count"            // reels seen today
    const val SECONDS = "seconds"        // seconds spent scrolling reels today
    const val DAY = "day"                // yyyy-mm-dd of the stats above
    const val REEL_LIMIT = "reel_limit"  // warn every N reels (0 = off)
    const val TIME_LIMIT = "time_limit"  // warn every N minutes (0 = off)
    const val OFFSET_DP = "offset_dp"    // pill distance from top of screen
    const val SHOW_PILL = "show_pill"
    const val ANY_SWIPE = "any_swipe"    // fallback: count every swipe in tracked apps
    const val VIBRATE = "vibrate"
    const val APPS = "apps"              // enabled package names
    const val THEME = "theme"            // "system" | "light" | "dark"
    const val POSITION = "position"      // "left" | "center" | "right"
    const val FULLSCREEN_WARN = "fullscreen_warn"
    const val WELCOMED = "welcomed"           // welcome screen already shown
    const val WARNED_LEVEL = "warned_level"   // highest limit level already warned about today
    const val SOUND = "sound"            // play a sound on warning
    const val SOUND_URI = "sound_uri"    // chosen ringtone; null = phone's default notification sound

    fun soundUri(p: SharedPreferences): Uri? =
        p.getString(SOUND_URI, null)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /** Plays the chosen warning sound once. Returns the Ringtone so callers can stop it. */
    fun playSound(c: Context, p: SharedPreferences): Ringtone? {
        val uri = soundUri(p) ?: return null
        return RingtoneManager.getRingtone(c, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }

    fun soundTitle(c: Context, p: SharedPreferences): String {
        val uri = soundUri(p) ?: return "None"
        return try {
            RingtoneManager.getRingtone(c, uri)?.getTitle(c) ?: "Default"
        } catch (_: Exception) { "Default" }
    }

    fun position(p: SharedPreferences) = p.getString(POSITION, "left") ?: "left"

    /** Apps we know how to track. Package name -> label. */
    val SUPPORTED = linkedMapOf(
        "com.instagram.android" to "Instagram Reels",
        "com.google.android.youtube" to "YouTube Shorts",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok (Asia)",
        "com.facebook.katana" to "Facebook Reels",
        "com.snapchat.android" to "Snapchat Spotlight",
    )

    fun get(c: Context): SharedPreferences =
        c.getSharedPreferences("reelcount", Context.MODE_PRIVATE)

    fun reelLimit(p: SharedPreferences) = p.getInt(REEL_LIMIT, 100)
    fun timeLimitMin(p: SharedPreferences) = p.getInt(TIME_LIMIT, 30)
    fun offsetDp(p: SharedPreferences) = p.getInt(OFFSET_DP, 8)
    fun apps(p: SharedPreferences): Set<String> =
        p.getStringSet(APPS, null) ?: SUPPORTED.keys

    /** When a new day starts: save yesterday into history, then reset today's counters. */
    fun rollDay(p: SharedPreferences) {
        val today = LocalDate.now().toString()
        val day = p.getString(DAY, "")
        if (day != today) {
            val e = p.edit()
            if (!day.isNullOrEmpty()) History.archive(p, e, History.today(p))
            History.clearToday(e)
            e.putString(DAY, today).apply()
        }
    }

    fun resetToday(p: SharedPreferences) {
        val e = p.edit()
        History.clearToday(e)
        e.putString(DAY, LocalDate.now().toString()).apply()
    }

    fun formatTime(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
