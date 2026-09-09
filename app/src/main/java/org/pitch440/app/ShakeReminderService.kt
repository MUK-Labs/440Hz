package org.pitch440.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.widget.Toast
import kotlin.math.sqrt

/** User-enabled foreground session; sensors run ONLY after a delivered reminder. */
class ShakeReminderService : Service(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val CHANNEL = "shake_after_reminders"
        private const val NOTIFICATION = 442
        private const val STOP = "org.pitch440.app.STOP_SHAKE"
        private var active: ShakeReminderService? = null

        fun supported(c: Context) =
            c.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

        private fun enabled(c: Context): Boolean {
            val p = Preferences(c)
            return p.enabled && p.shake && (p.expires == 0L || System.currentTimeMillis() < p.expires)
        }

        /** Call only from a visible activity, never from an inexact alarm receiver. */
        fun syncFromActivity(c: Context) {
            if (enabled(c) && supported(c) && Reminders.allowed(c)) {
                if (active?.ready == true) return
                try { c.startForegroundService(Intent(c, ShakeReminderService::class.java)) }
                catch (_: RuntimeException) {
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
            if (intent?.action == STOP) prefs.edit().putBoolean("shake", false).apply()
            if (!enabled(this) || !supported(this) || !Reminders.allowed(this)) {
                shutdown()
                return START_NOT_STICKY
            }
            handler.removeCallbacks(reconcile)
            reconcile.run()
        } catch (_: RuntimeException) {
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
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        try {
            gate.arm(SystemClock.elapsedRealtime(), Preferences(this).shakeThreshold)
            // Bounded CPU wake lock for screen-off phones with non-wakeup accelerometers.
            wake.acquire(ReminderShakeGate.WINDOW_MS + 5000)
            if (!sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler)) {
                endWindow()
                return
            }
            handler.postDelayed(timeout, ReminderShakeGate.WINDOW_MS)
            foreground(R.string.shake_listening)
        } catch (_: RuntimeException) { endWindow() }
    }

    private fun endWindow() {
        gate.disarm()
        sensors.unregisterListener(this)
        handler.removeCallbacks(timeout)
        if (wake.isHeld) wake.release()
        if (ready && !destroyed) foreground(R.string.shake_waiting)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        if (!gate.isArmed(now) || !enabled(this) || !canArm(this)) { endWindow(); return }
        val timestamp = event.timestamp / 1_000_000
        // Discard buffered readings, including those taken before this window.
        if (timestamp > now || now - timestamp > 250) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        if (gate.sample(timestamp, sqrt(x*x + y*y + z*z) / SensorManager.GRAVITY_EARTH)) {
            endWindow() // Consume before playing, even if focus is denied.
            if (!Reminders.allowed(this)) return
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
