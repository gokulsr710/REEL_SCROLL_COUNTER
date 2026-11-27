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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Full-screen "take a break" card shown when a limit is reached.
 * "Close app" sends you home; "Keep watching" unlocks after a short countdown.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class WarningView(
    context: Context,
    headline: String,
    detail: String,
    private val closeLabel: String,     // e.g. "Close Instagram" or "Lock phone"
    private val lastStep: String,       // third "what to do now" step
    private val keepLabel: String,      // e.g. "Keep watching" or "Keep using"
    ratio: Float,
    private val onClose: () -> Unit,
    private val onContinue: () -> Unit,
) : FrameLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val keepBtn: TextView
    private var secondsLeft = 5

    init {
        setBackgroundColor(Color.parseColor("#F2000000"))
        isClickable = true   // swallow touches so the reel underneath can't be swiped

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = dp(32); setPadding(p, p, p, p)
        }

        col.addView(CharacterView(context).apply { setRatio(ratio, immediate = true) },
            LinearLayout.LayoutParams(dp(160), dp(160)))
        col.addView(text(headline, 40f, Color.WHITE, bold = true).apply { setPadding(0, dp(4), 0, 0) })
        col.addView(text(detail, 17f, Color.parseColor("#C7C7CC")).apply { setPadding(0, dp(6), 0, 0) })
        col.addView(text(if (ratio >= 1.4f) "Sprout is exhausted. Time to rest." else "Sprout needs a break.",
            20f, Color.parseColor("#FF6B63"), bold = true)
            .apply { setPadding(0, dp(20), 0, dp(18)) })

        // What to do now: breathe, one random healthy step, close the app
        val middle = listOf(
            "Stand up and stretch for a minute",
            "Drink a glass of water",
            "Look out of a window or step outside",
            "Text or call someone you like",
            "Do one small task you've been putting off",
            "Roll your shoulders and relax your jaw",
        ).shuffled().first()
        col.addView(stepsCard(listOf(
            "Take 3 slow, deep breaths: in for 3 seconds, out for 3",
            middle,
            lastStep,
        )), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(22) })

        val closeBtn = text(closeLabel, 17f, Color.WHITE, bold = true).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(Color.parseColor("#FF3B30"))
            }
            setPadding(dp(28), dp(16), dp(28), dp(16))
            setOnClickListener { onClose() }
        }
        col.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        keepBtn = text("", 15f, Color.parseColor("#8E8E93")).apply {
            setPadding(dp(16), dp(18), dp(16), dp(16))
            setOnClickListener { if (secondsLeft <= 0) onContinue() }
        }
        col.addView(keepBtn)

        // Scrollable so it still fits on small screens; centred when there's room.
        val holder = FrameLayout(context).apply {
            addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        addView(ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(holder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        countdown()
    }

    /** "WHAT TO DO NOW" card with numbered steps. */
    private fun stepsCard(steps: List<String>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#1C1C1E"))
        }
        setPadding(dp(18), dp(16), dp(18), dp(10))
        addView(text("WHAT TO DO NOW", 12f, Color.parseColor("#8E8E93"), bold = true).apply {
            gravity = Gravity.START; letterSpacing = 0.08f
        })
        steps.forEachIndexed { i, s ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(4))
                addView(text("${i + 1}", 13f, Color.WHITE, bold = true).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Color.parseColor("#3A3A3C"))
                    }
                }, LinearLayout.LayoutParams(dp(26), dp(26)))
                addView(text(s, 15f, Color.WHITE).apply {
                    gravity = Gravity.START; setPadding(dp(12), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun countdown() {
        if (secondsLeft > 0) {
            keepBtn.text = "$keepLabel ($secondsLeft)"
            keepBtn.alpha = 0.5f
            secondsLeft--
            main.postDelayed({ countdown() }, 1000)
        } else {
            keepBtn.text = keepLabel
            keepBtn.alpha = 1f
        }
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
