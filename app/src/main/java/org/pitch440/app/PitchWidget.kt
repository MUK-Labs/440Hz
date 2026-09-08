package org.pitch440.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PitchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val play = PendingIntent.getForegroundService(
            context, 440,
            Intent(context, WidgetToneService::class.java).setAction(WidgetToneService.PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.pitch_widget)
            views.setOnClickPendingIntent(R.id.widget_play, play)
            manager.updateAppWidget(id, views)
        }
    }
}
