package com.reelcount

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Daily analysis: today vs yesterday, 7- and 30-day charts, time of day, per-app split. */
class AnalysisActivity : BaseActivity() {

    private val prefs by lazy { Prefs.get(this) }
    private var showTime = false          // false = reels, true = minutes
    private lateinit var content: LinearLayout
    private lateinit var tabReels: TextView
    private lateinit var tabTime: TextView

    private val cPrimary by lazy { getColor(R.color.text_primary) }
    private val cBody by lazy { getColor(R.color.text_body) }
    private val cMuted by lazy { getColor(R.color.text_muted) }
    private val cCard by lazy { getColor(R.color.card) }
    private val cOver by lazy { getColor(R.color.chart_over) }
    private val cGood by lazy { getColor(R.color.good) }
    private val cTrack by lazy { getColor(R.color.track) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        root.addView(text("Daily analysis", 26f, cPrimary, bold = true))

        // Reels / Time toggle
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = round(cTrack, 100)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        tabReels = tab("Reels") { showTime = false; render() }
        tabTime = tab("Time") { showTime = true; render() }
        tabs.addView(tabReels, LinearLayout.LayoutParams(0, dp(36), 1f))
        tabs.addView(tabTime, LinearLayout.LayoutParams(0, dp(36), 1f))
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })
        render()
    }

    override fun onResume() { super.onResume(); render() }

    // ---------------------------------------------------------------- render

    private fun render() {
        if (!::content.isInitialized) return
        styleTab(tabReels, !showTime); styleTab(tabTime, showTime)
        content.removeAllViews()

        val days30 = History.lastDays(prefs, 30)
        val days7 = days30.takeLast(7)
        val today = days30.last()
        val yesterday = days30[days30.size - 2]
        val reelLimit = Prefs.reelLimit(prefs)
        val timeLimit = Prefs.timeLimitMin(prefs)

        fun v(d: DayStats) = if (showTime) d.seconds / 60f else d.count.toFloat()
        fun fmt(d: DayStats) = if (showTime) mins(d.seconds) else "${d.count}"
        val limit = if (showTime) timeLimit.toFloat() else reelLimit.toFloat()
        val unit = if (showTime) "" else " reels"

        // ---- Today
        content.addView(card().apply {
            addView(label("TODAY"))
            addView(text(fmt(today) + unit, 40f, cPrimary, bold = true))
            addView(text(
                if (showTime) "${today.count} reels watched" else "${mins(today.seconds)} spent scrolling",
                15f, cBody))
            addView(compare(v(today), v(yesterday)).apply { setPadding(0, dp(10), 0, 0) })
            if (limit > 0) addView(progress(v(today) / limit,
                if (showTime) "${mins(today.seconds)} of ${timeLimit}m limit"
                else "${today.count} of $reelLimit reel limit"))
        })

        // ---- Last 7 days
        val avg7 = days7.map { v(it) }.average().toFloat()
        val total7 = days7.sumOf { v(it).toDouble() }.toFloat()
        val under = days7.count { (reelLimit == 0 || it.count < reelLimit) &&
            (timeLimit == 0 || it.seconds < timeLimit * 60L) }
        content.addView(card().apply {
            addView(label("LAST 7 DAYS"))
            addView(chart(dp(170)).apply {
                set(days7.map { v(it) }.toFloatArray(),
                    days7.mapIndexed { i, d -> if (i == 6) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }.toTypedArray(),
                    days7.map { if (v(it) > 0) fmt(it) else "" }.toTypedArray(),
                    limit, if (limit > 0) "limit" else "")
            })
            addView(tiles(
                "Daily avg" to num(avg7),
                "Week total" to num(total7),
                "Under limit" to "$under/7 days",
            ))
        })

        // ---- Time of day (reels per hour, last 7 days)
        val hours = IntArray(24).also { h -> days7.forEach { d -> d.hours.forEachIndexed { i, n -> h[i] += n } } }
        content.addView(card().apply {
            addView(label("WHEN YOU SCROLL · LAST 7 DAYS"))
            if (hours.sum() == 0) {
                addView(text("No reels yet. Check back after some scrolling.", 15f, cMuted))
            } else {
                val peak = hours.indices.maxBy { hours[it] }
                addView(text("Most reels around ${hourName(peak)}", 17f, cPrimary, bold = true))
                val night = (0..5).sumOf { hours[it] } + hours[23]
                if (night > 0) addView(text("${pct(night, hours.sum())} of reels were late at night (11 PM – 6 AM)", 14f, cBody))
                addView(chart(dp(120)).apply {
                    emphasiseLast = false
                    set(hours.map { it.toFloat() }.toFloatArray(),
                        Array(24) { when (it) { 0 -> "12a"; 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; else -> "" } },
                        null, 0f, "")
                }, LinearLayout.LayoutParams(-1, dp(120)).apply { topMargin = dp(10) })
            }
        })

        // ---- By app (today)
        content.addView(card().apply {
            addView(label("BY APP · TODAY"))
            val apps = today.apps.entries.sortedByDescending { if (showTime) it.value.second else it.value.first.toLong() }
            if (apps.isEmpty()) addView(text("No reels yet today 🎉", 15f, cMuted))
            val maxV = apps.maxOfOrNull { if (showTime) it.value.second.toFloat() else it.value.first.toFloat() } ?: 1f
            apps.forEach { (pkg, cs) ->
                val name = Prefs.SUPPORTED[pkg] ?: pkg
                val value = if (showTime) cs.second.toFloat() else cs.first.toFloat()
                addView(appRow(name, "${cs.first} reels · ${mins(cs.second)}", value / maxOf(maxV, 1f)))
            }
        })

        // ---- Last 30 days
        val active = days30.filter { it.count > 0 || it.seconds > 0 }
        content.addView(card().apply {
            addView(label("LAST 30 DAYS"))
            addView(chart(dp(110)).apply {
                set(days30.map { v(it) }.toFloatArray(),
                    Array(30) { i -> if (i == 0) "30d ago" else if (i == 29) "Today" else "" },
                    null, limit, "")
            })
            if (active.isNotEmpty()) {
                val best = active.minBy { v(it) }
                val worst = active.maxBy { v(it) }
                addView(tiles(
                    "Avg (active days)" to num(active.map { v(it) }.average().toFloat()),
                    "Lowest" to "${fmt(best)} · ${shortDate(best)}",
                    "Highest" to "${fmt(worst)} · ${shortDate(worst)}",
                ))
            }
        })
    }

    // ---------------------------------------------------------------- pieces

    private fun compare(today: Float, yesterday: Float): TextView {
        if (yesterday <= 0f) return text(
            if (today > 0f) "No data for yesterday yet" else "Nothing yet today — nice 👏", 14f, cMuted)
        val change = ((today - yesterday) / yesterday * 100).roundToInt()
        val up = change > 0
        val what = if (showTime) "time" else "reels"
        return text(
            when {
                change == 0 -> "Same as yesterday"
                up -> "▲ $change% more $what than yesterday"
                else -> "▼ ${abs(change)}% fewer $what than yesterday"
            },
            14f, if (up) cOver else cGood, bold = true)
    }

    private fun progress(fraction: Float, caption: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, 0)
        val over = fraction >= 1f
        val track = FrameLayout(this@AnalysisActivity).apply { background = round(cTrack, 100) }
        val fill = View(this@AnalysisActivity).apply { background = round(if (over) cOver else cPrimary, 100) }
        track.addView(fill, FrameLayout.LayoutParams(0, -1))
        track.post {
            fill.layoutParams = fill.layoutParams.apply {
                width = (track.width * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(if (fraction > 0) dp(6) else 0)
            }
        }
        addView(track, LinearLayout.LayoutParams(-1, dp(8)))
        addView(text(caption + if (over) " — over" else "", 13f, if (over) cOver else cMuted)
            .apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun appRow(name: String, detail: String, fraction: Float): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(4))
        val top = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(text(name, 15f, cPrimary, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text(detail, 14f, cBody))
        addView(top)
        val track = FrameLayout(this@AnalysisActivity).apply { background = round(cTrack, 100) }
        val fill = View(this@AnalysisActivity).apply { background = round(cPrimary, 100) }
        track.addView(fill, FrameLayout.LayoutParams(0, -1))
        track.post { fill.layoutParams = fill.layoutParams.apply { width = (track.width * fraction).toInt().coerceAtLeast(dp(6)) } }
        addView(track, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6) })
    }

    private fun tiles(vararg items: Pair<String, String>): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(14), 0, 0)
        items.forEach { (k, v) ->
            addView(LinearLayout(this@AnalysisActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(v, 16f, cPrimary, bold = true))
                addView(text(k, 12f, cMuted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun chart(h: Int) = BarChartView(this).apply {
        barColor = getColor(R.color.chart_main)
        dimColor = getColor(R.color.chart_dim)
        overColor = cOver
        textColor = cBody
        gridColor = getColor(R.color.chart_grid)
        layoutParams = LinearLayout.LayoutParams(-1, h).apply { topMargin = dp(8) }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(cCard, 16)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    private fun label(s: String) = text(s, 12f, cMuted, bold = true).apply {
        letterSpacing = 0.08f; setPadding(0, 0, 0, dp(4))
    }

    private fun tab(s: String, onClick: () -> Unit) = text(s, 14f, cPrimary, bold = true).apply {
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun styleTab(t: TextView, on: Boolean) {
        t.background = if (on) round(cCard, 100) else null
        t.setTextColor(if (on) cPrimary else cMuted)
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun round(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    // ---------------------------------------------------------------- formatting

    private fun mins(sec: Long): String {
        val m = sec / 60
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    /** A chart value back to text: reels as a count, time (already minutes) as h/m. */
    private fun num(v: Float) = if (showTime) mins((v * 60).toLong()) else "${v.roundToInt()}"

    private fun pct(part: Int, whole: Int) = "${(part * 100f / whole).roundToInt()}%"

    private fun hourName(h: Int) = when (h) {
        0 -> "midnight"; 12 -> "noon"
        in 1..11 -> "$h AM"; else -> "${h - 12} PM"
    }

    private fun shortDate(d: DayStats) =
        "${d.date.dayOfMonth} ${d.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
