package com.reelcount

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shown once, the first time the app is opened. "OK" goes to the main page. */
class WelcomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#7B61FF")
        window.navigationBarColor = Color.parseColor("#E86BC0")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0          // white status-bar icons on the gradient

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(28), dp(26), dp(28))
        }

        col.addView(CharacterView(this).apply { setRatio(0f, immediate = true) },
            LinearLayout.LayoutParams(dp(170), dp(170)))

        col.addView(text("Hi there!", 34f, bold = true).apply { setPadding(0, dp(6), 0, 0) })
        col.addView(text("I'm Sprout. Welcome to Reel Counter!", 18f, bold = true, alpha = 0.95f)
            .apply { setPadding(0, dp(6), 0, dp(20)) })

        // Privacy message in a soft glass card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat(); setColor(Color.parseColor("#2EFFFFFF"))
            }
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        card.addView(text(
            "Reel Counter works offline. It only counts your reels and the time you spend, " +
                "and all of that stays right here on this phone.",
            16f, start = true))
        card.addView(text(
            "We don't collect your name, contacts, messages, photos or any other details, " +
                "and nothing is ever sent to the developer.",
            16f, start = true).apply { setPadding(0, dp(12), 0, 0) })
        card.addView(text(
            "The app goes online only if you choose to start or join a challenge with friends. " +
                "Then just your nickname, reel count and scrolling time are shared, and only with the players in that challenge.",
            16f, start = true).apply { setPadding(0, dp(12), 0, 0) })
        col.addView(card, LinearLayout.LayoutParams(-1, -2))

        // Three quick promises
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(26))
        }
        listOf("Offline first", "Private", "On-device").forEach {
            chips.addView(text(it, 12f, bold = true).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(100).toFloat(); setColor(Color.parseColor("#33FFFFFF"))
                }
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        col.addView(chips)

        val ok = text("OK, let's go", 17f, bold = true).apply {
            setTextColor(Color.parseColor("#6C5CE7"))
            background = GradientDrawable().apply {
                cornerRadius = dp(100).toFloat(); setColor(Color.WHITE)
            }
            setPadding(dp(24), dp(16), dp(24), dp(16))
            elevation = dp(4).toFloat()
            setOnClickListener { finishWelcome() }
        }
        col.addView(ok, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#7B61FF"), Color.parseColor("#A35CF0"), Color.parseColor("#FF6FB5")),
            )
            fitsSystemWindows = true
            addView(LinearLayout(this@WelcomeActivity).apply {
                gravity = Gravity.CENTER
                addView(col, LinearLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, -1))
        }
        setContentView(scroll)
    }

    private fun finishWelcome() {
        Prefs.get(this).edit().putBoolean(Prefs.WELCOMED, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun text(s: String, sp: Float, bold: Boolean = false, start: Boolean = false, alpha: Float = 1f) =
        TextView(this).apply {
            text = s
            setTextColor(Color.WHITE)
            this.alpha = alpha
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            gravity = if (start) Gravity.START else Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlignment = if (start) View.TEXT_ALIGNMENT_VIEW_START else View.TEXT_ALIGNMENT_CENTER
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
