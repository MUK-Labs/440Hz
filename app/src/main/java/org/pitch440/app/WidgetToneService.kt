package org.pitch440.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast

/** Started only by an explicit widget tap; never restarted automatically. */
class WidgetToneService : Service() {
    companion object {
        const val PLAY_REMINDER = "org.pitch440.app.PLAY_REMINDER_TONE"
        const val PLAY = "org.pitch440.app.PLAY_WIDGET_TONE"
        private const val CHANNEL = "widget_playback"
        private const val NOTIFICATION = 441
    }

    private var playbackActive = false
    private var generation = 0
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.widget_playback_channel),
                NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(this, 441, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.widget_playback_title, PitchNotes.displayLabel(PitchPractice.current(this))))
            .setContentText(getString(R.string.widget_playback_text))
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            // Required before requesting audio focus on Android 15+.
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION, notification)
            }
            // An old notification/widget tap must never answer a newer prompt.
            // Quick Tap intentionally has no captured id and answers the current reminder.
            val expected = intent?.getStringExtra(PitchPractice.PROMPT_EXTRA)
            val matches = expected == null || expected == PitchPractice.promptId(this)
            val pitch = when (intent?.action) {
                PLAY -> if (expected != null && matches) PitchPractice.current(this) else null
                PLAY_REMINDER -> if (matches) ReminderResponse.consumePitch(this) else null
                else -> null
            }
            if (pitch != null) {
                val current = ++generation
                playbackActive = true
                TonePlayback.play(this, this, pitch) {
                    if (current == generation) {
                        playbackActive = false
                        stopSelf(latestStartId)
                    }
                }
            } else {
                if (intent?.action == PLAY) Toast.makeText(this,
                    "The practice prompt changed. Tap the widget again.", Toast.LENGTH_SHORT).show()
                if (intent?.action == PLAY_REMINDER) Toast.makeText(this,
                    R.string.response_closed, Toast.LENGTH_SHORT).show()
                if (!playbackActive) stopSelf(startId)
            }
        } catch (_: RuntimeException) {
            stopSelf(startId)
            Toast.makeText(this, R.string.widget_playback_failed, Toast.LENGTH_SHORT).show()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        generation++
        playbackActive = false
        TonePlayback.stop(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
