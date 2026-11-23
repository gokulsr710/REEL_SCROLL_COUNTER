package com.reelcount

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The tiny "dynamic island" style pill:  (🙂) 23 · 4:12
 * The character pops in when the timer starts and gets sadder as you pass your limit.
 * Tap to see your limits. Turns red with a message when a limit is hit.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class PillView(context: Context) : LinearLayout(context) {

    private val face = CharacterView(context)
    private val text = TextView(context)
    private val bg = GradientDrawable()
    private val main = Handler(Looper.getMainLooper())

    private var count = 0
    private var seconds = 0L
    private var reelLimit = 0
    private var timeLimitMin = 0
    private var watching = false
    private var mode = Mode.NORMAL
    private var message = ""

    private enum class Mode { NORMAL, DETAILS, WARNING }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        bg.cornerRadius = dp(100f)
        background = bg
        elevation = dp(4f)

        face.visibility = View.GONE
        addView(face, LayoutParams(dp(32f).toInt(), dp(32f).toInt()).apply {
            marginStart = dp(4f).toInt()
        })

        text.setTextColor(Color.WHITE)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        text.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        text.isSingleLine = true
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        applyPadding()

        setOnClickListener { if (mode == Mode.NORMAL) showFor(Mode.DETAILS, 3000) }
        render()
    }

    fun update(count: Int, seconds: Long, reelLimit: Int, timeLimitMin: Int, watching: Boolean) {
        this.count = count; this.seconds = seconds
        this.reelLimit = reelLimit; this.timeLimitMin = timeLimitMin
        val ratio = Mood.ratio(count, seconds, reelLimit, timeLimitMin)
        if (watching && !this.watching) popInFace(ratio)
        this.watching = watching
        face.setRatio(ratio)
        render()
    }

    /** The character appears with a little bounce when the timer starts. */
    private fun popInFace(ratio: Float) {
        face.setRatio(ratio, immediate = true)
        face.visibility = View.VISIBLE
        face.scaleX = 0f; face.scaleY = 0f
        face.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(3f)).setDuration(400).start()
        applyPadding()
    }

    private fun applyPadding() {
        val h = dp(12f).toInt(); val v = dp(4f).toInt()
        val left = if (face.visibility == View.VISIBLE) dp(2f).toInt() else h
        text.setPadding(if (face.visibility == View.VISIBLE) dp(4f).toInt() else 0, v, h, v)
        setPadding(left, dp(1f).toInt(), 0, dp(1f).toInt())
    }

    /** Little pop each time a reel is counted. */
    fun bump() {
        animate().cancel()
        scaleX = 1.12f; scaleY = 1.12f
        animate().scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    fun warn(msg: String) {
        message = msg
        showFor(Mode.WARNING, 5000)
        scaleX = 0.6f; scaleY = 0.6f
        animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(3f)).setDuration(450).start()
    }

    private fun showFor(m: Mode, ms: Long) {
        mode = m
        render()
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ mode = Mode.NORMAL; render() }, ms)
    }

    private fun overLimit(): Boolean =
        (reelLimit > 0 && count >= reelLimit) ||
            (timeLimitMin > 0 && seconds >= timeLimitMin * 60L)

    private fun render() {
        val time = Prefs.formatTime(seconds)
        val icon = if (face.visibility == View.VISIBLE) "" else "▶ "
        text.text = when (mode) {
            Mode.NORMAL -> "$icon$count · $time"
            Mode.DETAILS -> {
                val r = if (reelLimit > 0) "$count/$reelLimit reels" else "$count reels"
                val t = if (timeLimitMin > 0) "$time / ${timeLimitMin}m" else time
                "$r · $t"
            }
            Mode.WARNING -> "⚠ $message"
        }
        bg.setColor(
            when {
                mode == Mode.WARNING -> Color.parseColor("#E5352B")
                overLimit() -> Color.parseColor("#8E1B16")   // stays dark red once over a limit
                else -> Color.parseColor("#EE000000")
            }
        )
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
