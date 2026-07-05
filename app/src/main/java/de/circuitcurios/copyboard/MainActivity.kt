package de.circuitcurios.copyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var store: SnippetStore
    private lateinit var snippetsContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var colors: AppColors
    private var snippets: MutableList<Snippet> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = resolveAppColors(this)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        store = SnippetStore(this)
        snippets = store.getAll()
        buildUi()
        renderSnippets()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(12))
            setBackgroundColor(colors.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Copyboard"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }

        val addButton = Button(this).apply {
            text = "+"
            textSize = 22f
            setTextColor(colors.accent)
            contentDescription = "Snippet hinzufügen"
            setOnClickListener { showEditor(null) }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            addButton,
            LinearLayout.LayoutParams(dp(56), dp(52))
        )

        val helpText = TextView(this).apply {
            text = "Antippen kopiert. Lange drücken bearbeitet. Favoriten erscheinen im Widget."
            textSize = 13f
            setTextColor(colors.textSecondary)
            setPadding(0, dp(4), 0, dp(10))
        }

        searchInput = EditText(this).apply {
            setHint("Suchen …")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(colors.inputSurface, stroke = colors.border)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderSnippets()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        snippetsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(snippetsContainer)
        }

        root.addView(header)
        root.addView(helpText)
        root.addView(searchInput, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun renderSnippets() {
        snippetsContainer.removeAllViews()
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = snippets
            .sortedWith(compareByDescending<Snippet> { it.favorite }.thenBy { it.category.lowercase() }.thenBy { it.title.lowercase() })
            .filter { snippet ->
                query.isBlank() ||
                    snippet.title.lowercase().contains(query) ||
                    snippet.category.lowercase().contains(query) ||
                    snippet.text.lowercase().contains(query)
            }

        if (filtered.isEmpty()) {
            snippetsContainer.addView(
                TextView(this).apply {
                    text = "Nichts gefunden. Zeit für mehr Textbausteine."
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

        filtered.forEach { snippet ->
            snippetsContainer.addView(buildSnippetCard(snippet))
        }
    }

    private fun buildSnippetCard(snippet: Snippet): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(colors.surface, stroke = colors.border)
            isClickable = true
            isFocusable = true
            setOnClickListener { copySnippet(snippet) }
            setOnLongClickListener {
                showEditor(snippet)
                true
            }
        }

        val titleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = snippet.title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }

        val category = TextView(this).apply {
            text = if (snippet.favorite) "★ ${snippet.category}" else snippet.category
            textSize = 12f
            setTextColor(colors.textSecondary)
            gravity = Gravity.END
        }

        val preview = TextView(this).apply {
            text = snippet.text.replace("\n", " ").let { if (it.length > 140) it.take(140) + "…" else it }
            textSize = 14f
            setTextColor(colors.textSecondary)
            setPadding(0, dp(8), 0, 0)
        }

        titleLine.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleLine.addView(category, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        card.addView(titleLine)
        card.addView(preview)

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(12), 0, 0)
        }
        card.layoutParams = lp
        return card
    }

    private fun showEditor(existing: Snippet?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }

        val titleInput = EditText(this).apply {
            setHint("Titel")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setText(existing?.title.orEmpty())
        }

        val categoryInput = EditText(this).apply {
            setHint("Kategorie")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setText(existing?.category ?: "General")
        }

        val textInput = EditText(this).apply {
            setHint("Textbaustein")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            minLines = 5
            maxLines = 10
            gravity = Gravity.TOP
            setText(existing?.text.orEmpty())
        }

        val favoriteBox = CheckBox(this).apply {
            text = "Favorit / im Widget anzeigen"
            setTextColor(colors.textPrimary)
            isChecked = existing?.favorite ?: false
        }

        layout.addView(titleInput)
        layout.addView(categoryInput)
        layout.addView(textInput)
        layout.addView(favoriteBox)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Snippet hinzufügen" else "Snippet bearbeiten")
            .setView(layout)
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
            .apply {
                if (existing != null) {
                    setNeutralButton("Löschen", null)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val text = textInput.text.toString()
                val category = categoryInput.text.toString().trim().ifBlank { "General" }

                if (title.isBlank() || text.isBlank()) {
                    Toast.makeText(this, "Titel und Text dürfen nicht leer sein.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val snippet = Snippet(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    text = text,
                    category = category,
                    favorite = favoriteBox.isChecked
                )
                store.upsert(snippet)
                snippets = store.getAll()
                CopyboardWidgetProvider.updateAll(this)
                hideKeyboard(textInput)
                renderSnippets()
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                existing?.let { snippet ->
                    store.delete(snippet.id)
                    snippets = store.getAll()
                    CopyboardWidgetProvider.updateAll(this)
                    renderSnippets()
                }
                dialog.dismiss()
            }
        }

        dialog.show()
        titleInput.requestFocus()
    }

    private fun copySnippet(snippet: Snippet) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(snippet.title, snippet.text))
        Toast.makeText(this, "Kopiert: ${snippet.title}", Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun roundedBackground(color: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
