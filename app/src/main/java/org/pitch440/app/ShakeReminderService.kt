package org.pitch440.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.widget.Toast
import java.util.Locale
import kotlin.math.sqrt

/** User-enabled foreground session; sensors run ONLY after a delivered reminder. */
class ShakeReminderService : Service(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val CHANNEL = "shake_after_reminders"
        private const val NOTIFICATION = 442
        private const val STOP = "org.pitch440.app.STOP_SHAKE"
        private var active: ShakeReminderService? = null
        private var lastStartResult = "Not started in this app process"
        private var lastWindowStarted = -1L
        private var lastRegistration = "No reminder window attempted"
        private var lastSensorName = "None"
        private var sampleCount = 0L
        private var freshSampleCount = 0L
        private var peakG = 0.0
        private var lastSampleTime = -1L

        fun supported(c: Context): Boolean {
            val sensors = c.getSystemService(SensorManager::class.java)
            return sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true) != null ||
                sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false) != null
        }

        /** Read-only snapshot; does not register a sensor, create a channel, or arm a window. */
        fun diagnostics(c: Context): String {
            val p = Preferences(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            val channel = nm.getNotificationChannel(Reminders.CHANNEL)
            val now = SystemClock.elapsedRealtime()
            val service = active
            val listening = service?.ready == true && service.listening && service.gate.isArmed(now)
            val notificationState = when {
                !nm.areNotificationsEnabled() -> "Blocked for this app"
                channel == null -> "Reminder channel not created yet"
                channel.importance == NotificationManager.IMPORTANCE_NONE -> "Reminder channel blocked"
                else -> "Allowed"
            }
            val dnd = when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "Off"
                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "Unknown (gesture is not armed)"
                else -> "On (gesture is not armed)"
            }
            val sampleAge = if (lastSampleTime < 0) "No samples received"
                else if (lastSampleTime > now) "Sensor timestamp is ahead of the clock"
                else "${now - lastSampleTime} ms"
            return listOf(
                "Phone: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                "Accelerometer: ${if (supported(c)) "Available" else "Unavailable"}",
                "Reminders: ${if (p.enabled) "On" else "Off"} · Shake: ${if (p.shake) "On" else "Off"}",
                "Session eligible: ${if (enabled(c)) "Yes" else "No (disabled or expired)"}",
                "Notifications: $notificationState · Do Not Disturb: $dnd",
                "Service: ${if (service?.ready == true) "Running" else "Not running"}",
                "Last start: $lastStartResult",
                "Motion sensing: ${if (listening) "Listening in reminder window" else "Off"}",
                "Last window: ${if (lastWindowStarted < 0) "None in this process" else "${(now - lastWindowStarted) / 1000} seconds ago"}",
                "Registration: $lastRegistration",
                "Sensor: $lastSensorName",
                "Samples in last window: $sampleCount received; $freshSampleCount within freshness limit",
                "Peak acceleration: ${String.format(Locale.ROOT, "%.2f", peakG)} g · Threshold: ${p.shakeThreshold} g",
                "Age of last sensor reading: $sampleAge",
                "Use Test reminder, wait for vibration to finish, shake twice, then reopen this report. Measurements remain until the next window or app-process restart."
            ).joinToString("\n")
        }

        private fun enabled(c: Context): Boolean {
            val p = Preferences(c)
            return p.enabled && p.shake && (p.expires == 0L || System.currentTimeMillis() < p.expires)
        }

        /** Call only from a visible activity, never from an inexact alarm receiver. */
        fun syncFromActivity(c: Context) {
            if (enabled(c) && supported(c) && Reminders.allowed(c)) {
                if (active?.ready == true) return
                lastStartResult = "Starting"
                try { c.startForegroundService(Intent(c, ShakeReminderService::class.java)) }
                catch (error: RuntimeException) {
                    lastStartResult = "Failed: ${error.javaClass.simpleName}"
                    Preferences(c).data.edit().putBoolean("shake", false).apply()
                    Toast.makeText(c, R.string.shake_start_failed, Toast.LENGTH_LONG).show()
                }
            } else {
                active?.shutdown()
                c.stopService(Intent(c, ShakeReminderService::class.java))
            }
        }

        fun canArm(c: Context): Boolean = active?.ready == true && enabled(c) &&
            c.getSystemService(NotificationManager::class.java).currentInterruptionFilter ==
                NotificationManager.INTERRUPTION_FILTER_ALL

        fun afterReminder(c: Context) {
            if (canArm(c)) active?.arm()
        }

        fun cancelWindow() { active?.endWindow() }

        fun manualPlayback(owner: Any) {
            if (owner !== active) active?.endWindow()
        }
    }

    private lateinit var sensors: SensorManager
    private lateinit var prefs: SharedPreferences
    private lateinit var wake: PowerManager.WakeLock
    private val handler = Handler(Looper.getMainLooper())
    private val gate = ReminderShakeGate()
    private var ready = false
    private var destroyed = false
    private var listening = false
    private val timeout = Runnable { endWindow() }
    private val reconcile = object : Runnable {
        override fun run() {
            if (!enabled(this@ShakeReminderService) || !Reminders.allowed(this@ShakeReminderService)) {
                shutdown()
            } else {
                val expiry = Preferences(this@ShakeReminderService).expires
                val delay = if (expiry == 0L) 60_000L else
                    (expiry - System.currentTimeMillis()).coerceIn(1L, 60_000L)
                handler.postDelayed(this, delay)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        active = this
        sensors = getSystemService(SensorManager::class.java)
        prefs = Preferences(this).data
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "440:reminder-shake")
            .apply { setReferenceCounted(false) }
        prefs.registerOnSharedPreferenceChangeListener(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.shake_channel),
                NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            foreground(R.string.shake_waiting)
            ready = true
            lastStartResult = "Started successfully"
            if (intent?.action == STOP) prefs.edit().putBoolean("shake", false).apply()
            if (!enabled(this) || !supported(this) || !Reminders.allowed(this)) {
                shutdown()
                return START_NOT_STICKY
            }
            handler.removeCallbacks(reconcile)
            reconcile.run()
        } catch (error: RuntimeException) {
            lastStartResult = "Failed: ${error.javaClass.simpleName}"
            prefs.edit().putBoolean("shake", false).apply()
            shutdown()
            return START_NOT_STICKY
        }
        // A process restart waits for a NEW reminder; no window or sound is restored.
        return START_STICKY
    }

    private fun foreground(text: Int, playing: Boolean = false) {
        val open = PendingIntent.getActivity(this, 442, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 443,
            Intent(this, ShakeReminderService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.shake_title))
            .setContentText(getString(text))
            .setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.shake_turn_off), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (playing) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            startForeground(NOTIFICATION, notification, type)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION, notification,
                if (playing) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } else startForeground(NOTIFICATION, notification)
    }

    private fun arm() {
        endWindow()
        TonePlayback.stop(this)
        if (!ready || !enabled(this) || !Reminders.allowed(this)) return
        lastWindowStarted = SystemClock.elapsedRealtime()
        sampleCount = 0
        freshSampleCount = 0
        peakG = 0.0
        lastSampleTime = -1
        lastSensorName = "None"
        val candidates = listOfNotNull(
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true),
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)
        ).distinct()
        if (candidates.isEmpty()) {
            lastRegistration = "Failed: no accelerometer"
            return
        }
        try {
            gate.arm(SystemClock.elapsedRealtime(), Preferences(this).shakeThreshold)
            // Bounded CPU wake lock for screen-off phones with non-wakeup accelerometers.
            wake.acquire(ReminderShakeGate.WINDOW_MS + 5000)
            val attempts = mutableListOf<String>()
            for (sensor in candidates) {
                val kind = if (sensor.isWakeUpSensor) "Wake-up" else "Normal"
                val registered = try {
                    sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
                } catch (error: RuntimeException) {
                    attempts.add("$kind failed: ${error.javaClass.simpleName}")
                    false
                }
                if (registered) {
                    listening = true
                    lastSensorName = sensor.name
                    attempts.add("$kind registered")
                    break
                }
                // A present wake-up sensor is not necessarily usable. Try the normal one.
                sensors.unregisterListener(this)
                if (attempts.lastOrNull()?.startsWith("$kind failed:") != true)
                    attempts.add("$kind registration returned false")
            }
            lastRegistration = attempts.joinToString("; ")
            if (!listening) {
                endWindow()
                return
            }
            handler.postDelayed(timeout, ReminderShakeGate.WINDOW_MS)
            foreground(R.string.shake_listening)
        } catch (error: RuntimeException) {
            lastRegistration = "Window failed: ${error.javaClass.simpleName}"
            endWindow()
        }
    }

    private fun endWindow() {
        gate.disarm()
        sensors.unregisterListener(this)
        listening = false
        handler.removeCallbacks(timeout)
        if (wake.isHeld) wake.release()
        if (ready && !destroyed) foreground(R.string.shake_waiting)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        if (!gate.isArmed(now) || !enabled(this) || !canArm(this)) { endWindow(); return }
        val timestamp = event.timestamp / 1_000_000
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magnitude = sqrt(x*x + y*y + z*z) / SensorManager.GRAVITY_EARTH
        sampleCount++
        lastSampleTime = timestamp
        if (magnitude.isFinite()) peakG = maxOf(peakG, magnitude)
        // Count delayed readings in diagnostics, but never use them to trigger playback.
        if (timestamp > now || now - timestamp > 250) return
        freshSampleCount++
        if (gate.sample(timestamp, magnitude)) {
            endWindow() // Consume before playing, even if focus is denied.
            if (!ReminderResponse.consume(this, requireQuickTap = false)) return
            try {
                wake.acquire(10_000)
                foreground(R.string.shake_playing, true)
                TonePlayback.play(this, this) {
                    if (wake.isHeld) wake.release()
                    if (ready && !destroyed) foreground(R.string.shake_waiting)
                }
            } catch (_: RuntimeException) { endWindow() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSharedPreferenceChanged(shared: SharedPreferences?, key: String?) {
        if (key in listOf("enabled", "shake", "expires", "shakeSensitivity")) {
            endWindow()
            handler.removeCallbacks(reconcile)
            reconcile.run()
        }
    }

    private fun shutdown() {
        ready = false
        endWindow()
        TonePlayback.stop(this)
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        shutdown()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (active === this) active = null
        super.onDestroy()
    }
}
