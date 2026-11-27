package com.reelcount

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.app.KeyguardManager
import android.widget.Toast
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Watches scroll events inside reel apps. Each swipe to a new reel = +1.
 * While a reel app is open a timer runs. The pill is drawn as an
 * accessibility overlay, so no extra "draw over apps" permission is needed.
 */
class ReelCounterService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        /** View-id fragments of the full-screen reel pagers in each app. */
        private val REEL_IDS = listOf(
            "clips_viewer", "clips_video", "clips_tab",      // Instagram
            "reel_recycler", "reel_player", "reel_watch",    // YouTube Shorts
            "reels_viewer", "reel_viewer", "video_home",     // Facebook
            "spotlight",                                     // Snapchat
        )
        /** Apps where the main feed itself is a full-screen reel pager. */
        private val FULLSCREEN_APPS = setOf(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        )
        private val IGNORED = setOf("com.android.systemui", "android")
        private const val NEW_SWIPE_GAP_MS = 600L
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var pill: PillView? = null
    private var warning: WarningView? = null
    private var fgPkg: String? = null
    private var watching = false                 // swiped a reel since opening the app
    private val lastIndex = HashMap<String, Int>()
    private var lastScrollAt = 0L
    private var lastCountAt = 0L

    override fun onServiceConnected() {
        prefs = Prefs.get(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Prefs.rollDay(prefs)
        applyPackageFilter()
        main.post(tick)
    }

    /**
     * Tell Android to only connect us to the reel apps the user ticked.
     * Every other app (UPI, banking, messages…) never sends us anything.
     */
    private fun applyPackageFilter() {
        val info = serviceInfo ?: return
        val apps = Prefs.apps(prefs).filter { it in Prefs.SUPPORTED }
        // An empty list would mean "all apps" to Android, so fall back to our own package.
        info.packageNames = if (apps.isEmpty()) arrayOf(packageName) else apps.toTypedArray()
        serviceInfo = info
    }

    /**
     * We only receive events from reel apps, so we can't see you switch to another app.
     * Instead, once a second ask which window is active: if it isn't a reel app
     * (or Android won't tell us, as with filtered apps), switch off.
     */
    private var misses = 0
    private fun checkStillInReelApp() {
        if (!isTracked(fgPkg)) { misses = 0; return }
        val active = try {
            rootInActiveWindow?.let { r ->
                val p = r.packageName?.toString()
                @Suppress("DEPRECATION") r.recycle()
                p
            }
        } catch (_: Exception) { null }
        when {
            active != null && isTracked(active) -> { misses = 0; switchTo(active) }
            ++misses >= 2 -> { misses = 0; switchTo("") }   // left the reel app → off
        }
    }

    // ---------- timer ----------

    private val tick = object : Runnable {
        override fun run() {
            Prefs.rollDay(prefs)
            checkStillInReelApp()
            val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
            // Timer runs the whole time a reel app is open with the screen on,
            // whether you're swiping or just watching.
            // Screen time: phone unlocked and screen on, in any app
            val locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
            if (interactive && !locked) checkScreenLimit(History.recordScreenSecond(prefs))
            if (interactive && isTracked(fgPkg)) {
                History.recordSecond(prefs, fgPkg)
                checkLimits()
            }
            refresh()
            challengeTick()
            main.postDelayed(this, 1000)
        }
    }

    // ---------- friends' challenge ----------

    private var lastChallengeSync = 0L

    /**
     * While in a challenge: push my progress every 30 s (and once more just before 11:59 PM).
     * Shortly after 11:59 PM: fetch the final standings and post the results notification.
     */
    private fun challengeTick() {
        if (!Cloud.configured) return
        val code = Challenge.code(prefs) ?: return
        val now = System.currentTimeMillis()
        val ends = Challenge.endsAt(prefs)
        if (now < ends) {
            val finalPush = ends - now < 20_000 && !Challenge.finalPushed(prefs)
            if (finalPush) Challenge.markFinalPushed(prefs)
            if (finalPush || now - lastChallengeSync >= 30_000) {
                lastChallengeSync = now
                Cloud.run({ Challenge.sync(this) })
            }
        } else if (now >= ends + 30_000 && !Challenge.notified(prefs)) {
            Challenge.markNotified(prefs)
            Cloud.run({ Challenge.fetch(this, code) }) { r ->
                r.getOrNull()?.let { st ->
                    if (st.status != "ended" && st.members.size >= 2) {
                        Notify.results(this, st, Cloud.uidOrNull(this))
                    }
                }
            }
        }
    }

    // ---------- events ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg in IGNORED || pkg == packageName ||
                    pkg.contains("inputmethod") || pkg.contains("keyboard")) return
                switchTo(pkg)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!isTracked(pkg)) return
                switchTo(pkg)
                val key = reelSourceKey(event, pkg) ?: return
                onReelScroll(event, key)
            }
        }
    }

    private fun switchTo(pkg: String) {
        if (pkg == fgPkg) return
        fgPkg = pkg
        watching = isTracked(pkg)                // timer (and Sprout) start as soon as the app opens
        lastIndex.clear()
        if (!isTracked(pkg)) dismissWarning()   // left the app another way (home gesture etc.)
        refresh()
    }

    /** Returns a key for the scrolling view if it's a reel pager, else null. */
    private fun reelSourceKey(event: AccessibilityEvent, pkg: String): String? {
        val src = event.source
        val id = src?.viewIdResourceName?.lowercase() ?: ""
        val isReel = when {
            prefs.getBoolean(Prefs.ANY_SWIPE, false) -> true
            REEL_IDS.any { id.contains(it) } -> true
            pkg in FULLSCREEN_APPS && src != null -> {
                val r = Rect(); src.getBoundsInScreen(r)
                r.height() > resources.displayMetrics.heightPixels * 0.75
            }
            else -> false
        }
        @Suppress("DEPRECATION") src?.recycle()
        return if (isReel) "$pkg/$id" else null
    }

    private fun onReelScroll(event: AccessibilityEvent, key: String) {
        val now = SystemClock.uptimeMillis()
        val gap = now - lastScrollAt
        lastScrollAt = now
        watching = true

        // Pagers report which item is on screen: a new index = a new reel.
        // If the app doesn't report it, treat each separate swipe gesture as one reel.
        val idx = event.fromIndex
        val isNewReel = if (idx >= 0) {
            val prev = lastIndex.put(key, idx)
            prev != null && prev != idx
        } else {
            gap > NEW_SWIPE_GAP_MS
        }
        if (!isNewReel || now - lastCountAt < 250) return
        lastCountAt = now

        History.recordReel(prefs, fgPkg)
        refresh()
        pill?.bump()
        checkLimits()
    }

    /**
     * One warning for whichever limit is reached first (reels or time).
     * Level 1 = first limit hit, level 2 = double a limit, and so on.
     * Each level warns once per day, so hitting the second limit later doesn't warn again.
     */
    private fun checkLimits() {
        val c = prefs.getInt(Prefs.COUNT, 0)
        val s = prefs.getLong(Prefs.SECONDS, 0)
        val rl = Prefs.reelLimit(prefs)
        val tl = Prefs.timeLimitMin(prefs)
        val reelRatio = if (rl > 0) c.toFloat() / rl else 0f
        val timeRatio = if (tl > 0) s / (tl * 60f) else 0f
        val level = maxOf(reelRatio, timeRatio).toInt()
        if (level <= prefs.getInt(Prefs.WARNED_LEVEL, 0)) return
        prefs.edit().putInt(Prefs.WARNED_LEVEL, level).apply()
        if (reelRatio >= timeRatio) {
            warn("$c reels", "watched today · ${Prefs.formatTime(s)} spent scrolling")
        } else {
            warn("${s / 60} minutes", "of reels today · $c reels watched")
        }
    }

    /** One warning each time screen time passes the limit, 2× the limit, and so on. */
    private fun checkScreenLimit(screenSec: Long) {
        val limitMin = Prefs.screenLimitMin(prefs)
        if (limitMin <= 0) return
        val level = (screenSec / (limitMin * 60L)).toInt()
        if (level <= prefs.getInt(Prefs.SCREEN_WARNED, 0)) return
        prefs.edit().putInt(Prefs.SCREEN_WARNED, level).apply()
        warn("${Prefs.formatHm(screenSec)} screen time", "on your phone today", screen = true)
    }

    // ---------- overlay ----------

    private fun isTracked(pkg: String?) = pkg != null && pkg in Prefs.apps(prefs)

    private fun refresh() {
        val show = prefs.getBoolean(Prefs.SHOW_PILL, true) && isTracked(fgPkg)
        if (show && pill == null) addPill()
        if (!show && pill != null) removePill()
        pill?.update(
            prefs.getInt(Prefs.COUNT, 0), prefs.getLong(Prefs.SECONDS, 0),
            Prefs.reelLimit(prefs), Prefs.timeLimitMin(prefs), watching,
        )
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        val d = resources.displayMetrics.density
        gravity = Gravity.TOP or when (Prefs.position(prefs)) {
            "center" -> Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.END
            else -> Gravity.START          // default: top-left, clear of a centre punch-hole camera
        }
        x = if (Prefs.position(prefs) == "center") 0 else (14 * d).toInt()
        y = (Prefs.offsetDp(prefs) * d).toInt()
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun addPill() {
        val v = PillView(this)
        try { wm.addView(v, params()); pill = v } catch (_: Exception) { }
    }

    private fun removePill() {
        pill?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        pill = null
    }

    private fun warn(headline: String, detail: String, screen: Boolean = false) {
        if (pill == null) refresh()
        pill?.warn("$headline · take a break")
        if (prefs.getBoolean(Prefs.VIBRATE, true)) {
            @Suppress("DEPRECATION")
            val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120), -1))
        }
        val fullscreen = prefs.getBoolean(Prefs.FULLSCREEN_WARN, true)
        if (fullscreen) showWarning(headline, detail, screen)
        else if (screen && pill == null) Toast.makeText(this, "$headline. Time for a break.", Toast.LENGTH_LONG).show()
        if (prefs.getBoolean(Prefs.SOUND, true)) {
            if (!fullscreen) pauseReelAudio()   // full-screen warning already paused it
            ringtone?.stop()
            ringtone = Prefs.playSound(this, prefs)
            // pill-only warning: give the audio back to the reel after the sound
            if (!fullscreen) main.postDelayed({ if (warning == null) resumeReelAudio() }, 4000)
        }
    }

    // ---------- audio focus: pauses the reel while warning ----------

    private var ringtone: Ringtone? = null
    private var focusRequest: AudioFocusRequest? = null

    private fun pauseReelAudio() {
        if (focusRequest != null) return
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        am.requestAudioFocus(req)
        focusRequest = req
    }

    private fun resumeReelAudio() {
        val req = focusRequest ?: return
        (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(req)
        focusRequest = null
    }

    private fun showWarning(headline: String, detail: String, screen: Boolean = false) {
        if (warning != null) return
        val v = if (screen) {
            val limit = Prefs.screenLimitMin(prefs) * 60f
            WarningView(
                this, headline, detail,
                closeLabel = "Lock phone",
                lastStep = "Lock your phone and put it face-down for a while",
                keepLabel = "Keep using",
                ratio = if (limit > 0) prefs.getLong(Prefs.SCREEN_SECONDS, 0) / limit else 1f,
                onClose = {
                    dismissWarning()
                    performGlobalAction(if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else GLOBAL_ACTION_HOME)
                },
                onContinue = { dismissWarning() },
            )
        } else {
            val label = Prefs.SUPPORTED[fgPkg]?.substringBefore(" ") ?: "app"
            WarningView(
                this, headline, detail,
                closeLabel = "Close $label",
                lastStep = "Close $label and put your phone face-down",
                keepLabel = "Keep watching",
                ratio = Mood.ratio(prefs.getInt(Prefs.COUNT, 0), prefs.getLong(Prefs.SECONDS, 0),
                    Prefs.reelLimit(prefs), Prefs.timeLimitMin(prefs)),
                onClose = { dismissWarning(); performGlobalAction(GLOBAL_ACTION_HOME) },
                onContinue = { dismissWarning() },
            )
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try { wm.addView(v, lp); warning = v; pauseReelAudio() } catch (_: Exception) { }
    }

    private fun dismissWarning() {
        warning?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        warning = null
        ringtone?.stop()
        resumeReelAudio()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        if (key == Prefs.APPS) applyPackageFilter()
        if (key == Prefs.OFFSET_DP || key == Prefs.POSITION) pill?.let { try { wm.updateViewLayout(it, params()) } catch (_: Exception) { } }
        if (key != Prefs.SECONDS && key != Prefs.SCREEN_SECONDS && key?.startsWith("as_") != true) refresh()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        removePill()
        dismissWarning()
        super.onDestroy()
    }
}
