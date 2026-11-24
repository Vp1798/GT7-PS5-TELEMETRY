package com.gt7.telemetry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Supercar-style cockpit: big segmented tach ring with the gear in a hexagon, angled side panels,
 * TCS/ASM activity lights, tyres, pedals, fuel and lap strip. Always dark for contrast.
 */
class DashboardView(ctx: Context, private val tracker: LapTracker) : View(ctx) {
    @Volatile var data: Telemetry? = null
    @Volatile var lastPacketNs: Long = 0L
    @Volatile var statusText: String = "Connecting…"
    @Volatile var hasCustomPage: Boolean = false
    @Volatile var soundOn: Boolean = true

    private val cBg = 0xFF07080A.toInt()
    private val cPanel = 0xFF111418.toInt()
    private val cDim = 0xFF242830.toInt()
    private val cText = 0xFFF4F4F4.toInt()
    private val cSub = 0xFF7C838D.toInt()
    private val cAccent = 0xFFFFD60A.toInt()
    private val cOrange = 0xFFFF7A00.toInt()
    private val cRed = 0xFFFF2D3C.toInt()
    private val cGreen = 0xFF2EE66B.toInt()
    private val cCyan = 0xFF19D3FF.toInt()
    private val cBlue = 0xFF3B8BEB.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var bgBmp: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(cBg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x12FFFFFF
        p.strokeWidth = 2f
        var x = -h
        while (x < w) {
            c.drawLine(x.toFloat(), h.toFloat(), (x + h).toFloat(), 0f, p)
            x += 10
        }
        bgBmp = bmp
    }


