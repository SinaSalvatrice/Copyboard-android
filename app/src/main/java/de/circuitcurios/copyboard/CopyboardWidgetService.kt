package de.circuitcurios.copyboard

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class CopyboardWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return CopyboardWidgetFactory(applicationContext, appWidgetId)
    }
}

private class CopyboardWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var snippets: List<Snippet> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        snippets = CopyboardWidgetProvider.widgetSnippets(context, appWidgetId)
    }

    override fun onDestroy() {
        snippets = emptyList()
    }

    override fun getCount(): Int = snippets.size

    override fun getViewAt(position: Int): RemoteViews {
        val snippet = snippets.getOrNull(position)
        val views = RemoteViews(context.packageName, R.layout.widget_copyboard_row)

        if (snippet == null) {
            views.setTextViewText(R.id.widgetSnippetRowTitle, "")
            views.setTextViewText(R.id.widgetSnippetRowPreview, "")
            return views
        }

        val label = if (snippet.favorite) "★ ${snippet.title}" else snippet.title
        views.setTextViewText(R.id.widgetSnippetRowTitle, label)
        views.setTextViewText(R.id.widgetSnippetRowPreview, snippet.previewText())

        val fillInIntent = Intent().apply {
            putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, snippet.id)
        }
        views.setOnClickFillInIntent(R.id.widgetSnippetRowRoot, fillInIntent)
        views.setOnClickFillInIntent(R.id.widgetSnippetRowCopy, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return snippets.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
