package de.circuitcurios.copyboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class CopyboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_CYCLE_GROUP) {
            return
        }

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }

        cycleGroupForWidget(context, appWidgetId)
        val manager = AppWidgetManager.getInstance(context)
        updateWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = widgetPrefs(context)
        val editor = prefs.edit()
        appWidgetIds.forEach { widgetId ->
            editor.remove(widgetGroupKey(widgetId))
        }
        editor.apply()
    }

    companion object {
        const val ACTION_COPY_SNIPPET = "de.circuitcurios.copyboard.ACTION_COPY_SNIPPET"
        private const val ACTION_CYCLE_GROUP = "de.circuitcurios.copyboard.ACTION_CYCLE_GROUP"
        const val EXTRA_SNIPPET_ID = "snippet_id"
        const val WIDGET_ITEM_LIMIT = 20

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CopyboardWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun widgetSnippets(context: Context, appWidgetId: Int): List<Snippet> {
            val store = SnippetStore(context)
            val all = store.getAll()
            val groups = store.getGroups()
            val activeGroup = resolveGroupForWidget(context, appWidgetId, groups)
            val inGroup = all.filter { it.category == activeGroup }

            return inGroup.filter { it.favorite }
                .ifEmpty { inGroup }
                .ifEmpty { all.filter { it.favorite } }
                .ifEmpty { all }
                .sortedWith(compareByDescending<Snippet> { it.favorite }.thenBy { it.title.lowercase() })
                .take(WIDGET_ITEM_LIMIT)
        }

        fun resolveGroupForWidget(context: Context, appWidgetId: Int, groups: List<String>): String {
            if (groups.isEmpty()) {
                return "General"
            }

            val prefs = widgetPrefs(context)
            val stored = prefs.getString(widgetGroupKey(appWidgetId), null)
            if (stored != null && groups.contains(stored)) {
                return stored
            }

            val defaultGroup = groups.first()
            prefs.edit().putString(widgetGroupKey(appWidgetId), defaultGroup).apply()
            return defaultGroup
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_copyboard)
            val store = SnippetStore(context)
            val groups = store.getGroups()
            val activeGroup = resolveGroupForWidget(context, appWidgetId, groups)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widgetOpenApp, openPendingIntent)
            views.setTextViewText(R.id.widgetGroupPicker, activeGroup)

            val cycleIntent = Intent(context, CopyboardWidgetProvider::class.java).apply {
                action = ACTION_CYCLE_GROUP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val cyclePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10_000,
                cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetGroupPicker, cyclePendingIntent)

            val serviceIntent = Intent(context, CopyboardWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetSnippetList, serviceIntent)
            views.setEmptyView(R.id.widgetSnippetList, R.id.widgetEmptyText)

            val copyTemplateIntent = Intent(context, CopySnippetReceiver::class.java).apply {
                action = ACTION_COPY_SNIPPET
            }
            val copyTemplate = PendingIntent.getBroadcast(
                context,
                appWidgetId * 1000,
                copyTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widgetSnippetList, copyTemplate)

            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetSnippetList)
        }

        private fun cycleGroupForWidget(context: Context, appWidgetId: Int) {
            val groups = SnippetStore(context).getGroups()
            if (groups.isEmpty()) {
                return
            }

            val current = resolveGroupForWidget(context, appWidgetId, groups)
            val nextIndex = (groups.indexOf(current) + 1).let { if (it >= groups.size) 0 else it }
            widgetPrefs(context).edit().putString(widgetGroupKey(appWidgetId), groups[nextIndex]).apply()
        }

        private fun widgetPrefs(context: Context) =
            context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)

        private fun widgetGroupKey(appWidgetId: Int) = "widget_group_$appWidgetId"

        private const val WIDGET_PREFS_NAME = "copyboard_widget"
    }
}
