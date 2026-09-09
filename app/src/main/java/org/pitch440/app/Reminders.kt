package org.pitch440.app

import android.app.*
import android.content.*
import java.time.ZoneId

class Preferences(context: Context) {
    val data = context.getSharedPreferences("pitch440", Context.MODE_PRIVATE)
    val enabled get() = data.getBoolean("enabled", false)
    val shake get() = data.getBoolean("shake", false)
    val shakeThreshold get() = doubleArrayOf(1.8, 2.4, 3.0)[data.getInt("shakeSensitivity", 1).coerceIn(0, 2)]
    val start get() = data.getInt("start", 540)
    val end get() = data.getInt("end", 1200)
    val count get() = data.getInt("count", 3)
    val perDay get() = data.getBoolean("perDay", false)
    val expires get() = data.getLong("expires", 0)
    val days get() = data.getStringSet("days", (1..7).map { it.toString() }.toSet())!!.map { it.toInt() }.toSet()
}

object Reminders {
    const val CHANNEL = "quiet_reminders_v1"
    private const val NOTIFICATION = 440
    fun channel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "Pitch reminders", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "A gentle vibration to recall A440. No sound by default."
        ch.setSound(null, null)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 180, 140, 180)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    fun allowed(c: Context): Boolean {
        channel(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun alarm(c: Context) = PendingIntent.getBroadcast(c, 440,
        Intent(c, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(c: Context) {
        ShakeReminderService.cancelWindow()
        c.getSystemService(AlarmManager::class.java).cancel(alarm(c))
        c.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        Preferences(c).data.edit().remove("next").apply()
    }
    fun schedule(c: Context, reset: Boolean = false) {
        val p = Preferences(c)
        val now = System.currentTimeMillis()
        if (!p.enabled || (p.expires > 0 && now >= p.expires)) {
            if (p.enabled) p.data.edit().putBoolean("enabled", false).apply()
            cancel(c); return
        }
        if (!allowed(c)) { cancel(c); return }
        if (!reset && p.data.getLong("next", 0) > now) return
        val next = Schedule.next(now, ZoneId.systemDefault(), p.start, p.end, p.days, p.count,
            p.perDay, Math.random(), p.expires)
        c.getSystemService(AlarmManager::class.java).cancel(alarm(c))
        p.data.edit().putLong("next", next).apply()
        if (next > 0) c.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(c))
    }
    fun show(c: Context, armShake: Boolean = false) {
        if (!allowed(c)) return
        val open = PendingIntent.getActivity(c, 440, Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_note).setContentTitle("Can you recall A?")
            .setContentText(if (armShake && ShakeReminderService.canArm(c))
                "Recall A. Shake twice within 60 seconds to hear it, or tap to open."
                else "Imagine or sing it. Tap to open 440 when you’re ready.")
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(3600000L).build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification)
            if (armShake) ShakeReminderService.afterReminder(c)
        }
        catch (_: SecurityException) { /* Permission may be revoked between the check and delivery. */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Preferences(context)
        val now = System.currentTimeMillis()
        val due = p.data.getLong("next", -1)
        if (due <= 0 || now < due) return // Reject canceled or stale early deliveries.
        p.data.edit().remove("next").apply()
        if (p.enabled && (p.expires == 0L || now < p.expires) &&
            Schedule.active(now, ZoneId.systemDefault(), p.start, p.end, p.days)) Reminders.show(context, armShake = true)
        Reminders.schedule(context, true)
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Reminders.schedule(context, true) }
}
