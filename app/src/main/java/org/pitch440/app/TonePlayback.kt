package org.pitch440.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** One player shared by the activity and widgets. All calls run on the main thread. */
object TonePlayback {
    private var active: Session? = null

    fun play(context: Context, owner: Any, onStopped: () -> Unit = {}) {
        ShakeReminderService.manualPlayback(owner)
        active?.close()
        val session = Session(context.applicationContext, owner, onStopped)
        active = session
        session.start()
    }

    fun stop(owner: Any) {
        active?.takeIf { it.owner === owner }?.close()
    }

    private class Session(val context: Context, val owner: Any, val onStopped: () -> Unit) {
        private val handler = Handler(Looper.getMainLooper())
        private val audio = context.getSystemService(AudioManager::class.java)
        private val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0 && active === this) close()
            }, handler).build()
        private var player: MediaPlayer? = null
        private var hasFocus = false
        private var closed = false
        private val timeout = Runnable { close() }

        fun start() {
            try {
                hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (!hasFocus) {
                    Toast.makeText(context, "Audio is busy. Try again in a moment.", Toast.LENGTH_SHORT).show()
                    close()
                    return
                }
                val created = MediaPlayer.create(context, R.raw.a440, attributes, 0)
                    ?: throw IllegalStateException("Could not load tone")
                player = created
                created.setOnCompletionListener { close() }
                created.setOnErrorListener { _, _, _ -> close(); true }
                // Bound service lifetime even if a device misses a completion callback.
                handler.postDelayed(timeout, 8000)
                created.start()
            } catch (_: Exception) {
                close()
                Toast.makeText(context, "Could not play the tone.", Toast.LENGTH_SHORT).show()
            }
        }

        fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacks(timeout)
            player?.release()
            player = null
            if (hasFocus) {
                audio.abandonAudioFocusRequest(focus)
                hasFocus = false
            }
            if (active === this) active = null
            onStopped()
        }
    }
}
