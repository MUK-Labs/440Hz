package org.pitch440.app

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Shared by shake, Quick Tap and the notification action; never armed by a launch. */
object ReminderResponse {
    private fun prefs(c: Context) = c.getSharedPreferences("reminder_response", Context.MODE_PRIVATE)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun eligible(c: Context): Boolean {
        val p = Preferences(c)
        return p.enabled && (p.expires == 0L || System.currentTimeMillis() < p.expires) &&
            Reminders.allowed(c) && c.getSystemService(NotificationManager::class.java)
                .currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    }
    @Synchronized fun arm(c: Context) {
        clear(c)
        if (eligible(c)) prefs(c).edit().putLong("armedAt", SystemClock.elapsedRealtime())
            .putInt("boot", boot(c)).commit()
    }
    @Synchronized fun clear(c: Context): Boolean = prefs(c).edit().clear().commit()

    @Synchronized fun available(c: Context, requireQuickTap: Boolean = true): Boolean {
        if (requireQuickTap && !Preferences(c).quickTap) return false
        val p = prefs(c)
        return ReminderResponseWindow(p.getLong("armedAt", -1), p.getInt("boot", -1))
            .isOpen(SystemClock.elapsedRealtime(), boot(c), eligible(c))
    }
    @Synchronized fun consume(c: Context, requireQuickTap: Boolean = true): Boolean {
        if (requireQuickTap && !Preferences(c).quickTap) return false
        val p = prefs(c)
        val accepted = ReminderResponseWindow(p.getLong("armedAt", -1), p.getInt("boot", -1))
            .consume(SystemClock.elapsedRealtime(), boot(c), eligible(c))
        val cleared = clear(c) // Fail closed if consumption cannot be persisted.
        return accepted && cleared
    }
}
