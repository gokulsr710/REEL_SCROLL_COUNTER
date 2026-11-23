package com.reelcount

/** How "used up" the day is: 0 = fresh, 1 = at a limit, 2 = double the limit. */
object Mood {
    fun ratio(count: Int, seconds: Long, reelLimit: Int, timeLimitMin: Int): Float {
        val r1 = if (reelLimit > 0) count.toFloat() / reelLimit else 0f
        val r2 = if (timeLimitMin > 0) seconds / (timeLimitMin * 60f) else 0f
        return maxOf(r1, r2)
    }

    fun text(r: Float): String = when {
        r < 0.5f -> "Full of energy"
        r < 0.8f -> "Doing good"
        r < 1.0f -> "Getting tired…"
        r < 1.4f -> "Feeling sad"
        else -> "Exhausted, time to rest"
    }
}
