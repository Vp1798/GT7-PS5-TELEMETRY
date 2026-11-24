package com.gt7.telemetry

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.exp
import kotlin.math.pow
import android.widget.Toast
import java.io.File
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class MainActivity : Activity() {
    private val tracker = LapTracker()
    private lateinit var dash: DashboardView
    private lateinit var panel: LinearLayout
    private lateinit var ipField: EditText
    private lateinit var guide: ScrollView
    private lateinit var about: ScrollView
    private lateinit var custom: CustomView
    private lateinit var widgetInfo: TextView
    private var onCustomPage = false
    private val engine = EngineSound()
    private var soundOn = true
    private lateinit var soundBtn: Button
    private var client: Gt7Client? = null
    private val ui = Handler(Looper.getMainLooper())
    private var running = false
    private var demoOn = false
    private var demoStartNs = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (demoOn) feedDemo()
            dash.invalidate()
            custom.invalidate()
            ui.postDelayed(this, 33)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) 0xFF0B0D10.toInt() else 0xFFF5F5F2.toInt()
        val fg = if (night) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
        val sub = if (night) 0xFF8A9099.toInt() else 0xFF666666.toInt()

        val root = FrameLayout(this)
        root.setBackgroundColor(bg)
        dash = DashboardView(this, tracker)
        dash.visibility = View.GONE
        root.addView(dash, FrameLayout.LayoutParams(-1, -1))
        custom = CustomView(this, tracker)
        custom.visibility = View.GONE
        root.addView(custom, FrameLayout.LayoutParams(-1, -1))
        soundOn = getSharedPreferences("gt7", MODE_PRIVATE).getBoolean("sound", true)
        dash.soundOn = soundOn
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (e.x > dash.width * 0.75f && e.y < dash.height * 0.2f) toggleSound() else togglePage()
                return true
            }
            override fun onLongPress(e: MotionEvent) { stopAll() }
        })
        val touch = View.OnTouchListener { _, ev -> gd.onTouchEvent(ev); true }
        dash.setOnTouchListener(touch)
        custom.setOnTouchListener(touch)

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.gravity = Gravity.CENTER
        panel.setPadding(64, 24, 64, 24)

        val title = TextView(this)
        title.text = "GT7 Telemetry"
        title.textSize = 26f
        title.setTextColor(fg)
        title.gravity = Gravity.CENTER
        panel.addView(title)

        val hint = TextView(this)
        hint.text = "Enter your PS5 IP address (Settings > Network > Connection Status). Phone and PS5 must be on the same network."
        hint.textSize = 14f
        hint.setTextColor(sub)
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, 8, 0, 16)
        panel.addView(hint)

        ipField = EditText(this)
        ipField.hint = "192.168.1.50"
        ipField.inputType = InputType.TYPE_CLASS_PHONE
        ipField.setTextColor(fg)
        ipField.setHintTextColor(sub)
        ipField.setText(getSharedPreferences("gt7", MODE_PRIVATE).getString("ip", "") ?: "")
        panel.addView(ipField, LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val connect = Button(this)
        connect.text = "Connect"
        connect.setOnClickListener { connect() }
        val demo = Button(this)
        demo.text = "Demo mode"
        demo.setOnClickListener { startDemo() }
        soundBtn = Button(this)
        soundBtn.text = if (soundOn) "Sound: ON" else "Sound: OFF"
        soundBtn.setOnClickListener { toggleSound() }
        row.addView(connect)
        row.addView(demo)
        row.addView(soundBtn)
        panel.addView(row)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.gravity = Gravity.CENTER
        val imp = Button(this)
        imp.text = "Import widgets"
        imp.setOnClickListener { pickWidgetFile() }
        val sample = Button(this)
        sample.text = "Load sample"
        sample.setOnClickListener { installWidgets(WidgetPack.SAMPLE) }
        val clear = Button(this)
        clear.text = "Clear widgets"
        clear.setOnClickListener { clearWidgets() }
        val help = Button(this)
        help.text = "How to set up"
        help.setOnClickListener { showGuide(true) }
        row2.addView(imp)
        row2.addView(sample)
        row2.addView(clear)
        panel.addView(row2)

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        row3.gravity = Gravity.CENTER
        val aboutBtn = Button(this)
        aboutBtn.text = "About"
        aboutBtn.setOnClickListener { showAbout(true) }
        row3.addView(help)
        row3.addView(aboutBtn)
        panel.addView(row3)

        widgetInfo = TextView(this)
        widgetInfo.textSize = 12f
        widgetInfo.setTextColor(sub)
        widgetInfo.gravity = Gravity.CENTER
        widgetInfo.setPadding(0, 8, 0, 0)
        panel.addView(widgetInfo)

        val foot = TextView(this)
        foot.text = "Long-press the dashboard to go back."
        foot.textSize = 12f
        foot.setTextColor(sub)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, 16, 0, 0)
        panel.addView(foot)

        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        guide = buildGuide(bg, fg, sub)
        guide.visibility = View.GONE
        root.addView(guide, FrameLayout.LayoutParams(-1, -1))
        about = buildAbout(bg, fg, sub)
        about.visibility = View.GONE
        root.addView(about, FrameLayout.LayoutParams(-1, -1))
        val splash = SplashView(this)
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        if (soundOn) engine.start()
        splash.onTick = { t ->
            if (soundOn) {
                if (t < 1.1f) {
                    engine.rpm = 2500f + 4700f * (t / 1.1f).pow(1.2f)
                    engine.throttle = 1f
                } else if (t < 2.3f) {
                    engine.rpm = 1500f + 3700f * exp(-(t - 1.1f) * 1.6f)
                    engine.throttle = 0.25f
                } else {
                    engine.rpm = 0f
                }
            }
        }
        splash.postDelayed({
            engine.rpm = 0f
            splash.onTick = null
            splash.animate().alpha(0f).setDuration(350).withEndAction {
                root.removeView(splash)
                if (!running) engine.stop()
            }.start()
        }, 2600)
        setContentView(root)
        loadSavedWidgets()
        if (!getSharedPreferences("gt7", MODE_PRIVATE).getBoolean("seen_guide", false)) {
            getSharedPreferences("gt7", MODE_PRIVATE).edit().putBoolean("seen_guide", true).apply()
            showGuide(true)
        }
    }

    private fun showGuide(show: Boolean) {
        guide.visibility = if (show) View.VISIBLE else View.GONE
        panel.visibility = if (show) View.GONE else View.VISIBLE
        if (show) guide.scrollTo(0, 0)
    }

    private fun showAbout(show: Boolean) {
        about.visibility = if (show) View.VISIBLE else View.GONE
        panel.visibility = if (show) View.GONE else View.VISIBLE
        if (show) about.scrollTo(0, 0)
    }

    private fun buildAbout(bg: Int, fg: Int, sub: Int): ScrollView {
        val sv = ScrollView(this)
        sv.setBackgroundColor(bg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(64, 24, 64, 48)

        col.addView(EmblemView(this), LinearLayout.LayoutParams(220, 220))

        fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) {
            val v = TextView(this)
            v.text = t
            v.textSize = size
            v.setTextColor(color)
            v.gravity = Gravity.CENTER
            if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD)
            v.setPadding(0, top, 0, 0)
            col.addView(v)
        }

        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
        text("GT7 Telemetry", 24f, fg, bold = true)
        text("Version $version", 13f, sub, top = 4)
        text("A live dashboard for Gran Turismo 7. It reads the telemetry your PS5 sends over your home network " +
            "and shows RPM, gear, speed, pedals, tyres, fuel, lap times and delta to your best lap.", 15f, sub, top = 20)

        text("Developer", 13f, sub, top = 28)
        text("@" + Config.GITHUB_USER, 20f, fg, bold = true, top = 4)

        val link = LinearLayout(this)
        link.orientation = LinearLayout.HORIZONTAL
        link.gravity = Gravity.CENTER_VERTICAL
        link.setPadding(0, 16, 0, 0)
        val markId = resources.getIdentifier("github_mark", "drawable", packageName)
        if (markId != 0) {
            val iv = ImageView(this)
            iv.setImageResource(markId)
            link.addView(iv, LinearLayout.LayoutParams(72, 72))
        } else {
            link.addView(CodeGlyphView(this, fg), LinearLayout.LayoutParams(72, 72))
        }
        val visit = Button(this)
        visit.text = "Visit GitHub profile"
        visit.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GITHUB_URL)))
            } catch (e: Exception) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }
        link.addView(visit)
        col.addView(link)
        text(Config.GITHUB_URL.removePrefix("https://"), 13f, sub, top = 4)

        text("Not affiliated with, endorsed by, or sponsored by Sony Interactive Entertainment or Polyphony Digital. " +
            "Gran Turismo is a trademark of its owners. This app only reads the telemetry feed your own PS5 sends " +
            "and cannot change anything in the game.", 12f, sub, top = 32)

        val back = Button(this)
        back.text = "Back"
        back.setOnClickListener { showAbout(false) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 28
        col.addView(back, lp)

        sv.addView(col)
        return sv
    }

    private fun buildGuide(bg: Int, fg: Int, sub: Int): ScrollView {
        val sv = ScrollView(this)
        sv.setBackgroundColor(bg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(64, 32, 64, 48)

        fun head(text: String) {
            val t = TextView(this)
            t.text = text
            t.textSize = 18f
            t.setTextColor(fg)
            t.setTypeface(null, android.graphics.Typeface.BOLD)
            t.setPadding(0, 28, 0, 6)
            col.addView(t)
        }
        fun body(text: String) {
            val t = TextView(this)
            t.text = text
            t.textSize = 15f
            t.setTextColor(sub)
            t.setLineSpacing(0f, 1.2f)
            col.addView(t)
        }

        val title = TextView(this)
        title.text = "How to set up GT7 Telemetry"
        title.textSize = 24f
        title.setTextColor(fg)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        col.addView(title)

        head("1. Put the phone and PS5 on the same network")
        body("Connect your phone to the same Wi-Fi or router as the PS5. The PS5 can be on a cable. " +
            "Don't use mobile data, a guest network, or a VPN on the phone, because those block the connection.")

        head("2. Find your PS5's IP address")
        body("On the PS5, open Settings > Network > Connection Status > View Connection Status, " +
            "then note the IP address, for example 192.168.1.50.\n\n" +
            "Tip: the address can change after a restart. If it does, update it in the app, " +
            "or reserve a fixed address for the PS5 in your router settings.")

        head("3. Connect")
        body("Type the IP address into the box on the main screen and tap Connect. " +
            "The app remembers it for next time.")

        head("4. Start driving in GT7")
        body("Telemetry is only sent while you're on track. Start a race, a time trial, or Free Run. " +
            "Menus, replays, and photo mode send nothing, so the app will say it's waiting.")

        head("5. Reading the dashboard")
        body("Tach ring: fills as RPM rises. Yellow is normal, orange is the shift zone, and everything flashes red at the limiter.\n" +
            "Gear: sits in the hexagon and turns orange when GT7 suggests a different gear.\n" +
            "TCS and ASM lights: glow only while traction control or stability management is actually stepping in. " +
            "ABS has no signal in GT7, so it can't be shown. You can't change these settings from the app, only in the game.\n" +
            "Tyres: blue is cold, green is good, orange is hot, red is too hot (about 60, 95, and 110 degrees C).\n" +
            "Brake and throttle: the bars fill as you press the pedals.\n" +
            "Fuel: percent left, plus laps remaining once you've finished a lap.\n" +
            "Delta under the gear: green means you're faster than your best lap, red means slower. " +
            "It appears after you've set a best lap.")

        head("Engine sound")
        body("The app plays a synthesized V8 engine note that follows your real RPM and throttle, and revs at the limiter. " +
            "It plays from the phone speaker. Tap Sound on the main screen, or tap the top-right corner of the dashboard, to switch it on or off. " +
            "It is generated by the app, not a recording of any real car.")

        head("Custom widgets (optional)")
        body("You can add your own gauges. Tap Import widgets and pick a .json file, or tap Load sample to see an example. " +
            "Widgets appear on a second page: tap the dashboard to switch pages. Clear widgets removes them.\n\n" +
            "File format: {\"widgets\": [ {...}, {...} ]}. Each widget has:\n" +
            "type: number, bar, arc, light, or text\n" +
            "source: rpm, speed_kmh, speed_mph, gear, throttle, brake, fuel_pct, fuel, laps_left, boost, oil_temp, water_temp, " +
            "tyre_fl, tyre_fr, tyre_rl, tyre_rr, lap, laps, best_s, last_s, current_s, delta, tcs, asm, rev_limiter, on_track, " +
            "or best_time, last_time, current_time (number type only)\n" +
            "label, color (like #FFD60A), decimals, min and max (bar and arc)\n" +
            "x, y, w, h: position and size as percent of the screen\n" +
            "alert_above or alert_below: turns the widget red past that value\n" +
            "text: the words for a text widget")

        head("6. If it says \"Waiting for PS5\"")
        body("• Check the IP address is correct and the PS5 is on, with GT7 running.\n" +
            "• Make sure you are on track, not in a menu.\n" +
            "• Confirm the phone is on the same Wi-Fi, and turn off any VPN.\n" +
            "• Some routers isolate devices (AP or client isolation). Turn that off, or use a different network.\n" +
            "• Tap back, then Connect again, or restart GT7.")

        head("Good to know")
        body("GT7 only shares data about your own car. It does not provide positions or gaps to other drivers.\n\n" +
            "No PS5 handy? Tap Demo mode to see the dashboard with sample data.\n\n" +
            "Long-press the dashboard, or press Back, to return to this screen.")

        val back = Button(this)
        back.text = "Got it"
        back.setOnClickListener { showGuide(false) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 32
        col.addView(back, lp)

        sv.addView(col)
        return sv
    }

    private fun connect() {
        val ip = ipField.text.toString().trim()
        if (ip.isEmpty()) {
            ipField.error = "Enter the PS5 IP address"
            return
        }
        getSharedPreferences("gt7", MODE_PRIVATE).edit().putString("ip", ip).apply()
        demoOn = false
        showDash()
        dash.statusText = "Connecting…"
        custom.statusText = "Connecting…"
        client = Gt7Client(
            ip,
            { t ->
                val n = System.nanoTime()
                tracker.update(t, n)
                publish(t, n)
            },
            { s -> dash.statusText = s; custom.statusText = s },
        ).also { it.start() }
    }

    private fun startDemo() {
        demoOn = true
        demoStartNs = System.nanoTime()
        showDash()
    }

    private fun showDash() {
        tracker.reset()
        dash.data = null
        custom.data = null
        panel.visibility = View.GONE
        onCustomPage = false
        dash.visibility = View.VISIBLE
        custom.visibility = View.GONE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        running = true
        if (soundOn) engine.start()
        ui.post(tick)
    }

    private fun stopAll() {
        running = false
        demoOn = false
        engine.rpm = 0f
        engine.stop()
        client?.stop()
        client = null
        dash.visibility = View.GONE
        custom.visibility = View.GONE
        onCustomPage = false
        panel.visibility = View.VISIBLE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (about.visibility == View.VISIBLE) showAbout(false)
        else if (guide.visibility == View.VISIBLE) showGuide(false)
        else if (running) stopAll() else super.onBackPressed()
    }

    override fun onDestroy() {
        engine.stop()
        client?.stop()
        super.onDestroy()
    }

    /** Synthetic 60-second laps so the layout, shift lights and delta can be tried without a PS5. */
    private fun feedDemo() {
        val now = System.nanoTime()
        val t = (now - demoStartNs) / 1e9
        val lapLen = 60.0
        val lap = 1 + (t / lapLen).toInt()
        val scale = 1.0 + 0.03 * sin(lap * 2.3)
        val ph = (t % lapLen) / lapLen
        val speedMs = ((45.0 + 28.0 * sin(2 * PI * ph * 3)) * scale).toFloat()
        val rpmFrac = 0.5 + 0.5 * sin(2 * PI * t / 5.0)
        val rpm = (3500 + 4300 * rpmFrac).toFloat()
        val gear = (2 + (speedMs / 14f).toInt()).coerceIn(1, 6)
        val thr = max(0.0, sin(2 * PI * t / 5.0 + 0.6)).toFloat()
        val brk = max(0.0, -sin(2 * PI * t / 5.0 + 0.6)).toFloat() * 0.8f
        var best = -1
        var last = -1
        for (l in 1 until lap) {
            last = 60000 + ((l * 7919) % 2000) - 1000 + ((scale - 1.0) * -20000).toInt()
            best = if (best < 0) last else minOf(best, last)
        }
        val fuel = max(5.0, 100.0 - t * 0.12).toFloat()
        val tyres = FloatArray(4) { i -> (82 + 18 * sin(t / 7.0 + i)).toFloat() }
        val p = Telemetry(
            rpm = rpm, speedMs = speedMs, gear = gear, throttle = thr, brake = brk,
            fuel = fuel, fuelCap = 100f, tyres = tyres, lap = lap, laps = 5,
            bestMs = best, lastMs = last, flags = 1, minAlertRpm = 6500, maxAlertRpm = 7800,
        )
        tracker.update(p, now)
        publish(p, now)
    }

    private fun publish(t: Telemetry, ns: Long) {
        dash.data = t
        dash.lastPacketNs = ns
        custom.data = t
        custom.lastPacketNs = ns
        if (soundOn) {
            engine.rpm = if (t.paused || !t.onTrack) 0f else t.rpm
            engine.throttle = t.throttle
            engine.limiter = t.revLimiter
        }
    }

    private fun toggleSound() {
        soundOn = !soundOn
        getSharedPreferences("gt7", MODE_PRIVATE).edit().putBoolean("sound", soundOn).apply()
        dash.soundOn = soundOn
        soundBtn.text = if (soundOn) "Sound: ON" else "Sound: OFF"
        if (soundOn) { if (running) engine.start() } else { engine.rpm = 0f }
    }

    override fun onPause() {
        engine.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (running && soundOn) engine.start()
    }

    // ---- Custom widgets

    private fun widgetFile() = File(filesDir, "widgets.json")

    private fun pickWidgetFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        @Suppress("DEPRECATION")
        startActivityForResult(i, 42)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("Couldn't read the file")
            if (bytes.size > 200_000) throw IllegalArgumentException("File is too large")
            installWidgets(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun installWidgets(json: String) {
        try {
            val list = WidgetPack.parse(json)
            widgetFile().writeText(json)
            applyWidgets(list)
            Toast.makeText(this, "Imported ${list.size} widgets. Tap the dashboard to switch pages.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSavedWidgets() {
        val f = widgetFile()
        if (!f.exists()) { applyWidgets(emptyList()); return }
        try { applyWidgets(WidgetPack.parse(f.readText())) } catch (e: Exception) { applyWidgets(emptyList()) }
    }

    private fun clearWidgets() {
        widgetFile().delete()
        applyWidgets(emptyList())
        Toast.makeText(this, "Custom widgets removed", Toast.LENGTH_SHORT).show()
    }

    private fun applyWidgets(list: List<WidgetDef>) {
        custom.widgets = list
        dash.hasCustomPage = list.isNotEmpty()
        widgetInfo.text = if (list.isEmpty()) "No custom widgets loaded." else "${list.size} custom widgets loaded. Tap the dashboard to switch pages."
        if (list.isEmpty()) onCustomPage = false
    }

    private fun togglePage() {
        if (custom.widgets.isEmpty()) return
        onCustomPage = !onCustomPage
        dash.visibility = if (onCustomPage) View.GONE else View.VISIBLE
        custom.visibility = if (onCustomPage) View.VISIBLE else View.GONE
    }
}
