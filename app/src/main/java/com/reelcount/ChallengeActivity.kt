package com.reelcount

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Friends' challenge: start, join by link or code, live leaderboard, end-by-agreement, results. */
class ChallengeActivity : BaseActivity() {

    private val prefs by lazy { Prefs.get(this) }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private var state: ChallengeState? = null
    private var busy = false
    private var pendingJoin: String? = null
    private var lastError: String? = null

    private val cPrimary by lazy { getColor(R.color.text_primary) }
    private val cBody by lazy { getColor(R.color.text_body) }
    private val cMuted by lazy { getColor(R.color.text_muted) }
    private val cCard by lazy { getColor(R.color.card) }
    private val cAccent by lazy { getColor(R.color.accent) }
    private val cOver by lazy { getColor(R.color.chart_over) }
    private val cGood by lazy { getColor(R.color.good) }
    private val cTrack by lazy { getColor(R.color.track) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }
        root.addView(text("Challenge", 28f, cPrimary, bold = true))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })
        pendingJoin = codeFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingJoin = codeFrom(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        refresh()
        main.postDelayed(poller, 30_000)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(poller)
    }

    private val poller = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 30_000) }
    }

    /** Invite links look like https://…/join.html?c=ABC123 or reelcounter://join/ABC123 */
    private fun codeFrom(i: Intent?): String? {
        val uri = i?.data ?: return null
        val raw = uri.getQueryParameter("c") ?: uri.lastPathSegment ?: return null
        return raw.uppercase().filter { it.isLetterOrDigit() }.takeIf { it.length == 6 }
    }

    private fun refresh() {
        if (!Cloud.configured || Challenge.code(prefs) == null || busy) return
        Cloud.run({ Challenge.sync(this) }) { r ->
            r.onSuccess { state = it; lastError = null }
            r.onFailure { lastError = friendly(it) }
            render()
        }
    }

    // ---------------------------------------------------------------- screens

    private fun render() {
        content.removeAllViews()
        if (!Cloud.configured) {
            content.addView(card().apply {
                addView(text("Challenge mode isn't available in this build yet.", 16f, cPrimary, bold = true))
                addView(text("It needs an online server. Once the app is connected to one, you'll be able to challenge friends here.",
                    14f, cBody).apply { setPadding(0, dp(6), 0, 0) })
            })
            return
        }
        lastError?.let { content.addView(banner(it)) }

        val code = Challenge.code(prefs)
        pendingJoin?.let { join ->
            pendingJoin = null
            if (join != code) { renderLobby(); startJoin(join); return }
        }
        if (code == null) renderLobby() else renderChallenge(code)
    }

    private fun renderLobby() {
        content.addView(hero().apply {
            addView(text("Challenge your friends", 24f, Color.WHITE, bold = true))
            addView(text("Compete to watch the fewest reels. The challenge runs until 11:59 PM tonight, and the results are published right after.",
                15f, Color.WHITE).apply { alpha = 0.92f; setPadding(0, dp(6), 0, dp(16)) })
            addView(whiteButton("Start a challenge") { startCreate() })
        })

        content.addView(section("HAVE AN INVITE CODE?"))
        content.addView(card().apply {
            val input = EditText(this@ChallengeActivity).apply {
                hint = "ABC123"
                filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(6))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                textSize = 20f
                letterSpacing = 0.2f
                gravity = Gravity.CENTER
                setTextColor(cPrimary)
                background = round(cTrack, 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            addView(input, LinearLayout.LayoutParams(-1, dp(52)))
            addView(primaryButton("Join challenge") {
                val c = input.text.toString().trim().uppercase()
                if (c.length != 6) toast("Enter the 6-character code from your invite.") else startJoin(c)
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        })

        content.addView(section("HOW IT WORKS"))
        content.addView(card().apply {
            listOf(
                "Start a challenge and share the invite link with your friends.",
                "The challenge begins as soon as a friend accepts.",
                "Whoever watches the fewest reels by 11:59 PM wins. A tie goes to less scrolling time.",
                "Reset is locked until the challenge ends.",
            ).forEachIndexed { i, s -> addView(step(i + 1, s, cTrack, cPrimary, cBody)) }
        })
    }

    private fun renderChallenge(code: String) {
        val st = state
        if (st == null) {
            content.addView(card().apply { addView(text("Loading challenge $code…", 15f, cBody)) })
            return
        }
        val uid = Cloud.uidOrNull(this)
        val me = st.member(uid)
        val live = st.status == "active" && !st.finished
        val (headline, sub) = when {
            st.status == "ended" -> "Ended early" to "Every player agreed to end the challenge."
            st.finished -> "Finished" to "The results are in."
            st.status == "waiting" -> "Waiting for friends" to "The challenge starts as soon as someone accepts your invite."
            else -> "Live until 11:59 PM" to "Ends in ${countdown(st.endsAt)}"
        }

        // Hero
        content.addView(hero().apply {
            addView(text("CHALLENGE  ·  $code", 12f, Color.WHITE, bold = true).apply { alpha = 0.85f; letterSpacing = 0.08f })
            addView(text(headline, 28f, Color.WHITE, bold = true).apply { setPadding(0, dp(4), 0, 0) })
            addView(text(sub, 15f, Color.WHITE).apply { alpha = 0.92f })
            if (me != null && st.status != "waiting") {
                addView(text("You: ${Challenge.myReels(prefs)} reels  ·  ${mins(Challenge.mySeconds(prefs))}", 16f, Color.WHITE, bold = true)
                    .apply { setPadding(0, dp(12), 0, 0) })
            }
            if (st.status == "waiting" || live) {
                addView(whiteButton("Share invite link") { share(code) },
                    LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) })
            }
        })

        // Results
        if (st.finished && st.status != "ended" && st.ranking.isNotEmpty()) {
            val w = st.ranking.first()
            content.addView(section("RESULTS"))
            content.addView(card().apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat(); setColor(cCard); setStroke(dp(2), cAccent)
                }
                addView(text("WINNER", 12f, cAccent, bold = true).apply { letterSpacing = 0.08f })
                addView(text(if (w.uid == uid) "You won!" else w.name, 26f, cPrimary, bold = true))
                addView(text("${w.reels} reels  ·  ${mins(w.seconds)}", 15f, cBody))
                addView(text(Challenge.resultText(st, uid), 14f, cMuted).apply { setPadding(0, dp(8), 0, 0) })
            })
        }

        // Leaderboard
        content.addView(section(if (st.finished || st.status == "ended") "FINAL STANDINGS" else "LEADERBOARD"))
        content.addView(card().apply {
            st.ranking.forEachIndexed { i, m -> addView(playerRow(i + 1, m, m.uid == uid, live)) }
            if (st.members.size == 1) addView(text("No one has joined yet.", 14f, cMuted).apply { setPadding(0, dp(8), 0, 0) })
        })

        // End early (needs everyone to agree)
        if (live && me != null) {
            content.addView(section("END EARLY"))
            content.addView(card().apply {
                val others = st.members.size
                if (me.endVote) {
                    addView(text("You asked to end the challenge. ${st.endVotes} of $others players agree so far.", 15f, cBody))
                    addView(softButton("Cancel my request") { vote(false) },
                        LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
                } else {
                    addView(text("The challenge only ends early if every player agrees. Once it ends, Reset is unlocked.", 15f, cBody))
                    if (st.endVotes > 0) addView(text("${st.endVotes} of $others players want to end it.", 14f, cOver, bold = true)
                        .apply { setPadding(0, dp(6), 0, 0) })
                    addView(softButton("Ask to end challenge") { confirmVote() },
                        LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
                }
            })
        }

        // Leave / cancel
        when {
            st.status == "waiting" && st.createdBy == uid ->
                content.addView(softButton("Cancel challenge") { cancelWaiting() },
                    LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(18) })
            !Challenge.isLocked(prefs) ->
                content.addView(softButton("Close challenge") {
                    Challenge.clear(prefs); state = null; render()
                }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(18) })
        }
    }

    private fun playerRow(rank: Int, m: Member, isMe: Boolean, live: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(text("$rank", 14f, if (rank == 1) Color.WHITE else cPrimary, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (rank == 1) cAccent else cTrack) }
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        addView(LinearLayout(this@ChallengeActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(text(if (isMe) "${m.name} (you)" else m.name, 16f, cPrimary, bold = true))
            val bits = mutableListOf<String>()
            if (live && m.updatedAt > 0) bits += "synced ${ago(m.updatedAt)}"
            if (m.endVote && live) bits += "wants to end"
            if (bits.isNotEmpty()) addView(text(bits.joinToString("  ·  "), 12f, if (m.endVote && live) cOver else cMuted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(LinearLayout(this@ChallengeActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(text("${m.reels} reels", 16f, cPrimary, bold = true).apply { gravity = Gravity.END })
            addView(text(mins(m.seconds), 12f, cMuted).apply { gravity = Gravity.END })
        })
    }

    // ---------------------------------------------------------------- flows

    private fun startCreate() {
        showRules(inviter = null) { ensureName { create() } }
    }

    private fun create() {
        askNotificationPermission()
        work("Creating challenge…", { Challenge.create(this) }) { st ->
            state = st; render(); share(st.code)
        }
    }

    private fun startJoin(code: String) {
        work("Opening invite…", { Challenge.fetch(this, code) }) { st ->
            when {
                st == null -> toast("No challenge found with code $code.")
                st.finished -> toast("This challenge has already finished.")
                st.status == "ended" -> toast("This challenge was ended by its players.")
                else -> showRules(inviter = st.creatorName()) { ensureName { join(code) } }
            }
        }
    }

    private fun join(code: String) {
        askNotificationPermission()
        work("Joining…", { Challenge.join(this, code) }) { st ->
            state = st; render(); toast("You're in! The challenge is live.")
        }
    }

    private fun vote(v: Boolean) = work(null, { Challenge.voteEnd(this, v) }) { st -> state = st; render() }

    private fun confirmVote() {
        AlertDialog.Builder(this)
            .setTitle("Ask to end the challenge?")
            .setMessage("The challenge ends only when every player agrees. If they all do, there's no winner and Reset is unlocked for everyone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Ask to end") { _, _ -> vote(true) }
            .show()
    }

    private fun cancelWaiting() {
        work(null, { Challenge.cancelWaiting(this) }) { state = null; render() }
    }

    /** The highlighted instructions shown before starting or accepting a challenge. */
    private fun showRules(inviter: String?, onAgree: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        if (inviter != null) {
            box.addView(text("$inviter invited you to a challenge.", 16f, cPrimary, bold = true)
                .apply { setPadding(0, 0, 0, dp(10)) })
        }
        box.addView(text("Please read these rules before you continue.", 14f, cBody).apply { setPadding(0, 0, 0, dp(10)) })
        val highlight = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(30, Color.red(cAccent), Color.green(cAccent), Color.blue(cAccent)))
                setStroke(dp(2), cAccent)
            }
            setPadding(dp(14), dp(6), dp(14), dp(12))
        }
        val rules = listOf(
            "Whoever watches the fewest reels by 11:59 PM tonight wins. If it's a tie, less scrolling time wins.",
            "Once the challenge starts, you can't reset your count until 11:59 PM.",
            "You can end the challenge early only if every player agrees. Ending it unlocks Reset.",
            "Keep Reel Counter turned on. Reels watched while it's off aren't counted, and other players can see when you last synced.",
            "Only your nickname, reel count and scrolling time are shared, and only with the players in this challenge.",
        )
        rules.forEachIndexed { i, s ->
            val important = i == 1
            highlight.addView(step(i + 1, s,
                if (important) cOver else cAccent, Color.WHITE,
                if (important) cOver else cPrimary, bold = important))
        }
        box.addView(highlight)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Challenge rules")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I agree") { _, _ -> onAgree() }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(cAccent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(cMuted)
    }

    private fun ensureName(then: () -> Unit) {
        Challenge.name(prefs)?.let { then(); return }
        val input = EditText(this).apply {
            hint = "Your nickname"
            filters = arrayOf(InputFilter.LengthFilter(20))
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Choose a nickname")
            .setMessage("Players in your challenge will see this name.")
            .setView(LinearLayout(this).apply { setPadding(dp(22), 0, dp(22), 0); addView(input, LinearLayout.LayoutParams(-1, -2)) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isEmpty()) toast("Please enter a nickname.") else { Challenge.setName(prefs, n); then() }
            }
            .show()
    }

    private fun share(code: String) {
        val msg = "Join my Reel Counter challenge! Whoever watches the fewest reels by 11:59 PM tonight wins.\n\n" +
            "Tap to join: ${Challenge.JOIN_URL}$code\n\n" +
            "Or open Reel Counter, go to Challenge and enter the code: $code"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg)
        }, "Share invite"))
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    private fun <T> work(label: String?, task: () -> T, done: (T) -> Unit) {
        if (busy) return
        busy = true
        label?.let { toast(it) }
        Cloud.run(task) { r ->
            busy = false
            r.onSuccess { lastError = null; done(it) }
            r.onFailure { toast(friendly(it)) }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is IllegalStateException -> e.message ?: "Something went wrong."
        is Cloud.CloudException -> if (e.code == 401 || e.code == 403) "The server refused this request." else "Server error (${e.code}). Please try again."
        is java.io.IOException -> "No internet connection. Please check your network and try again."
        else -> e.message ?: "Something went wrong."
    }

    // ---------------------------------------------------------------- small views

    private fun hero() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = getDrawable(R.drawable.hero_bg)
        elevation = dp(6).toFloat()
        setPadding(dp(20), dp(20), dp(20), dp(20))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(cCard, 20)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun banner(msg: String) = text(msg, 14f, cOver, bold = true).apply {
        background = round(Color.argb(30, 255, 59, 48), 14)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun section(s: String) = text(s, 13f, cMuted, bold = true).apply {
        letterSpacing = 0.06f
        setPadding(dp(6), dp(22), 0, 0)
    }

    private fun step(n: Int, s: String, circle: Int, circleText: Int, body: Int, bold: Boolean = false) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(2))
            addView(text("$n", 12f, circleText, bold = true).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(circle) }
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(text(s, 15f, body, bold = bold).apply { setPadding(dp(12), dp(1), 0, 0) },
                LinearLayout.LayoutParams(0, -2, 1f))
        }

    private fun whiteButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#6C5CE7"))
        background = getDrawable(R.drawable.btn_white)
        stateListAnimator = null
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48))
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        background = getDrawable(R.drawable.btn_primary)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun softButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(cPrimary)
        background = getDrawable(R.drawable.btn_soft)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setLineSpacing(0f, 1.1f)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun mins(sec: Long): String { val m = sec / 60; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }

    private fun countdown(endsAt: Long): String {
        val left = ((endsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        return if (left >= 60) "${left / 60}h ${left % 60}m" else "${left}m"
    }

    private fun ago(t: Long): String {
        val m = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
        return when { m < 1 -> "just now"; m < 60 -> "$m min ago"; else -> "${m / 60}h ago" }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
