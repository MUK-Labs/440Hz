package org.pitch440.app

import android.content.Context
import java.util.UUID

/** One persistent prompt shared by the screen, reminder and all playback controls. */
object PitchPractice {
    const val PROMPT_EXTRA = "practicePrompt"
    fun promptId(c: Context): String = Preferences(c).data.getString(PROMPT_EXTRA, "initial") ?: "initial"
    fun selectedMask(c: Context): Int = PitchNotes.normalizeMask(
        Preferences(c).data.getInt("practiceMask", PitchNotes.DEFAULT_MASK))

    fun current(c: Context): Int {
        val pitch = PitchNotes.normalizePitch(Preferences(c).data.getInt("practicePitch", PitchNotes.A))
        val mask = selectedMask(c)
        return if (mask and (1 shl pitch) != 0) pitch else PitchNotes.choose(mask, 0.0)
    }

    fun chooseNext(c: Context): Int {
        val pitch = PitchNotes.choose(selectedMask(c), Math.random())
        replacePrompt(c, pitch)
        return pitch
    }

    fun setSelection(c: Context, selected: Int) {
        val mask = PitchNotes.normalizeMask(selected)
        val old = current(c)
        val pitch = if (mask and (1 shl old) != 0) old else PitchNotes.choose(mask, Math.random())
        Reminders.clearPrompt(c)
        TonePlayback.stopAll()
        Preferences(c).data.edit().putInt("practiceMask", mask).putInt("practicePitch", pitch)
            .putString(PROMPT_EXTRA, UUID.randomUUID().toString()).commit()
        PitchWidget.updateAll(c)
    }

    private fun replacePrompt(c: Context, pitch: Int) {
        Reminders.clearPrompt(c)
        TonePlayback.stopAll()
        Preferences(c).data.edit().putInt("practicePitch", pitch)
            .putString(PROMPT_EXTRA, UUID.randomUUID().toString()).commit()
        PitchWidget.updateAll(c)
    }
}
