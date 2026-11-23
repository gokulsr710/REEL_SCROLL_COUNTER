package com.reelcount

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

data class Member(
    val uid: String,
    val name: String,
    val reels: Int,
    val seconds: Long,
    val updatedAt: Long,
    val endVote: Boolean,
)

data class ChallengeState(
    val code: String,
    val status: String,          // "waiting" | "active" | "ended"
    val endsAt: Long,            // epoch ms of 11:59 PM on the challenge day
    val createdBy: String,
    val members: List<Member>,
) {
    val finished: Boolean get() = System.currentTimeMillis() >= endsAt
    /** Fewest reels first; a tie goes to less scrolling time. */
    val ranking: List<Member> get() = members.sortedWith(compareBy<Member>({ it.reels }, { it.seconds }))
    val endVotes: Int get() = members.count { it.endVote }
    val allAgreedToEnd: Boolean get() = members.size >= 2 && members.all { it.endVote }
    fun member(uid: String?) = members.firstOrNull { it.uid == uid }
    fun creatorName() = member(createdBy)?.name ?: "A friend"
}

/**
 * Friends' challenge: fewest reels by 11:59 PM wins.
 * Server layout (Firebase Realtime Database):
 *   challenges/{code}: { status, endsAt, createdBy, createdAt, members/{uid}: { name, reels, seconds, endVote, joinedAt, updatedAt } }
 * Each player's challenge count starts when the challenge goes live for them.
 */
object Challenge {
    private const val CODE = "ch_code"
    private const val STATUS = "ch_status"
    private const val ENDS = "ch_ends"
    private const val BASE_R = "ch_base_r"
    private const val BASE_S = "ch_base_s"
    private const val BASE_SET = "ch_base_set"
    private const val NAME = "ch_name"
    private const val NOTIFIED = "ch_notified"
    private const val FINAL_PUSHED = "ch_final_pushed"

    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val MAX_PLAYERS = 10
    const val JOIN_URL = "https://gokulsr710.github.io/REEL_SCROLL_COUNTER/join.html?c="

    // ---------------------------------------------------------------- local state

    fun code(p: SharedPreferences): String? = p.getString(CODE, null)
    fun status(p: SharedPreferences): String = p.getString(STATUS, "waiting") ?: "waiting"
    fun endsAt(p: SharedPreferences): Long = p.getLong(ENDS, 0)
    fun name(p: SharedPreferences): String? = p.getString(NAME, null)
    fun setName(p: SharedPreferences, n: String) = p.edit().putString(NAME, n.trim().take(20)).apply()

    /** Reset (and un-ticking apps) is locked while a live challenge runs and hasn't been ended by everyone. */
    fun isLocked(p: SharedPreferences): Boolean =
        code(p) != null && status(p) == "active" && System.currentTimeMillis() < endsAt(p)

    fun myReels(p: SharedPreferences): Int =
        if (!p.getBoolean(BASE_SET, false)) 0
        else (p.getInt(Prefs.COUNT, 0) - p.getInt(BASE_R, 0)).coerceAtLeast(0)

    fun mySeconds(p: SharedPreferences): Long =
        if (!p.getBoolean(BASE_SET, false)) 0
        else (p.getLong(Prefs.SECONDS, 0) - p.getLong(BASE_S, 0)).coerceAtLeast(0)

    fun notified(p: SharedPreferences) = p.getBoolean(NOTIFIED, false)
    fun markNotified(p: SharedPreferences) = p.edit().putBoolean(NOTIFIED, true).apply()
    fun finalPushed(p: SharedPreferences) = p.getBoolean(FINAL_PUSHED, false)
    fun markFinalPushed(p: SharedPreferences) = p.edit().putBoolean(FINAL_PUSHED, true).apply()

    /** Forget the challenge on this phone (only allowed when not locked). */
    fun clear(p: SharedPreferences) {
        p.edit().remove(CODE).remove(STATUS).remove(ENDS).remove(BASE_R).remove(BASE_S)
            .remove(BASE_SET).remove(NOTIFIED).remove(FINAL_PUSHED).apply()
    }

    private fun setBaselineNow(p: SharedPreferences) {
        Prefs.rollDay(p)
        p.edit().putInt(BASE_R, p.getInt(Prefs.COUNT, 0))
            .putLong(BASE_S, p.getLong(Prefs.SECONDS, 0))
            .putBoolean(BASE_SET, true).apply()
    }

    private fun saveLocal(p: SharedPreferences, code: String, status: String, endsAt: Long, startCounting: Boolean) {
        p.edit().putString(CODE, code).putString(STATUS, status).putLong(ENDS, endsAt)
            .putBoolean(BASE_SET, false).putBoolean(NOTIFIED, false).putBoolean(FINAL_PUSHED, false).apply()
        if (startCounting) setBaselineNow(p)
    }

    fun tonightEnd(): Long =
        LocalDate.now().atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun serverTime() = JSONObject().put(".sv", "timestamp")

    private fun memberJson(name: String) = JSONObject()
        .put("name", name).put("reels", 0).put("seconds", 0).put("endVote", false)
        .put("joinedAt", serverTime()).put("updatedAt", serverTime())

    // ---------------------------------------------------------------- server calls (blocking; run via Cloud.run)

