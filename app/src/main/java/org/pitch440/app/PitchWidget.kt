package org.pitch440.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class PitchWidget : AppWidgetProvider() {
    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PitchWidget::class.java))
            if (ids.isNotEmpty()) PitchWidget().onUpdate(context, manager, ids)
        }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pitch = PitchPractice.current(context)
        val play = PendingIntent.getForegroundService(
            context, 440,
            Intent(context, WidgetToneService::class.java).setAction(WidgetToneService.PLAY)
                .setData(Uri.parse("pitch440://widget/${PitchPractice.promptId(context)}"))
                .putExtra(PitchPractice.PROMPT_EXTRA, PitchPractice.promptId(context)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.pitch_widget)
            views.setTextViewText(R.id.widget_play, if (pitch == PitchNotes.A) "440" else PitchNotes.label(pitch).substringBefore(" / "))
            views.setContentDescription(R.id.widget_play, "Play ${PitchNotes.displayLabel(pitch)}")
            views.setOnClickPendingIntent(R.id.widget_play, play)
            manager.updateAppWidget(id, views)
        }
    }
}
