package org.pitch440.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/** A distinct launcher target for Quick Tap. MainActivity always opens silently. */
class ReminderPlayActivity : Activity() {
    private var handled = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never replay from a restored task or a configuration change.
        handled = savedInstanceState != null ||
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        setContentView(TextView(this).apply {
            text = getString(R.string.response_checking)
            gravity = Gravity.CENTER
            textSize = 20f
        })
    }
    override fun onResume() {
        super.onResume()
        if (!handled) {
            handled = true
            if (!ReminderResponse.available(this)) {
                Toast.makeText(this, if (Preferences(this).quickTap) R.string.response_closed
                    else R.string.response_disabled, Toast.LENGTH_LONG).show()
            } else {
                try {
                    // Start while resumed. The service rechecks/consumes the window atomically.
                    startForegroundService(Intent(this, WidgetToneService::class.java)
                        .setAction(WidgetToneService.PLAY_REMINDER))
                } catch (_: RuntimeException) {
                    Toast.makeText(this, R.string.widget_playback_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }
}