    fun fetch(c: Context, code: String): ChallengeState? {
        val o = JSONObject(Cloud.get(c, "challenges/$code") ?: return null)
        val members = mutableListOf<Member>()
        o.optJSONObject("members")?.let { m ->
            m.keys().forEach { uid ->
                val x = m.getJSONObject(uid)
                members += Member(uid, x.optString("name", "Player"), x.optInt("reels"),
                    x.optLong("seconds"), x.optLong("updatedAt"), x.optBoolean("endVote"))
            }
        }
        return ChallengeState(code, o.optString("status", "waiting"), o.optLong("endsAt"),
            o.optString("createdBy"), members)
    }

    fun create(c: Context): ChallengeState {
        val p = Prefs.get(c)
        val name = name(p) ?: throw IllegalStateException("Please choose a nickname first.")
        val ends = tonightEnd()
        if (ends - System.currentTimeMillis() < 10 * 60_000) {
            throw IllegalStateException("It's too close to 11:59 PM to start a challenge. Try again tomorrow.")
        }
        val uid = Cloud.uid(c)
        repeat(6) {
            val code = (1..6).map { ALPHABET.random() }.joinToString("")
            val body = JSONObject()
                .put("status", "waiting").put("endsAt", ends).put("createdBy", uid)
                .put("createdAt", serverTime())
                .put("members", JSONObject().put(uid, memberJson(name)))
            try {
                Cloud.put(c, "challenges/$code", body.toString())
            } catch (e: Cloud.CloudException) {
                if (e.code == 401 || e.code == 403) return@repeat   // code already taken: try another
                throw e
            }
            // The creator's count starts when a friend accepts (see sync)
            saveLocal(p, code, "waiting", ends, startCounting = false)
            return fetch(c, code) ?: throw IOException("Couldn't load the new challenge.")
        }
        throw IOException("Couldn't create a challenge. Please try again.")
    }

    fun join(c: Context, code: String): ChallengeState {
        val p = Prefs.get(c)
        val name = name(p) ?: throw IllegalStateException("Please choose a nickname first.")
        val st = fetch(c, code) ?: throw IllegalStateException("No challenge found with code $code.")
        val uid = Cloud.uid(c)
        if (st.member(uid) != null && code(p) == code) return st          // already in it
        if (st.finished) throw IllegalStateException("This challenge has already finished.")
        if (st.status == "ended") throw IllegalStateException("This challenge was ended by its players.")
        if (st.members.size >= MAX_PLAYERS) throw IllegalStateException("This challenge is full ($MAX_PLAYERS players).")
        if (isLocked(p) && code(p) != code) throw IllegalStateException("You're already in a live challenge. Finish it first.")

        Cloud.put(c, "challenges/$code/members/$uid", memberJson(name).toString())
        if (st.status == "waiting") Cloud.put(c, "challenges/$code/status", "\"active\"")
        saveLocal(p, code, "active", st.endsAt, startCounting = true)
        return fetch(c, code) ?: st
    }

    /** Pull the latest state, push my progress, and close the challenge if everyone agreed to end. */
    fun sync(c: Context): ChallengeState? {
        val p = Prefs.get(c)
        val code = code(p) ?: return null
        val st = fetch(c, code) ?: run { clear(p); return null }
        p.edit().putString(STATUS, st.status).apply()
        val uid = Cloud.uid(c)

        // Creator: start counting the moment a friend accepts
        if (st.status == "active" && !p.getBoolean(BASE_SET, false)) setBaselineNow(p)

        if (st.member(uid) != null && st.status == "active" && !st.finished) {
            Cloud.patch(c, "challenges/$code/members/$uid", JSONObject()
                .put("reels", myReels(p)).put("seconds", mySeconds(p))
                .put("updatedAt", serverTime()).toString())
        }
        if (st.allAgreedToEnd && st.status != "ended") {
            Cloud.put(c, "challenges/$code/status", "\"ended\"")
        }
        val fresh = fetch(c, code) ?: st
        p.edit().putString(STATUS, fresh.status).apply()
        return fresh
    }

    fun voteEnd(c: Context, vote: Boolean): ChallengeState? {
        val p = Prefs.get(c)
        val code = code(p) ?: return null
        Cloud.put(c, "challenges/$code/members/${Cloud.uid(c)}/endVote", vote.toString())
        return sync(c)
    }

    /** Creator cancels a challenge nobody has joined yet. */
    fun cancelWaiting(c: Context) {
        val p = Prefs.get(c)
        val code = code(p) ?: return
        Cloud.put(c, "challenges/$code/status", "\"ended\"")
        clear(p)
    }

    /** Short human summary for the results notification / card. */
    fun resultText(st: ChallengeState, myUid: String?): String {
        val r = st.ranking
        if (r.isEmpty()) return "The challenge has finished."
        val w = r.first()
        val winner = if (w.uid == myUid) "You won" else "${w.name} won"
        val place = r.indexOfFirst { it.uid == myUid }
        val mine = if (place > 0) " You finished ${ordinal(place + 1)} of ${r.size}." else ""
        return "$winner with ${w.reels} reels.$mine"
    }

    fun ordinal(n: Int) = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"
        else -> "th"
    }
}
