package com.reelcount

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ProgressBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.view.View
import android.widget.RadioGroup
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : BaseActivity() {

    private val prefs by lazy { Prefs.get(this) }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var btnAccess: Button
    private lateinit var statCount: TextView
    private lateinit var statTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First launch: show the welcome screen once
        if (!prefs.getBoolean(Prefs.WELCOMED, false)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnChallenge).setOnClickListener {
            startActivity(Intent(this, ChallengeActivity::class.java))
        }
        findViewById<Button>(R.id.btnAnalysis).setOnClickListener {
            startActivity(Intent(this, AnalysisActivity::class.java))
        }

        // Hero: character + date
        heroChar = CharacterView(this)
        findViewById<FrameLayout>(R.id.heroCharBox).addView(heroChar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        heroChar.setRatio(currentRatio(), immediate = true)
        findViewById<TextView>(R.id.dateText).text =
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase()

        // Appearance
        val themeGroup = findViewById<RadioGroup>(R.id.themeGroup)
        themeGroup.check(
            when (prefs.getString(Prefs.THEME, "system")) {
                "light" -> R.id.themeLight
                "dark" -> R.id.themeDark
                else -> R.id.themeSystem
            }
        )
        themeGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) { R.id.themeLight -> "light"; R.id.themeDark -> "dark"; else -> "system" }
            if (mode != prefs.getString(Prefs.THEME, "system")) {
                prefs.edit().putString(Prefs.THEME, mode).commit()
                recreate()
            }
        }

        status = findViewById(R.id.status)
        btnAccess = findViewById(R.id.btnAccess)
        statCount = findViewById(R.id.statCount)
        statTime = findViewById(R.id.statTime)

        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener { confirmReset() }

        // Limits
        val reelLimit = findViewById<EditText>(R.id.reelLimit)
        val timeLimit = findViewById<EditText>(R.id.timeLimit)
        reelLimit.setText(Prefs.reelLimit(prefs).toString())
        timeLimit.setText(Prefs.timeLimitMin(prefs).toString())
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putInt(Prefs.REEL_LIMIT, reelLimit.text.toString().toIntOrNull() ?: 0)
                .putInt(Prefs.TIME_LIMIT, timeLimit.text.toString().toIntOrNull() ?: 0)
                .apply()
            Toast.makeText(this, "Limits saved", Toast.LENGTH_SHORT).show()
        }

        // Toggles
        bindSwitch(R.id.swShow, Prefs.SHOW_PILL, true)
        bindSwitch(R.id.swVibrate, Prefs.VIBRATE, true)
        bindSwitch(R.id.swFullscreen, Prefs.FULLSCREEN_WARN, true)

        // Sound
        val soundRow = findViewById<View>(R.id.soundRow)
        btnSound = findViewById(R.id.btnSound)
        findViewById<Switch>(R.id.swSound).apply {
            isChecked = prefs.getBoolean(Prefs.SOUND, true)
            soundRow.visibility = if (isChecked) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(Prefs.SOUND, on).apply()
                soundRow.visibility = if (on) View.VISIBLE else View.GONE
            }
        }
        updateSoundLabel()
        btnSound.setOnClickListener {
            val pick = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Warning sound")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Prefs.soundUri(prefs))
            try { startActivityForResult(pick, PICK_SOUND) } catch (_: Exception) {
                Toast.makeText(this, "No sound picker on this phone", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnTestSound).setOnClickListener {
            testTone?.stop()
            testTone = Prefs.playSound(this, prefs)
        }
        bindSwitch(R.id.swAny, Prefs.ANY_SWIPE, false)

        // Pill position: left / center / right
        val posGroup = findViewById<RadioGroup>(R.id.posGroup)
        posGroup.check(
            when (Prefs.position(prefs)) {
                "center" -> R.id.posCenter
                "right" -> R.id.posRight
                else -> R.id.posLeft
            }
        )
        posGroup.setOnCheckedChangeListener { _, id ->
            val pos = when (id) { R.id.posCenter -> "center"; R.id.posRight -> "right"; else -> "left" }
            prefs.edit().putString(Prefs.POSITION, pos).apply()
        }

        // Distance from top
        val offsetLabel = findViewById<TextView>(R.id.offsetLabel)
        val offset = findViewById<SeekBar>(R.id.offset)
        offset.progress = Prefs.offsetDp(prefs)
        offsetLabel.text = "Distance from top: ${offset.progress}dp"
        offset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                offsetLabel.text = "Distance from top: ${p}dp"
                prefs.edit().putInt(Prefs.OFFSET_DP, p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        // Apps
        val appsBox = findViewById<LinearLayout>(R.id.appsBox)
        for ((pkg, label) in Prefs.SUPPORTED) {
            val cb = CheckBox(this).apply {
                text = label
                textSize = 16f
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setTextColor(getColor(R.color.text_row))
                isChecked = pkg in Prefs.apps(prefs)
                setOnCheckedChangeListener { _, on ->
                    val set = Prefs.apps(prefs).toMutableSet()
                    if (on) set.add(pkg) else set.remove(pkg)
                    prefs.edit().putStringSet(Prefs.APPS, set).apply()
                }
            }
            appsBox.addView(cb)
        }
    }

    private lateinit var btnSound: Button
    private var testTone: Ringtone? = null

    private fun updateSoundLabel() {
        btnSound.text = "Sound: ${Prefs.soundTitle(this, prefs)}"
    }

    @Deprecated("Ringtone picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_SOUND || resultCode != RESULT_OK || data == null) return
        @Suppress("DEPRECATION")
        val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        prefs.edit().putString(Prefs.SOUND_URI, uri?.toString()).apply()
        updateSoundLabel()
    }

    override fun onDestroy() {
        testTone?.stop()
        super.onDestroy()
    }

    companion object { private const val PICK_SOUND = 42 }

    private fun bindSwitch(id: Int, key: String, def: Boolean) {
        findViewById<Switch>(id).apply {
            isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        }
    }

    private val ticker = object : Runnable {
        override fun run() { updateStats(); main.postDelayed(this, 1000) }
    }

    override fun onResume() {
        super.onResume()
        val on = serviceEnabled()
        status.text = if (on) "Running" else "Off"
        status.setTextColor(getColor(if (on) R.color.good else R.color.chart_over))
        findViewById<View>(R.id.offCard).visibility = if (on) View.GONE else View.VISIBLE
        status.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        main.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(ticker)
    }

    /** Ask before wiping today's numbers. */
    private fun confirmReset() {
        if (Challenge.isLocked(prefs)) {
            AlertDialog.Builder(this)
                .setTitle("Reset is locked")
                .setMessage("You're in a challenge with friends until 11:59 PM tonight, so your count can't be reset. " +
                    "Reset unlocks after 11:59 PM, or sooner if every player agrees to end the challenge.")
                .setNegativeButton("OK", null)
                .setPositiveButton("View challenge") { _, _ ->
                    startActivity(Intent(this, ChallengeActivity::class.java))
                }
                .show()
                .getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.accent))
            return
        }
        val c = prefs.getInt(Prefs.COUNT, 0)
        val time = Prefs.formatTime(prefs.getLong(Prefs.SECONDS, 0))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Reset today's count?")
            .setMessage(
                "This will permanently delete today's progress so far: $c reels and $time of " +
                    "scrolling time. Your counts will start again from zero. This can't be undone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                Prefs.resetToday(prefs)
                updateStats()
                Toast.makeText(this, "Today's count has been reset", Toast.LENGTH_SHORT).show()
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.chart_over))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getColor(R.color.accent))
    }

    private lateinit var heroChar: CharacterView

    private fun currentRatio() = Mood.ratio(
        prefs.getInt(Prefs.COUNT, 0), prefs.getLong(Prefs.SECONDS, 0),
        Prefs.reelLimit(prefs), Prefs.timeLimitMin(prefs))

    private fun updateStats() {
        Prefs.rollDay(prefs)
        val c = prefs.getInt(Prefs.COUNT, 0)
        val s = prefs.getLong(Prefs.SECONDS, 0)
        val rl = Prefs.reelLimit(prefs)
        val tl = Prefs.timeLimitMin(prefs)
        val ratio = currentRatio()

        statCount.text = "$c"
        statTime.text = "${Prefs.formatTime(s)} scrolling"
        heroChar.setRatio(ratio)
        findViewById<TextView>(R.id.moodText).text =
            if (c == 0 && s == 0L) "Sprout is ready" else "Sprout: ${Mood.text(ratio)}"

        bar(R.id.reelBar, R.id.reelBarLabel, rl > 0, if (rl > 0) c / rl.toFloat() else 0f,
            "$c of $rl reels")
        bar(R.id.timeBar, R.id.timeBarLabel, tl > 0, if (tl > 0) s / (tl * 60f) else 0f,
            "${s / 60} of $tl minutes")

        // Challenge: show status on the button; lock Reset and app choices while live
        val locked = Challenge.isLocked(prefs)
        findViewById<Button>(R.id.btnChallenge).text = when {
            Challenge.code(prefs) == null -> "Challenge friends"
            locked -> "Challenge live  ·  ${Challenge.myReels(prefs)} reels"
            Challenge.status(prefs) == "waiting" -> "Challenge  ·  waiting for friends"
            else -> "View challenge results"
        }
        findViewById<Button>(R.id.btnReset).alpha = if (locked) 0.5f else 1f
        findViewById<Switch>(R.id.swAny).isEnabled = !locked
        val apps = findViewById<LinearLayout>(R.id.appsBox)
        for (i in 0 until apps.childCount) apps.getChildAt(i).isEnabled = !locked
    }

    private fun bar(barId: Int, labelId: Int, visible: Boolean, fraction: Float, caption: String) {
        val bar = findViewById<ProgressBar>(barId)
        val label = findViewById<TextView>(labelId)
        val vis = if (visible) View.VISIBLE else View.GONE
        bar.visibility = vis; label.visibility = vis
        bar.progress = (fraction.coerceIn(0f, 1f) * 1000).toInt()
        val over = fraction >= 1f
        label.text = if (over) "$caption  ·  over limit" else caption
        bar.progressTintList = ColorStateList.valueOf(if (over) Color.parseColor("#FFE066") else Color.WHITE)
    }

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, ReelCounterService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
