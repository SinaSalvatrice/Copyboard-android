package de.circuitcurios.copyboard

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

class NoteWidgetConfigureActivity : Activity() {
    private lateinit var store: SnippetStore
    private lateinit var colors: AppColors
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var snippets: List<Snippet> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        colors = resolveAppColors(this)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        store = SnippetStore(this)
        snippets = store.getAll().sortedWith(compareByDescending<Snippet> { it.favorite }.thenBy { it.title.lowercase() })

        buildUi()
        renderList()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(12))
            setBackgroundColor(colors.background)
        }

        val title = TextView(this).apply {
            text = "Notiz für Widget wählen"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }

        val help = TextView(this).apply {
            text = "Diese Notiz wird direkt im Widget angezeigt. Tippen im Widget öffnet die Notiz, der Kopieren-Button kopiert den kompletten Text."
            textSize = 13f
            setTextColor(colors.textSecondary)
            setPadding(0, dp(4), 0, dp(12))
        }

        searchInput = EditText(this).apply {
            hint = "Notiz suchen …"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(colors.inputSurface, colors.border)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderList()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(listContainer)
        }

        root.addView(title)
        root.addView(help)
        root.addView(searchInput, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val visible = snippets.filter { snippet ->
            query.isBlank() || snippet.searchText().contains(query)
        }

        if (visible.isEmpty()) {
            listContainer.addView(
                TextView(this).apply {
                    text = "Keine passende Notiz gefunden."
                    setTextColor(colors.textSecondary)
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(40), 0, 0)
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            return
        }

        visible.forEach { snippet ->
            listContainer.addView(noteRow(snippet))
        }
    }

    private fun noteRow(snippet: Snippet): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(colors.surface, colors.border)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { chooseSnippet(snippet) }

            addView(TextView(this@NoteWidgetConfigureActivity).apply {
                text = if (snippet.favorite) "★ ${snippet.title}" else snippet.title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colors.textPrimary)
            })
            addView(TextView(this@NoteWidgetConfigureActivity).apply {
                text = snippet.previewText().let { if (it.length > 180) it.take(180) + "…" else it }
                textSize = 13f
                setTextColor(colors.textSecondary)
                setPadding(0, dp(6), 0, 0)
            })

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(10), 0, 0)
            }
        }
    }

    private fun chooseSnippet(snippet: Snippet) {
        FloatingNotesWidgetProvider.setSelectedNote(this, appWidgetId, snippet.id)
        val manager = AppWidgetManager.getInstance(this)
        FloatingNotesWidgetProvider.updateWidget(this, manager, appWidgetId)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun roundedBackground(color: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
