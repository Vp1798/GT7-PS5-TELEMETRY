package com.gt7.telemetry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View

/**
 * Launch screen: large bull emblem on a carbon-style texture, a yellow bar and one word.
 * [onTick] is called every frame with elapsed seconds (used to drive the engine sound).
 */
class SplashView(ctx: Context) : View(ctx) {
    var onTick: ((Float) -> Unit)? = null
    private val startMs = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgBmp: Bitmap? = null
    private val bg = 0xFF07080A.toInt()
    private val yellow = 0xFFFFD60A.toInt()

    private fun col(rgb: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
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

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (SystemClock.uptimeMillis() - startMs) / 1000f
        onTick?.invoke(t)
        val bmp = bgBmp
        if (bmp != null) c.drawBitmap(bmp, 0f, 0f, null) else c.drawColor(bg)

        val cx = w / 2
        val aBull = (t / 0.5f).coerceIn(0f, 1f)
        val aBar = ((t - 0.35f) / 0.5f).coerceIn(0f, 1f)
        val aText = ((t - 0.6f) / 0.5f).coerceIn(0f, 1f)

        // Bull: fades and scales in
        val sc = h * 0.25f * (0.88f + 0.12f * aBull)
        Emblem.draw(c, cx, h * 0.41f, sc, col(yellow, aBull), col(bg, aBull))

        // Yellow bar grows from the centre
        val bw = w * 0.32f * aBar
        paint.style = Paint.Style.FILL
        paint.color = yellow
        c.drawRect(cx - bw / 2, h * 0.75f, cx + bw / 2, h * 0.75f + h * 0.015f, paint)

        // Word
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        paint.letterSpacing = 0.45f
        paint.color = col(0xF4F4F4, aText)
        paint.textSize = h * 0.09f
        c.drawText("TELEMETRY", cx, h * 0.90f, paint)
        paint.letterSpacing = 0f

        if (t < 3f) postInvalidateOnAnimation()
    }
}
