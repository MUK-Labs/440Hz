package org.pitch440.app

import android.Manifest
import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val ink = Color.rgb(30, 52, 43)
    private val green = Color.rgb(33, 105, 87)
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private var updating = false
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(244, 243, 235)) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(28)) }
        scroll.addView(content)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        label("A LITTLE PITCH PRACTICE", 12).apply { letterSpacing = 0.15f }
        label("Find your A.", 34).setTypeface(null, Typeface.BOLD)
        label("Recall it. Sing it. Then compare.", 17)
        val play = Button(this).apply {
            text = "440"; textSize = 76f; isAllCaps = false; setTextColor(Color.rgb(244, 243, 235))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(green) }
            contentDescription = "Play A, 440 hertz"
            setOnClickListener { playTone() }
        }
        content.addView(play, LinearLayout.LayoutParams(dp(240), dp(240)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(24); bottomMargin = dp(12)
        })
        label("Tap to hear A · 440 Hz", 15).gravity = Gravity.CENTER
        label("Uses your media volume and current speaker or headphones.", 12).gravity = Gravity.CENTER
        toggle = Switch(this).apply {
            text = "Random reminders"; textSize = 19f; setTextColor(ink); setPadding(0, dp(24), 0, dp(12))
            setOnCheckedChangeListener { _, checked ->
                if (!updating) {
                    if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 440)
                        updating = true; isChecked = false; updating = false
                    } else setEnabledReminders(checked)
                }
            }
        }
        content.addView(toggle)
        status = label("", 14)
        button(getString(R.string.widget_add)) {
            val widgets = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, PitchWidget::class.java)
            if (!widgets.isRequestPinAppWidgetSupported ||
                !widgets.requestPinAppWidget(provider, null, null)) {
                Toast.makeText(this, R.string.widget_add_help, Toast.LENGTH_LONG).show()
            }
        }
        button("Reminder settings") { editSettings() }
        button("Test vibration") {
            if (Reminders.allowed(this)) Reminders.show(this)
            else Toast.makeText(this, "Allow notifications first, using Notification settings.", Toast.LENGTH_LONG).show()
        }
        button("Notification settings") {
            Reminders.channel(this)
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, Reminders.CHANNEL))
        }
        label("A quiet nudge. No automatic sound.\nVibration follows your phone’s notification and Do Not Disturb settings.", 13)
    }
    private fun label(value: String, size: Int): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(ink); setPadding(0, dp(5), 0, dp(5)); content.addView(this)
    }
    private fun button(value: String, action: () -> Unit) {
        content.addView(Button(this).apply { text = value; isAllCaps = false; setOnClickListener { action() } })
    }
    override fun onResume() { super.onResume(); Reminders.schedule(this); refresh() }
    private fun refresh() {
        val p = Preferences(this)
        updating = true; toggle.isChecked = p.enabled; updating = false
        val ch = getSystemService(NotificationManager::class.java).getNotificationChannel(Reminders.CHANNEL)
        status.text = when {
            !p.enabled -> "Reminders are off. The 440 button is always ready."
            !Reminders.allowed(this) -> "Reminders are enabled, but notifications are blocked. Open Notification settings."
            else -> "About ${p.count} per ${if (p.perDay) "active day" else "active hour"} · ${time(p.start)}–${time(p.end)}\n" +
                (if (ch != null && !ch.shouldVibrate()) "Vibration is disabled in Notification settings.\n" else "") +
                (if (p.expires > 0) "Ends ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(p.expires))}.\n" else "") +
                "Battery saving may delay or reduce reminders."
        }
    }
    private fun setEnabledReminders(value: Boolean) {
        val p = Preferences(this)
        val editor = p.data.edit().putBoolean("enabled", value)
        if (value) {
            val duration = p.data.getLong("duration", 0)
            editor.putLong("expires", if (duration == 0L) 0 else System.currentTimeMillis() + duration)
        }
        editor.apply()
        Reminders.schedule(this, true); refresh()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 440) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) setEnabledReminders(true)
            else Toast.makeText(this, "Reminders need notification permission. You can still play 440.", Toast.LENGTH_LONG).show()
        }
    }
    private fun stopTone() = TonePlayback.stop(this)
    private fun playTone() = TonePlayback.play(this, this)
    override fun onStop() { stopTone(); super.onStop() }
    private fun time(minutes: Int) = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    private fun editSettings() {
        val p = Preferences(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun caption(s: String) { panel.addView(TextView(this).apply { text = s; textSize = 15f; setPadding(0, dp(12), 0, dp(4)) }) }
        fun spinner(items: List<String>) = Spinner(this).also {
            it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items); panel.addView(it)
        }
        caption("Average reminders")
        val count = spinner((1..6).map { it.toString() }); count.setSelection(p.count - 1)
        val unit = spinner(listOf("Per active hour", "Per active day")); unit.setSelection(if (p.perDay) 1 else 0)
        var start = p.start; var end = p.end
        caption("Active hours · equal times mean all day")
        val from = Button(this).apply { text = "From ${time(start)}" }
        val to = Button(this).apply { text = "To ${time(end)}" }
        from.setOnClickListener { TimePickerDialog(this, { _, h, m -> start = h * 60 + m; from.text = "From ${time(start)}" }, start / 60, start % 60, true).show() }
        to.setOnClickListener { TimePickerDialog(this, { _, h, m -> end = h * 60 + m; to.text = "To ${time(end)}" }, end / 60, end % 60, true).show() }
        panel.addView(from); panel.addView(to)
        caption("Active days · overnight windows start on these days")
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").mapIndexed { i, s ->
            CheckBox(this).apply { text = s; isChecked = (i + 1) in p.days; panel.addView(this) }
        }
        caption("Run for · starts when enabled or settings are saved")
        val durations = listOf(0L, 3600000L, 28800000L, 86400000L, 604800000L)
        val duration = spinner(listOf("Until switched off", "1 hour", "8 hours", "1 day", "7 days"))
        duration.setSelection(durations.indexOf(p.data.getLong("duration", 0)).coerceAtLeast(0))
        caption("Random spacing, at least 5 active minutes apart. No catch-up reminders.")
        val scroller = ScrollView(this).apply { addView(panel) }
        val dialog = AlertDialog.Builder(this).setTitle("Your practice rhythm").setView(scroller)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val selected = days.mapIndexedNotNull { i, day -> if (day.isChecked) (i + 1).toString() else null }.toSet()
            if (selected.isEmpty()) { Toast.makeText(this, "Choose at least one day.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val length = durations[duration.selectedItemPosition]
            p.data.edit().putInt("count", count.selectedItemPosition + 1).putBoolean("perDay", unit.selectedItemPosition == 1)
                .putInt("start", start).putInt("end", end).putStringSet("days", selected).putLong("duration", length)
                .putLong("expires", if (p.enabled && length > 0) System.currentTimeMillis() + length else 0).apply()
            Reminders.cancel(this); Reminders.schedule(this, true); refresh(); dialog.dismiss()
        } }
        dialog.show()
    }
}
