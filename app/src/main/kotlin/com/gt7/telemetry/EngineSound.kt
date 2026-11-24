package com.gt7.telemetry

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Synthesized V8 engine note (no recordings). Cross-plane V8 firing: strong 4th and 8th engine orders with a
 * lumpy lower-order burble. Pitch follows [rpm], brightness and loudness follow [throttle].
 */
class EngineSound {
    @Volatile var rpm = 0f
    @Volatile var throttle = 0f
    @Volatile var limiter = false
    @Volatile private var running = false
    private var thread: Thread? = null

    private val sr = 22050
    private val table = FloatArray(4096) { sin(2.0 * PI * it / 4096).toFloat() }
    private val ph = FloatArray(25) { ((it * 0.6180339f) % 1f) }
    private val base = FloatArray(25) { 0.06f }.also {
        it[1] = 0.12f; it[2] = 0.25f; it[3] = 0.10f; it[4] = 0.60f; it[5] = 0.10f; it[6] = 0.22f
        it[7] = 0.10f; it[8] = 1.00f; it[9] = 0.10f; it[10] = 0.20f; it[11] = 0.12f; it[12] = 0.50f
        it[14] = 0.25f; it[16] = 0.45f; it[20] = 0.20f; it[24] = 0.14f
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread { runLoop() }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
    }

    private fun runLoop() {
        val chunk = 512
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuf, chunk * 2 * 4))
                .build()
        } catch (e: Exception) {
            running = false
            return
        }
        track.play()
        val buf = ShortArray(chunk)
        val amp = FloatArray(25)
        val rnd = java.util.Random(7)
        var theta = 0f
        var rpmS = 800f
        var vol = 0f
        var nl = 0f
        var n = 0L
        while (running) {
            val target = rpm
            val thr = throttle.coerceIn(0f, 1f)
            val bright = 0.35f + 0.65f * thr
            val fc = 500f + 2500f * bright
            var sum = 0.001f
            for (k in 1..24) {
                val fk = k * rpmS / 120f
                val r = fk / fc
                amp[k] = if (fk > 9000f) 0f else base[k] / (1f + r * r)
                sum += amp[k]
            }
            val norm = 0.55f / sum
            val gain = (0.55f + 0.45f * (rpmS / 8000f)) * (0.45f + 0.55f * thr)
            val noiseAmp = 0.04f + 0.10f * thr
            val volTarget = if (target > 100f) 1f else 0f
            val lim = limiter
            for (i in 0 until chunk) {
                if (target > 100f) rpmS += (target - rpmS) * 0.0008f
                theta += rpmS / 120f / sr
                if (theta >= 1f) theta -= 1f
                var s = 0f
                for (k in 1..24) {
                    val x = k * theta + ph[k]
                    val idx = ((x - floor(x)) * 4096f).toInt() and 4095
                    s += amp[k] * table[idx]
                }
                val nz = rnd.nextFloat() * 2f - 1f
                nl += (nz - nl) * 0.3f
                val pulse = 0.5f + 0.5f * table[((8f * theta - floor(8f * theta)) * 4096f).toInt() and 4095]
                var y = s * norm * gain + nl * noiseAmp * pulse
                y *= 1.6f
                y /= (1f + kotlin.math.abs(y))
                vol += (volTarget - vol) * 0.0005f
                var gate = 1f
                if (lim) gate = if (((n * 28L / sr) % 2L) == 0L) 1f else 0.15f
                n++
                buf[i] = (y * vol * gate * 0.9f * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            }
            track.write(buf, 0, chunk)
        }
        try { track.stop() } catch (e: Exception) { }
        track.release()
    }
}
