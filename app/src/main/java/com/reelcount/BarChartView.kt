package com.reelcount

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Minimal vertical bar chart drawn on a Canvas.
 * - bars over [limit] use [overColor], others [barColor]; the last bar can be emphasised
 * - optional value labels above bars, category labels below
 * - optional dashed limit line
 */
class BarChartView(context: Context) : View(context) {

    var values: FloatArray = FloatArray(0)
    var labels: Array<String> = emptyArray()
    var valueLabels: Array<String>? = null
    var limit: Float = 0f
    var limitLabel: String = ""
    var emphasiseLast = true

    var barColor = 0
    var overColor = 0
    var dimColor = 0
    var textColor = 0
    var gridColor = 0

    private val d = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }
    private val rect = RectF()

    fun set(values: FloatArray, labels: Array<String>, valueLabels: Array<String>?, limit: Float, limitLabel: String) {
        this.values = values; this.labels = labels; this.valueLabels = valueLabels
        this.limit = limit; this.limitLabel = limitLabel
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val top = if (valueLabels != null) 18 * d else 6 * d
        val bottom = height - 20 * d
        val chartH = bottom - top
        val max = maxOf(values.maxOrNull() ?: 0f, limit * 1.1f, 1f)
        val slot = width.toFloat() / values.size
        val gap = if (values.size > 12) slot * 0.25f else slot * 0.32f
        val radius = minOf(4 * d, (slot - gap) / 2)

        // baseline
        line.color = gridColor; line.pathEffect = null; line.strokeWidth = 1 * d
        c.drawLine(0f, bottom, width.toFloat(), bottom, line)

        text.textSize = 11 * d
        values.forEachIndexed { i, v ->
            val x0 = i * slot + gap / 2
            val x1 = (i + 1) * slot - gap / 2
            val h = if (v <= 0f) 0f else maxOf(chartH * v / max, 2 * d)
            val isLast = i == values.lastIndex
            bar.color = when {
                limit > 0 && v >= limit -> overColor
                emphasiseLast && !isLast -> dimColor
                else -> barColor
            }
            rect.set(x0, bottom - h, x1, bottom)
            c.drawRoundRect(rect, radius, radius, bar)
            // square off the bottom corners
            if (h > radius) c.drawRect(x0, bottom - radius, x1, bottom, bar)

            text.color = textColor
            text.isFakeBoldText = isLast && emphasiseLast
            valueLabels?.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let {
                c.drawText(it, (x0 + x1) / 2, bottom - h - 5 * d, text)
            }
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let {
                c.drawText(it, (x0 + x1) / 2, height - 5 * d, text)
            }
        }
        text.isFakeBoldText = false

        // limit line
        if (limit > 0) {
            val y = bottom - chartH * limit / max
            line.color = overColor; line.strokeWidth = 1.5f * d
            line.pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
            c.drawLine(0f, y, width.toFloat(), y, line)
            if (limitLabel.isNotEmpty()) {
                text.color = overColor; text.textAlign = Paint.Align.RIGHT; text.textSize = 10 * d
                c.drawText(limitLabel, width.toFloat(), y - 4 * d, text)
                text.textAlign = Paint.Align.CENTER
            }
        }
    }
}
