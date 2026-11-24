package com.gt7.telemetry

import android.graphics.Color
import org.json.JSONObject

/** One user-defined widget. Position and size are percentages of the screen (0-100). */
class WidgetDef(
    val type: String,
    val label: String,
    val source: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val min: Float, val max: Float,
    val color: Int,
    val decimals: Int,
    val text: String,
    val alertAbove: Float?,
    val alertBelow: Float?,
)

object WidgetPack {
    val TYPES = listOf("number", "bar", "arc", "light", "text")

    val NUMERIC_SOURCES = listOf(
        "rpm", "speed_kmh", "speed_mph", "gear", "throttle", "brake", "fuel_pct", "fuel", "laps_left",
        "boost", "oil_temp", "water_temp", "tyre_fl", "tyre_fr", "tyre_rl", "tyre_rr", "lap", "laps",
        "best_s", "last_s", "current_s", "delta", "tcs", "asm", "rev_limiter", "on_track",
    )
    val TIME_SOURCES = listOf("best_time", "last_time", "current_time")

    fun num(s: String, t: Telemetry, tr: LapTracker): Float? = when (s) {
        "rpm" -> t.rpm
        "speed_kmh" -> t.speedKmh
        "speed_mph" -> t.speedMs * 2.236936f
        "gear" -> t.gear.toFloat()
        "throttle" -> t.throttle * 100f
        "brake" -> t.brake * 100f
        "fuel_pct" -> if (t.fuelCap > 0f) t.fuel / t.fuelCap * 100f else 0f
        "fuel" -> t.fuel
        "laps_left" -> if (tr.lapsLeft >= 0f) tr.lapsLeft else null
        "boost" -> t.boost
        "oil_temp" -> t.oilTemp
        "water_temp" -> t.waterTemp
        "tyre_fl" -> t.tyres[0]
        "tyre_fr" -> t.tyres[1]
        "tyre_rl" -> t.tyres[2]
        "tyre_rr" -> t.tyres[3]
        "lap" -> t.lap.toFloat()
        "laps" -> t.laps.toFloat()
        "best_s" -> if (t.bestMs > 0) t.bestMs / 1000f else null
        "last_s" -> if (t.lastMs > 0) t.lastMs / 1000f else null
        "current_s" -> tr.curLapSec.toFloat()
        "delta" -> tr.deltaSec?.toFloat()
        "tcs" -> if (t.tcsActive) 1f else 0f
        "asm" -> if (t.asmActive) 1f else 0f
        "rev_limiter" -> if (t.revLimiter) 1f else 0f
        "on_track" -> if (t.onTrack) 1f else 0f
        else -> null
    }

    private fun fmtMs(ms: Int): String =
        if (ms <= 0) "-:--.---" else String.format("%d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000)

    fun time(s: String, t: Telemetry, tr: LapTracker): String? = when (s) {
        "best_time" -> fmtMs(t.bestMs)
        "last_time" -> fmtMs(t.lastMs)
        "current_time" -> fmtMs((tr.curLapSec * 1000).toInt())
        else -> null
    }

    private fun parseColor(s: String, fallback: Int): Int = try { Color.parseColor(s) } catch (e: Exception) { fallback }

    /** Parses a widget file. Throws IllegalArgumentException with a readable message on bad input. */
    fun parse(json: String): List<WidgetDef> {
        val root = try { JSONObject(json) } catch (e: Exception) {
            throw IllegalArgumentException("Not valid JSON: " + (e.message ?: ""))
        }
        val arr = root.optJSONArray("widgets") ?: throw IllegalArgumentException("Missing \"widgets\" list")
        if (arr.length() > 60) throw IllegalArgumentException("Too many widgets (max 60)")
        val out = ArrayList<WidgetDef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: throw IllegalArgumentException("Widget ${i + 1} is not an object")
            val type = o.optString("type", "number")
            if (type !in TYPES) throw IllegalArgumentException("Widget ${i + 1}: unknown type \"$type\" (use ${TYPES.joinToString()})")
            val src = o.optString("source", "")
            if (type != "text" && src !in NUMERIC_SOURCES && src !in TIME_SOURCES)
                throw IllegalArgumentException("Widget ${i + 1}: unknown source \"$src\"")
            if (src in TIME_SOURCES && type != "number")
                throw IllegalArgumentException("Widget ${i + 1}: time sources only work with type \"number\"")
            val w = o.optDouble("w", 20.0).toFloat()
            val h = o.optDouble("h", 20.0).toFloat()
            if (w <= 0f || h <= 0f) throw IllegalArgumentException("Widget ${i + 1}: w and h must be above 0")
            val alertAbove = if (o.has("alert_above")) o.optDouble("alert_above").toFloat() else null
            val alertBelow = if (o.has("alert_below")) o.optDouble("alert_below").toFloat() else null
            out.add(
                WidgetDef(
                    type = type,
                    label = o.optString("label", ""),
                    source = src,
                    x = o.optDouble("x", 0.0).toFloat(), y = o.optDouble("y", 0.0).toFloat(), w = w, h = h,
                    min = o.optDouble("min", 0.0).toFloat(), max = o.optDouble("max", 100.0).toFloat(),
                    color = parseColor(o.optString("color", "#FFD60A"), 0xFFFFD60A.toInt()),
                    decimals = o.optInt("decimals", 0).coerceIn(0, 3),
                    text = o.optString("text", ""),
                    alertAbove = alertAbove,
                    alertBelow = alertBelow,
                ),
            )
        }
        return out
    }

    val SAMPLE = """{
  "name": "Sample widgets",
  "widgets": [
    {"type": "arc",    "label": "SPEED", "source": "speed_kmh", "min": 0, "max": 300, "x": 3,  "y": 6,  "w": 28, "h": 54, "color": "#FFD60A"},
    {"type": "number", "label": "OIL",   "source": "oil_temp",   "decimals": 0, "x": 34, "y": 6,  "w": 19, "h": 24, "color": "#FF7A00", "alert_above": 130},
    {"type": "number", "label": "WATER", "source": "water_temp", "decimals": 0, "x": 55, "y": 6,  "w": 19, "h": 24, "color": "#19D3FF", "alert_above": 110},
    {"type": "bar",    "label": "BOOST", "source": "boost", "min": 0, "max": 2, "decimals": 2, "x": 34, "y": 36, "w": 40, "h": 24, "color": "#2EE66B"},
    {"type": "light",  "label": "REV LIMIT", "source": "rev_limiter", "x": 78, "y": 6,  "w": 19, "h": 15, "color": "#FF2D3C"},
    {"type": "light",  "label": "TCS", "source": "tcs", "x": 78, "y": 25, "w": 19, "h": 15, "color": "#FF7A00"},
    {"type": "light",  "label": "ASM", "source": "asm", "x": 78, "y": 44, "w": 19, "h": 15, "color": "#19D3FF"},
    {"type": "number", "label": "BEST LAP",    "source": "best_time",    "x": 3,  "y": 68, "w": 30, "h": 26, "color": "#FFD60A"},
    {"type": "number", "label": "CURRENT LAP", "source": "current_time", "x": 36, "y": 68, "w": 30, "h": 26, "color": "#F4F4F4"},
    {"type": "number", "label": "DELTA",       "source": "delta", "decimals": 2, "x": 69, "y": 68, "w": 28, "h": 26, "color": "#2EE66B", "alert_above": 0}
  ]
}"""
}
