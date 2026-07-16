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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = notePrefs(context).edit()
        appWidgetIds.forEach { appWidgetId ->
            editor.remove(selectedNoteKey(appWidgetId))
        }
        editor.apply()
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FloatingNotesWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_floating_notes)
            val store = SnippetStore(context)
            val selectedId = selectedNoteId(context, appWidgetId)
            val note = selectedId?.let { id -> store.getAll().firstOrNull { it.id == id } }

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetNotesOpenApp, openAppPendingIntent)

            val configureIntent = Intent(context, NoteWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configurePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 2000 + 1,
                configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetNotesChange, configurePendingIntent)

            if (note == null) {
                views.setTextViewText(R.id.widgetNotesTitle, "Notiz wählen")
                views.setTextViewText(R.id.widgetNoteBody, "Dieses Widget zeigt eine feste Notiz. Tippe auf Wählen und such dir eine aus.")
                views.setViewVisibility(R.id.widgetNoteCopy, View.GONE)
                views.setOnClickPendingIntent(R.id.widgetNotesTitle, configurePendingIntent)
                views.setOnClickPendingIntent(R.id.widgetNoteBody, configurePendingIntent)
            } else {
                views.setTextViewText(R.id.widgetNotesTitle, note.title)
                views.setTextViewText(R.id.widgetNoteBody, note.clipboardText().ifBlank { note.previewText() })
                views.setViewVisibility(R.id.widgetNoteCopy, View.VISIBLE)

                val openNoteIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, note.id)
                }
                val openNotePendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 3000 + 1,
                    openNoteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetNotesTitle, openNotePendingIntent)
                views.setOnClickPendingIntent(R.id.widgetNoteBody, openNotePendingIntent)

                val copyIntent = Intent(context, CopySnippetReceiver::class.java).apply {
                    action = CopyboardWidgetProvider.ACTION_COPY_SNIPPET
                    putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, note.id)
                }
                val copyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 4000 + 1,
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetNoteCopy, copyPendingIntent)
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        fun setSelectedNote(context: Context, appWidgetId: Int, snippetId: String) {
            notePrefs(context).edit()
                .putString(selectedNoteKey(appWidgetId), snippetId)
                .apply()
        }

        private fun selectedNoteId(context: Context, appWidgetId: Int): String? {
            return notePrefs(context).getString(selectedNoteKey(appWidgetId), null)
        }

        private fun notePrefs(context: Context) =
            context.getSharedPreferences(NOTE_WIDGET_PREFS_NAME, Context.MODE_PRIVATE)

        private fun selectedNoteKey(appWidgetId: Int) = "selected_note_$appWidgetId"

        private const val NOTE_WIDGET_PREFS_NAME = "copyboard_note_widgets"
    }
}
