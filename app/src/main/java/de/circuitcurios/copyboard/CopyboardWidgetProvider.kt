package de.circuitcurios.copyboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class CopyboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_COPY_SNIPPET = "de.circuitcurios.copyboard.ACTION_COPY_SNIPPET"
        const val EXTRA_SNIPPET_ID = "snippet_id"

        private val buttonIds = intArrayOf(
            R.id.widgetSnippet1,
            R.id.widgetSnippet2,
            R.id.widgetSnippet3,
            R.id.widgetSnippet4
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CopyboardWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_copyboard)
            val store = SnippetStore(context)
            val all = store.getAll()
            val snippets = all.filter { it.favorite }.ifEmpty { all }.take(4)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widgetOpenApp, openPendingIntent)

            buttonIds.forEachIndexed { index, buttonId ->
                val snippet = snippets.getOrNull(index)
                if (snippet == null) {
                    views.setViewVisibility(buttonId, View.GONE)
                } else {
                    views.setViewVisibility(buttonId, View.VISIBLE)
                    views.setTextViewText(buttonId, snippet.title)
                    val copyIntent = Intent(context, CopySnippetReceiver::class.java).apply {
                        action = ACTION_COPY_SNIPPET
                        putExtra(EXTRA_SNIPPET_ID, snippet.id)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 100 + index,
                        copyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(buttonId, pendingIntent)
                }
            }

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
