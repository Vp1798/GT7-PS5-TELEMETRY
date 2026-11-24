package com.gt7.telemetry

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Original angular bull-head emblem. Unit coordinates: x -1..1, y -1.05..0.95, mirrored left/right. */
object Emblem {
    private val head = floatArrayOf(-0.42f, -0.30f, 0.42f, -0.30f, 0.55f, 0.05f, 0.30f, 0.75f, 0.14f, 0.95f, -0.14f, 0.95f, -0.30f, 0.75f, -0.55f, 0.05f)
    private val ear = floatArrayOf(0.45f, -0.12f, 0.80f, -0.22f, 0.62f, 0.08f)
    private val horn = floatArrayOf(0.40f, -0.30f, 0.70f, -0.42f, 0.98f, -0.70f, 1.05f, -1.00f, 0.82f, -0.82f, 0.55f, -0.64f, 0.30f, -0.52f)
    private val eye = floatArrayOf(0.12f, -0.02f, 0.34f, -0.08f, 0.28f, 0.06f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun poly(c: Canvas, pts: FloatArray, cx: Float, cy: Float, sc: Float, sx: Float, color: Int) {
        path.reset()
        var i = 0
        while (i < pts.size) {
            val px = cx + pts[i] * sx * sc
            val py = cy + pts[i + 1] * sc
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            i += 2
        }
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(path, paint)
    }

    fun draw(c: Canvas, cx: Float, cy: Float, sc: Float, color: Int, eyeColor: Int) {
        for (sx in floatArrayOf(1f, -1f)) {
            poly(c, horn, cx, cy, sc, sx, color)
            poly(c, ear, cx, cy, sc, sx, color)
        }
        poly(c, head, cx, cy, sc, 1f, color)
        for (sx in floatArrayOf(1f, -1f)) poly(c, eye, cx, cy, sc, sx, eyeColor)
    }
}
