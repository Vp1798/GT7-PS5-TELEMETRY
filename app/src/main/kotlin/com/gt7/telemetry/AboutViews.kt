package com.gt7.telemetry

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Small yellow bull emblem for the About screen. */
class EmblemView(ctx: Context) : View(ctx) {
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) * 0.45f
        Emblem.draw(c, width / 2f, height / 2f + s * 0.05f, s, 0xFFFFD60A.toInt(), 0xFF07080A.toInt())
    }
}

/**
 * Generic "code" glyph (circle with angle brackets) shown next to the GitHub link.
 * If you add the official GitHub mark as res/drawable/github_mark.png, the About screen shows that instead.
 */
class CodeGlyphView(ctx: Context, private val fg: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2f - 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.10f
        p.color = fg
        c.drawCircle(w / 2, h / 2, r, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = r * 0.85f
        c.drawText("</>", w / 2, h / 2 + r * 0.30f, p)
    }
}