    private fun para(c: Canvas, l: Float, t: Float, r: Float, b: Float, skew: Float, color: Int) {
        path.reset()
        path.moveTo(l + skew, t); path.lineTo(r, t); path.lineTo(r - skew, b); path.lineTo(l, b); path.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun bar(c: Canvas, l: Float, t: Float, r: Float, b: Float, frac: Float, color: Int) {
        val sk = (b - t) * 0.5f
        para(c, l, t, r, b, sk, cDim)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0.01f) para(c, l, t, l + (r - l) * f, b, sk, color)
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, rad: Float) {
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, paint)
    }

    private fun txt(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align,
        style: Int = Typeface.NORMAL, mono: Boolean = false, spacing: Float = 0f,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.letterSpacing = spacing
        paint.typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.DEFAULT, style)
        c.drawText(s, x, y, paint)
        paint.letterSpacing = 0f
    }

    private fun fmt(ms: Int): String =
        if (ms <= 0) "-:--.---" else String.format("%d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000)

    private fun tyreColor(t: Float): Int = when {
        t < 60f -> cBlue
        t < 95f -> cGreen
        t < 110f -> cOrange
        else -> cRed
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bmp = bgBmp
        if (bmp != null) c.drawBitmap(bmp, 0f, 0f, null) else c.drawColor(cBg)
        val t = data
        val now = System.nanoTime()
        if (t == null || now - lastPacketNs > 2_000_000_000L) {
            txt(c, statusText, w / 2, h / 2, h * 0.06f, cSub, Paint.Align.CENTER, Typeface.BOLD_ITALIC, spacing = 0.05f)
            return
        }

        val cx = w / 2
        val cy = h * 0.52f
        val r = min(h * 0.46f, w * 0.28f)
        val maxR = if (t.maxAlertRpm > 0) t.maxAlertRpm.toFloat() else 7500f
        val minR = if (t.minAlertRpm > 0) t.minAlertRpm.toFloat() else maxR * 0.85f
        val scaleMax = (ceil(maxR * 1.08f / 1000f) * 1000f).coerceAtLeast(4000f)
        val flash = t.revLimiter || t.rpm >= maxR * 0.99f
        val flashOn = (now / 80_000_000L) % 2L == 0L

        // Glow behind the tach
        val glow = if (flash && flashOn) 0x44FF2D3C else 0x22FFD60A
        paint.shader = RadialGradient(cx, cy, r * 1.25f, glow, 0x00000000, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r * 1.25f, paint)
        paint.shader = null

        // Segmented tach ring (270 degrees, open at the bottom)
        val n = 54
        val frac = (t.rpm / scaleMax).coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = r * 0.05f
        for (i in 0 until n) {
            val f = i / (n - 1).toFloat()
            val a = (135.0 + 270.0 * f) * PI / 180.0
            val rpmAt = f * scaleMax
            val lit = f <= frac
            val col = when {
                !lit -> if (rpmAt >= minR) 0xFF3A1519.toInt() else cDim
                rpmAt >= maxR * 0.97f -> cRed
                rpmAt >= minR -> cOrange
                else -> cAccent
            }
            paint.color = if (flash && flashOn) cRed else col
            val long = i % 6 == 0
            val r0 = r * (if (long) 0.80f else 0.84f)
            val r1 = r * 0.97f
            c.drawLine(
                (cx + cos(a) * r0).toFloat(), (cy + sin(a) * r0).toFloat(),
                (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(), paint,
            )
        }
        // Thin outer rail
        paint.strokeWidth = r * 0.012f
        paint.color = 0xFF3A3F48.toInt()
        rect.set(cx - r * 1.01f, cy - r * 1.01f, cx + r * 1.01f, cy + r * 1.01f)
        c.drawArc(rect, 135f, 270f, false, paint)
        paint.style = Paint.Style.FILL

        // Tach numbers
        var k = 0
        while (k * 1000f <= scaleMax) {
            val a = (135.0 + 270.0 * (k * 1000f / scaleMax)) * PI / 180.0
            val tx = (cx + cos(a) * r * 0.69f).toFloat()
            val ty = (cy + sin(a) * r * 0.69f).toFloat()
            txt(c, k.toString(), tx, ty + r * 0.03f, r * 0.085f, if (k * 1000f >= maxR * 0.97f) cRed else cSub, Paint.Align.CENTER, Typeface.BOLD)
            k++
        }

        // Hexagon with gear
        val hy = cy - r * 0.10f
        val hr = r * 0.40f
        path.reset()
        for (i in 0 until 6) {
            val a = (-90.0 + 60.0 * i) * PI / 180.0
            val px = (cx + cos(a) * hr).toFloat()
            val py = (hy + sin(a) * hr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = 0xCC0B0D10.toInt()
        c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.014f
        paint.color = if (flash && flashOn) cRed else cAccent
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        val gearStr = if (t.gear == 0) "R" else t.gear.toString()
        val sugg = t.suggestedGear
        val gearCol = if (sugg in 1..14 && sugg != t.gear) cOrange else cText
        txt(c, gearStr, cx, hy + r * 0.20f, r * 0.55f, gearCol, Paint.Align.CENTER, Typeface.BOLD_ITALIC)
        txt(c, String.format("%.0f", t.rpm), cx, cy - r * 0.55f, r * 0.10f, cSub, Paint.Align.CENTER, Typeface.BOLD, mono = true)

        // Speed
        txt(c, String.format("%.0f", t.speedKmh), cx, cy + r * 0.50f, r * 0.24f, cText, Paint.Align.CENTER, Typeface.BOLD_ITALIC)
        txt(c, "KM/H", cx, cy + r * 0.61f, r * 0.065f, cSub, Paint.Align.CENTER, Typeface.BOLD, spacing = 0.1f)

        // Delta bar and number in the open bottom of the ring
        val d = tracker.deltaSec
        val dCol = if (d == null) cSub else if (d <= 0) cGreen else cRed
        val bw = r * 0.55f
        val by = cy + r * 0.68f
        val bh = r * 0.045f
        rr(c, cx - bw, by, cx + bw, by + bh, cDim, bh / 2)
        if (d != null) {
            val f = (abs(d) / 2.0).toFloat().coerceIn(0f, 1f)
            if (d <= 0) rr(c, cx - bw * f, by, cx, by + bh, cGreen, bh / 2)
            else rr(c, cx, by, cx + bw * f, by + bh, cRed, bh / 2)
        }
        rr(c, cx - 2f, by - bh * 0.4f, cx + 2f, by + bh * 1.4f, cText, 2f)
        txt(c, if (d == null) "--.--" else String.format("%+.2f", d), cx, cy + r * 0.93f, r * 0.20f, dCol, Paint.Align.CENTER, Typeface.BOLD_ITALIC, mono = true)

        // ---- Left panel: tyres, TCS/ASM, brake, temps
        val m = h * 0.04f
        val lx0 = m
        val lx1 = (cx - r * 1.08f).coerceAtLeast(lx0 + h * 0.3f)
        val pw = lx1 - lx0
        para(c, lx0, h * 0.05f, lx1, h * 0.52f, h * 0.03f, cPanel)
        txt(c, "TYRES", lx0 + h * 0.05f, h * 0.10f, h * 0.035f, cSub, Paint.Align.LEFT, Typeface.BOLD, spacing = 0.15f)
        val tw = (pw - h * 0.16f) / 2
        val th = h * 0.15f
        for (i in 0 until 4) {
            val l = lx0 + h * 0.06f + (i % 2) * (tw + h * 0.03f)
            val tp = h * 0.13f + (i / 2) * (th + h * 0.03f)
            rr(c, l, tp, l + tw, tp + th, tyreColor(t.tyres[i]), 10f)
            txt(c, String.format("%.0f", t.tyres[i]), l + tw / 2, tp + th * 0.66f, th * 0.5f, 0xFF07080A.toInt(), Paint.Align.CENTER, Typeface.BOLD_ITALIC)
        }
        // TCS / ASM lights
        val by2 = h * 0.57f
        val bw2 = (pw - h * 0.16f) / 2
        val lit1 = t.tcsActive
        val lit2 = t.asmActive
        val l1 = lx0 + h * 0.06f
        val l2 = l1 + bw2 + h * 0.03f
        para(c, l1, by2, l1 + bw2, by2 + h * 0.085f, h * 0.02f, if (lit1) cOrange else cPanel)
        para(c, l2, by2, l2 + bw2, by2 + h * 0.085f, h * 0.02f, if (lit2) cCyan else cPanel)
        txt(c, "TCS", l1 + bw2 / 2, by2 + h * 0.058f, h * 0.045f, if (lit1) 0xFF07080A.toInt() else cSub, Paint.Align.CENTER, Typeface.BOLD_ITALIC, spacing = 0.1f)
        txt(c, "ASM", l2 + bw2 / 2, by2 + h * 0.058f, h * 0.045f, if (lit2) 0xFF07080A.toInt() else cSub, Paint.Align.CENTER, Typeface.BOLD_ITALIC, spacing = 0.1f)
        // Brake
        txt(c, "BRAKE", lx0 + h * 0.06f, h * 0.73f, h * 0.032f, cSub, Paint.Align.LEFT, Typeface.BOLD, spacing = 0.15f)
        bar(c, lx0 + h * 0.06f, h * 0.745f, lx1 - h * 0.04f, h * 0.805f, t.brake, cRed)
        // Emblem
        Emblem.draw(c, lx0 + pw * 0.5f, h * 0.915f, h * 0.07f, cAccent, cPanel)

        // ---- Right panel: lap, times, fuel, throttle
        val rx0 = (cx + r * 1.08f).coerceAtMost(w - m - h * 0.3f)
        val rx1 = w - m
        para(c, rx0, h * 0.05f, rx1, h * 0.60f, h * 0.03f, cPanel)
        val lapStr = if (t.laps > 0) "LAP ${t.lap}/${t.laps}" else "LAP ${t.lap}"
        txt(c, lapStr, rx0 + h * 0.06f, h * 0.12f, h * 0.06f, cAccent, Paint.Align.LEFT, Typeface.BOLD_ITALIC, spacing = 0.08f)
        val cur = (tracker.curLapSec * 1000).toInt()
        val labels = arrayOf("CURRENT", "LAST", "BEST")
        val vals = arrayOf(fmt(cur), fmt(t.lastMs), fmt(t.bestMs))
        for (i in 0 until 3) {
            val y = h * 0.22f + i * h * 0.125f
            txt(c, labels[i], rx0 + h * 0.06f, y, h * 0.03f, cSub, Paint.Align.LEFT, Typeface.BOLD, spacing = 0.15f)
            txt(c, vals[i], rx0 + h * 0.06f, y + h * 0.065f, h * 0.062f, if (i == 2) cAccent else cText, Paint.Align.LEFT, Typeface.BOLD, mono = true)
        }
        // Fuel
        val fp = if (t.fuelCap > 0f) (t.fuel / t.fuelCap).coerceIn(0f, 1f) else 0f
        val ll = tracker.lapsLeft
        val fuelStr = String.format("FUEL %.0f%%", fp * 100f) + if (ll >= 0f) String.format("  ~%.1f LAPS", ll) else ""
        txt(c, fuelStr, rx0 + h * 0.06f, h * 0.665f, h * 0.034f, cSub, Paint.Align.LEFT, Typeface.BOLD, mono = true)
        bar(c, rx0 + h * 0.06f, h * 0.68f, rx1 - h * 0.04f, h * 0.72f, fp, if (fp < 0.15f) cRed else cBlue)
        // Throttle
        txt(c, "THROTTLE", rx0 + h * 0.06f, h * 0.775f, h * 0.032f, cSub, Paint.Align.LEFT, Typeface.BOLD, spacing = 0.15f)
        bar(c, rx0 + h * 0.06f, h * 0.79f, rx1 - h * 0.04f, h * 0.85f, t.throttle, cGreen)
        val oil = String.format("OIL %.0f°", t.oilTemp)
        val wat = String.format("WATER %.0f°", t.waterTemp)
        txt(c, "$oil   $wat", rx0 + h * 0.06f, h * 0.90f, h * 0.036f, cSub, Paint.Align.LEFT, Typeface.BOLD, mono = true)
        if (t.hasTurbo) {
            txt(c, String.format("BOOST %.2f BAR", t.boost.coerceAtLeast(0f)), rx0 + h * 0.06f, h * 0.95f, h * 0.036f, cSub, Paint.Align.LEFT, Typeface.BOLD, mono = true)
        }

        txt(c, if (soundOn) "SOUND ON" else "SOUND OFF", w - h * 0.04f, h * 0.04f, h * 0.03f, if (soundOn) cAccent else cSub, Paint.Align.RIGHT, Typeface.BOLD, spacing = 0.1f)

        if (hasCustomPage) {
            paint.style = Paint.Style.FILL
            paint.color = cAccent
            c.drawCircle(cx - h * 0.015f, h * 0.985f, h * 0.006f, paint)
            paint.color = cDim
            c.drawCircle(cx + h * 0.015f, h * 0.985f, h * 0.006f, paint)
        }

        if (t.paused) txt(c, "PAUSED", cx, cy - r * 0.62f, r * 0.12f, cOrange, Paint.Align.CENTER, Typeface.BOLD_ITALIC, spacing = 0.1f)
    }
}
