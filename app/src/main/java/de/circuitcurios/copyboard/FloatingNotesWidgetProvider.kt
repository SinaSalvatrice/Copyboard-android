package de.circuitcurios.copyboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class FloatingNotesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private val rowIds = intArrayOf(
            R.id.widgetNoteRow1,
            R.id.widgetNoteRow2,
            R.id.widgetNoteRow3
        )

        private val contentIds = intArrayOf(
            R.id.widgetNoteContent1,
            R.id.widgetNoteContent2,
            R.id.widgetNoteContent3
        )

        private val titleIds = intArrayOf(
            R.id.widgetNoteTitle1,
            R.id.widgetNoteTitle2,
            R.id.widgetNoteTitle3
        )

        private val bodyIds = intArrayOf(
            R.id.widgetNoteBody1,
            R.id.widgetNoteBody2,
            R.id.widgetNoteBody3
        )

        private val copyIds = intArrayOf(
            R.id.widgetNoteCopy1,
            R.id.widgetNoteCopy2,
            R.id.widgetNoteCopy3
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FloatingNotesWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_floating_notes)
            val snippets = SnippetStore(context).getAll()
                .sortedWith(compareByDescending<Snippet> { it.favorite }.thenBy { it.title.lowercase() })
                .take(3)

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetNotesTitle, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widgetNotesOpenApp, openAppPendingIntent)

            rowIds.forEachIndexed { index, rowId ->
                val snippet = snippets.getOrNull(index)
                if (snippet == null) {
                    views.setViewVisibility(rowId, View.GONE)
                } else {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(titleIds[index], snippet.title)
                    views.setTextViewText(bodyIds[index], snippet.text.previewText())

                    val openSnippetIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, snippet.id)
                    }
                    val openSnippetPendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId * 100 + index,
                        openSnippetIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(contentIds[index], openSnippetPendingIntent)
                    views.setOnClickPendingIntent(titleIds[index], openSnippetPendingIntent)
                    views.setOnClickPendingIntent(bodyIds[index], openSnippetPendingIntent)

                    val copyIntent = Intent(context, CopySnippetReceiver::class.java).apply {
                        action = CopyboardWidgetProvider.ACTION_COPY_SNIPPET
                        putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, snippet.id)
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
            .ifBlank { "Leere Notiz" }
            .let { if (it.length > 110) it.take(110) + "…" else it }
    }
}
