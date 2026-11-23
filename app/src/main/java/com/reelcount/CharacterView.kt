package com.reelcount

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * "Sprout": a little round blob with a leaf on its head.
 * Its mood follows [setRatio] (0 = fresh, 1 = at limit, 2 = way over):
 *   bouncy & smiling → worried (sweat drop) → sad (frown, tears) → exhausted (droopy, grey, z's).
 * The displayed mood drifts slowly toward the target so the change feels gradual.
 */
class CharacterView(context: Context) : View(context) {

    private var target = 0f
    private var shown = 0f
    private val born = SystemClock.uptimeMillis()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val path = Path()
    private val oval = RectF()
    private val argb = ArgbEvaluator()

    /** Set the usage ratio. [immediate] skips the slow drift (e.g. first show). */
    fun setRatio(r: Float, immediate: Boolean = false) {
        target = r.coerceIn(0f, 2.5f)
        if (immediate) shown = target
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        shown += (target - shown) * 0.02f          // slow drift toward the new mood
        val r = shown
        val energy = 1f - smooth(0.7f, 1.6f, r)     // 1 = lively, 0 = drained
        val sad = smooth(0.9f, 1.3f, r)
        val tired = smooth(1.25f, 1.85f, r)
        val worried = smooth(0.7f, 0.95f, r) * (1f - sad)

        val w = width.toFloat(); val h = height.toFloat()
        val s = min(w, h)
        val t = (SystemClock.uptimeMillis() - born) / 1000f

        // ---- hop
        val period = lerp(0.75f, 2.8f, 1f - energy)
        val hop = abs(sin(t / period * PI.toFloat()))
        val bounce = hop * lerp(0.11f, 0.012f, 1f - energy) * s
        val squash = if (hop < 0.15f) (0.15f - hop) / 0.15f * 0.06f * energy else 0f

        // ---- body geometry
        val bw = s * 0.74f * (1f + 0.10f * tired + squash)
        val bh = s * 0.60f * (1f - 0.12f * tired - squash)
        val cx = w / 2f
        val bottom = h - s * 0.08f - bounce
        val top = bottom - bh

        val bodyColor = mix(mix(YELLOW, AMBER, sad * 0.6f), GREY, tired * 0.85f)

        // shadow
        fill.color = Color.argb((40 * (1f - hop * 0.5f)).toInt(), 0, 0, 0)
        val shW = bw * (0.42f - hop * 0.12f)
        oval.set(cx - shW, h - s * 0.10f, cx + shW, h - s * 0.04f)
        c.drawOval(oval, fill)

        // sprout (stem + leaf); droops as energy drops
        c.save()
        c.rotate(lerp(-8f, 70f, 1f - energy) + sin(t * 2.2f) * 6f * energy, cx, top + s * 0.02f)
        stroke.color = mix(GREEN_DARK, GREY_DARK, tired * 0.8f)
        stroke.strokeWidth = s * 0.035f
        c.drawLine(cx, top + s * 0.02f, cx, top - s * 0.12f, stroke)
        fill.color = mix(GREEN, GREY_LEAF, tired * 0.8f)
        c.save()
        c.rotate(-35f, cx, top - s * 0.12f)
        oval.set(cx - s * 0.01f, top - s * 0.18f, cx + s * 0.15f, top - s * 0.10f)
        c.drawOval(oval, fill)
        c.restore()
        c.restore()

        // body
        fill.color = bodyColor
        oval.set(cx - bw / 2, top, cx + bw / 2, bottom)
        c.drawOval(oval, fill)
        // soft highlight
        fill.color = Color.argb(70, 255, 255, 255)
        oval.set(cx - bw * 0.32f, top + bh * 0.10f, cx - bw * 0.05f, top + bh * 0.30f)
        c.drawOval(oval, fill)

        // cheeks
        fill.color = Color.argb((150 * energy * (1f - sad * 0.5f)).toInt(), 255, 128, 150)
        val eyeY = top + bh * 0.44f
        val eyeDx = bw * 0.19f
        val er = s * 0.055f
        for (dir in intArrayOf(-1, 1)) {
            oval.set(cx + dir * eyeDx * 1.55f - er * 1.2f, eyeY + er * 1.1f,
                cx + dir * eyeDx * 1.55f + er * 1.2f, eyeY + er * 2.0f)
            c.drawOval(oval, fill)
        }

        // eyes (+ blink, + sleepy lids)
        val blinking = energy > 0.3f && (t % 3.7f) < 0.12f
        val lid = if (blinking) 1f else tired * 0.6f + sad * 0.15f
        for (dir in intArrayOf(-1, 1)) {
            val ex = cx + dir * eyeDx
            fill.color = INK
            c.drawCircle(ex, eyeY, er, fill)
            if (lid < 0.8f) {
                fill.color = Color.WHITE
                c.drawCircle(ex + er * 0.35f, eyeY - er * 0.35f, er * 0.35f, fill)
            }
            if (lid > 0.02f) {
                // eyelid: cover the top of the eye with body colour
                fill.color = bodyColor
                c.drawRect(ex - er * 1.3f, eyeY - er * 1.4f, ex + er * 1.3f, eyeY - er + 2 * er * lid, fill)
                stroke.color = INK; stroke.strokeWidth = s * 0.022f
                val ly = eyeY - er + 2 * er * lid
                c.drawLine(ex - er * 1.05f, ly, ex + er * 1.05f, ly, stroke)
            }
            // brows: inner ends lift when sad / worried
            val browLift = (sad + worried).coerceAtMost(1f)
            if (browLift > 0.05f) {
                stroke.color = Color.argb((255 * browLift).toInt(), 45, 42, 50)
                stroke.strokeWidth = s * 0.025f
                val by = eyeY - er * 2.0f
                val innerX = ex - dir * er * 1.0f      // toward the middle of the face
                val outerX = ex + dir * er * 1.1f
                c.drawLine(innerX, by - er * 0.8f * browLift, outerX, by + er * 0.25f * browLift, stroke)
            }
        }

        // mouth: smile → flat → frown
        val mouthY = top + bh * 0.68f
        val mw = bw * 0.13f
        val curve = lerp(1f, -0.9f, sad) - worried * 0.9f
        if (energy > 0.85f && r < 0.45f) {
            // open happy mouth
            path.reset()
            path.moveTo(cx - mw, mouthY)
            path.quadTo(cx, mouthY + mw * 1.6f, cx + mw, mouthY)
            path.close()
            fill.color = INK; c.drawPath(path, fill)
            fill.color = TONGUE
            c.drawCircle(cx, mouthY + mw * 0.55f, mw * 0.32f, fill)
        } else {
            stroke.color = INK; stroke.strokeWidth = s * 0.03f
            val mwNow = mw * (1f - tired * 0.3f)
            path.reset()
            path.moveTo(cx - mwNow, mouthY + (if (curve < 0) mw * 0.3f else 0f))
            path.quadTo(cx, mouthY + curve * mw * 0.9f + (if (curve < 0) mw * 0.3f else 0f),
                cx + mwNow, mouthY + (if (curve < 0) mw * 0.3f else 0f))
            c.drawPath(path, stroke)
        }

        // sweat drop when worried
        if (worried > 0.35f) {
            drop(c, cx + bw * 0.42f, top + bh * 0.18f + (t % 1.4f) / 1.4f * bh * 0.15f, s * 0.05f,
                (220 * worried).toInt())
        }
        // tear when sad (fades once exhausted)
        if (sad > 0.4f && tired < 0.8f) {
            val k = (t % 1.8f) / 1.8f
            drop(c, cx - eyeDx - er * 0.4f, eyeY + er * 1.2f + k * bh * 0.35f, s * 0.045f,
                (230 * sad * (1f - k) * (1f - tired)).toInt())
        }
        // floating z's when exhausted
        if (tired > 0.45f) {
            text.color = Color.argb((200 * tired).toInt(), 120, 130, 160)
            for (i in 0..1) {
                val k = ((t * 0.5f + i * 0.5f) % 1f)
                text.textSize = s * (0.14f + 0.06f * k)
                text.alpha = (200 * tired * (1f - k)).toInt()
                c.drawText("z", cx + bw * 0.38f + k * s * 0.12f, top - k * s * 0.18f, text)
            }
        }

        if (isShown) postInvalidateDelayed(40)
    }

    private fun drop(c: Canvas, x: Float, y: Float, size: Float, alpha: Int) {
        fill.color = TEAR; fill.alpha = alpha.coerceIn(0, 255)
        path.reset()
        path.moveTo(x, y - size * 1.4f)
        path.quadTo(x + size, y, x, y + size)
        path.quadTo(x - size, y, x, y - size * 1.4f)
        c.drawPath(path, fill)
        fill.alpha = 255
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return k * k * (3 - 2 * k)
    }
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun mix(a: Int, b: Int, k: Float) = argb.evaluate(k.coerceIn(0f, 1f), a, b) as Int

    companion object {
        private val YELLOW = Color.parseColor("#FFCD4D")
        private val AMBER = Color.parseColor("#F5B04F")
        private val GREY = Color.parseColor("#A9B1C2")
        private val GREEN = Color.parseColor("#5CCB6F")
        private val GREEN_DARK = Color.parseColor("#3E9E52")
        private val GREY_LEAF = Color.parseColor("#9DA89F")
        private val GREY_DARK = Color.parseColor("#7D857F")
        private val INK = Color.parseColor("#2D2A32")
        private val TONGUE = Color.parseColor("#FF7A8A")
        private val TEAR = Color.parseColor("#6EC6FF")
    }
}
