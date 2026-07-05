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

        private val rowIds = intArrayOf(
            R.id.widgetSnippetRow1,
            R.id.widgetSnippetRow2,
            R.id.widgetSnippetRow3,
            R.id.widgetSnippetRow4
        )

        private val contentIds = intArrayOf(
            R.id.widgetSnippetContent1,
            R.id.widgetSnippetContent2,
            R.id.widgetSnippetContent3,
            R.id.widgetSnippetContent4
        )

        private val titleIds = intArrayOf(
            R.id.widgetSnippetTitle1,
            R.id.widgetSnippetTitle2,
            R.id.widgetSnippetTitle3,
            R.id.widgetSnippetTitle4
        )

        private val previewIds = intArrayOf(
            R.id.widgetSnippetPreview1,
            R.id.widgetSnippetPreview2,
            R.id.widgetSnippetPreview3,
            R.id.widgetSnippetPreview4
        )

        private val copyIds = intArrayOf(
            R.id.widgetSnippetCopy1,
            R.id.widgetSnippetCopy2,
            R.id.widgetSnippetCopy3,
            R.id.widgetSnippetCopy4
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

            rowIds.forEachIndexed { index, rowId ->
                val snippet = snippets.getOrNull(index)
                if (snippet == null) {
                    views.setViewVisibility(rowId, View.GONE)
                } else {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(titleIds[index], snippet.title)
                    views.setTextViewText(previewIds[index], snippet.text.previewText())

                    val editIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra(EXTRA_SNIPPET_ID, snippet.id)
                    }
                    val editPendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId * 100 + index,
                        editIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(contentIds[index], editPendingIntent)
                    views.setOnClickPendingIntent(titleIds[index], editPendingIntent)
                    views.setOnClickPendingIntent(previewIds[index], editPendingIntent)

                    val copyIntent = Intent(context, CopySnippetReceiver::class.java).apply {
                        action = ACTION_COPY_SNIPPET
                        putExtra(EXTRA_SNIPPET_ID, snippet.id)
                    }
                    val copyPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 1000 + index,
                        copyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(copyIds[index], copyPendingIntent)
                }
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun String.previewText(): String = replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Leerer Textbaustein" }
    }
}
