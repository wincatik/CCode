package com.ccode.code

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class CCodeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onEnabled(context: Context) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CCodeWidget::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val settings = context.getSharedPreferences("ccode-settings", Context.MODE_PRIVATE)
            val model = settings.getString("selected_model_title", "Code 1.0") ?: "Code 1.0"
            val chats = settings.getInt("chat_count", 0)
            val messages = settings.getInt("sent_count", 0)
            val streak = settings.getInt("current_streak", 0)
            val best = settings.getInt("best_streak", 0)
val status = settings.getString("widget_status", "Ready") ?: "Ready"
            val views = RemoteViews(context.packageName, R.layout.widget_ccode).apply {
                setTextViewText(R.id.widget_model, model)
setTextViewText(R.id.widget_stats, "Chats: $chats · Messages: $messages")
setTextViewText(R.id.widget_streak, "Streak: $streak days · Best: $best")
                setTextViewText(R.id.widget_status, status)
                val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
                val pending = PendingIntent.getActivity(context, 3000 + id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_open, pending)
            }
            manager.updateAppWidget(id, views)
        }
    }
}

fun updateCCodeWidgets(context: Context) = CCodeWidget.updateAll(context)
