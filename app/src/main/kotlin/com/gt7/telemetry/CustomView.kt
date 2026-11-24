package com.gt7.telemetry

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/** Draws the user's imported widgets on a second page of the dashboard. */
class CustomView(ctx: Context, private val tracker: LapTracker) : View(ctx) {
    @Volatile var data: Telemetry? = null
    @Volatile var lastPacketNs: Long = 0L
    @Volatile var widgets: List<WidgetDef> = emptyList()
    @Volatile var statusText: String = "Connecting…"

    private val cBg = 0xFF07080A.toInt()
    private val cPanel = 0xFF111418.toInt()
    private val cDim = 0xFF242830.toInt()
    private val cText = 0xFFF4F4F4.toInt()
    private val cSub = 0xFF7C838D.toInt()
    private val cRed = 0xFFFF2D3C.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private fun para(c: Canvas, l: Float, t: Float, r: Float, b: Float, skew: Float, color: Int) {
        path.reset()
        path.moveTo(l + skew, t); path.lineTo(r, t); path.lineTo(r - skew, b); path.lineTo(l, b); path.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, style: Int = Typeface.BOLD, mono: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.DEFAULT, style)
        c.drawText(s, x, y, paint)
    }

    private fun fmtNum(wd: WidgetDef, v: Float?): String {
        if (v == null) return "--"
        if (wd.source == "gear") return if (v.toInt() == 0) "R" else v.toInt().toString()
        return String.format("%." + wd.decimals + "f", v)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(cBg)
        val t = data
        val now = System.nanoTime()
        if (t == null || now - lastPacketNs > 2_000_000_000L) {
            txt(c, statusText, w / 2, h / 2, h * 0.06f, cSub, Paint.Align.CENTER, Typeface.BOLD_ITALIC)
            return
        }
        for (wd in widgets) draw(c, wd, t, w, h)
        paint.style = Paint.Style.FILL
        paint.color = cDim
        c.drawCircle(w / 2 - h * 0.015f, h * 0.985f, h * 0.006f, paint)
        paint.color = 0xFFFFD60A.toInt()
        c.drawCircle(w / 2 + h * 0.015f, h * 0.985f, h * 0.006f, paint)
    }

    private fun draw(c: Canvas, wd: WidgetDef, t: Telemetry, sw: Float, sh: Float) {
        val l = wd.x / 100f * sw
        val tp = wd.y / 100f * sh
        val r = l + wd.w / 100f * sw
        val b = tp + wd.h / 100f * sh
        val ww = r - l
        val hh = b - tp
        val skew = min(hh * 0.12f, ww * 0.08f)
        val labelSize = (hh * 0.16f).coerceIn(11f, 34f)

        if (wd.type == "text") {
            txt(c, wd.text, l + ww / 2, tp + hh / 2 + hh * 0.18f, hh * 0.5f, wd.color, Paint.Align.CENTER, Typeface.BOLD_ITALIC)
            return
        }

        val timeStr = WidgetPack.time(wd.source, t, tracker)
        val v = if (timeStr == null) WidgetPack.num(wd.source, t, tracker) else null
        var col = wd.color
        if (v != null) {
            if (wd.alertAbove != null && v > wd.alertAbove) col = cRed
            if (wd.alertBelow != null && v < wd.alertBelow) col = cRed
        }
        // Delta-style widgets: alert_above:0 turns red when slower, otherwise keep the colour.
        val valueStr = timeStr ?: fmtNum(wd, v)

        when (wd.type) {
            "light" -> {
                val on = v != null && v > 0.5f
                para(c, l, tp, r, b, skew, if (on) col else cPanel)
                txt(c, wd.label, l + ww / 2, tp + hh / 2 + labelSize * 0.35f, labelSize * 1.2f, if (on) 0xFF07080A.toInt() else cSub, Paint.Align.CENTER, Typeface.BOLD_ITALIC)
            }
            "bar" -> {
                para(c, l, tp, r, b, skew, cPanel)
                txt(c, wd.label, l + skew + ww * 0.05f, tp + labelSize * 1.4f, labelSize, cSub, Paint.Align.LEFT)
                txt(c, valueStr, r - ww * 0.05f, tp + labelSize * 1.4f, labelSize * 1.3f, col, Paint.Align.RIGHT, Typeface.BOLD, mono = true)
                val by0 = tp + hh * 0.55f
                val by1 = b - hh * 0.15f
                val x0 = l + ww * 0.08f
                val x1 = r - ww * 0.08f
                val bsk = (by1 - by0) * 0.5f
                para(c, x0, by0, x1, by1, bsk, cDim)
                val frac = if (v != null && wd.max > wd.min) ((v - wd.min) / (wd.max - wd.min)).coerceIn(0f, 1f) else 0f
                if (frac > 0.01f) para(c, x0, by0, x0 + (x1 - x0) * frac, by1, bsk, col)
            }
            "arc" -> {
                para(c, l, tp, r, b, skew, cPanel)
                val rad = min(ww, hh) * 0.40f
                val cx = l + ww / 2
                val cy = tp + hh * 0.52f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = rad * 0.16f
                rect.set(cx - rad, cy - rad, cx + rad, cy + rad)
                paint.color = cDim
                c.drawArc(rect, 135f, 270f, false, paint)
                val frac = if (v != null && wd.max > wd.min) ((v - wd.min) / (wd.max - wd.min)).coerceIn(0f, 1f) else 0f
                if (frac > 0.005f) {
                    paint.color = col
                    c.drawArc(rect, 135f, 270f * frac, false, paint)
                }
                paint.style = Paint.Style.FILL
                txt(c, valueStr, cx, cy + rad * 0.18f, rad * 0.7f, cText, Paint.Align.CENTER, Typeface.BOLD_ITALIC)
                txt(c, wd.label, cx, cy + rad * 0.85f, labelSize, cSub, Paint.Align.CENTER)
            }
            else -> { // number
                para(c, l, tp, r, b, skew, cPanel)
                txt(c, wd.label, l + skew + ww * 0.05f, tp + labelSize * 1.5f, labelSize, cSub, Paint.Align.LEFT)
                val size = min(hh * 0.5f, ww * 0.9f / (valueStr.length.coerceAtLeast(3) * 0.6f))
                txt(c, valueStr, l + ww / 2, b - hh * 0.2f, size, col, Paint.Align.CENTER, Typeface.BOLD_ITALIC, mono = timeStr != null)
            }
        }
    }
}
