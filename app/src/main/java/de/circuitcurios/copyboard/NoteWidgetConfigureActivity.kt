package de.circuitcurios.copyboard

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import kotlin.math.roundToInt

class NoteWidgetConfigureActivity : Activity() {
    private lateinit var store: NoteStore
    private lateinit var colors: AppColors
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var notes: List<Note> = emptyList()

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
        store = NoteStore(this)
        reloadNotes()

        buildUi()
        renderList()
    }

    private fun reloadNotes() {
        notes = store.getAll().sortedByDescending { it.updatedAt }
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
            text = "Das Widget zeigt eine echte Copyboard-Notiz, nicht deine Snippets. Antippen wählt die Notiz. Lange drücken bearbeitet sie."
            textSize = 13f
            setTextColor(colors.textSecondary)
            setPadding(0, dp(4), 0, dp(12))
        }

        val newButton = Button(this).apply {
            text = "+ Neue Notiz"
            setTextColor(colors.accent)
            setOnClickListener { showNoteEditor(null) }
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
        root.addView(newButton, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(searchInput, LinearLayout.LayoutParams.MATCH_PARENT, dp(48).also { })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val visible = notes.filter { note ->
            query.isBlank() || note.searchText().contains(query)
        }

        if (visible.isEmpty()) {
            listContainer.addView(
                TextView(this).apply {
                    text = "Noch keine passende Notiz. Tippe oben auf + Neue Notiz."
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

        visible.forEach { note ->
            listContainer.addView(noteRow(note))
        }
    }

    private fun noteRow(note: Note): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(colors.surface, colors.border)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { chooseNote(note) }
            setOnLongClickListener {
                showNoteEditor(note)
                true
            }

            addView(TextView(this@NoteWidgetConfigureActivity).apply {
                text = note.title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colors.textPrimary)
            })
            addView(TextView(this@NoteWidgetConfigureActivity).apply {
                text = note.previewText().let { if (it.length > 180) it.take(180) + "…" else it }
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

    private fun chooseNote(note: Note) {
        FloatingNotesWidgetProvider.setSelectedNote(this, appWidgetId, note.id)
        val manager = AppWidgetManager.getInstance(this)
        FloatingNotesWidgetProvider.updateWidget(this, manager, appWidgetId)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun showNoteEditor(existing: Note?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }

        val titleInput = EditText(this).apply {
            hint = "Titel"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setText(existing?.title.orEmpty())
        }

        val bodyInput = EditText(this).apply {
            hint = "Notiz schreiben …"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            setText(existing?.body.orEmpty())
        }

        layout.addView(titleInput)
        layout.addView(bodyInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Neue Notiz" else "Notiz bearbeiten")
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
                val title = titleInput.text.toString().trim().ifBlank { "Notiz" }
                val body = bodyInput.text.toString()
                if (body.isBlank()) {
                    Toast.makeText(this, "Notizinhalt fehlt.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val note = Note(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    body = body
                )
                store.upsert(note)
                reloadNotes()
                renderList()
                FloatingNotesWidgetProvider.updateAll(this)
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                existing?.let { note ->
                    store.delete(note.id)
                    reloadNotes()
                    renderList()
                    FloatingNotesWidgetProvider.updateAll(this)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
        titleInput.requestFocus()
    }

    private fun roundedBackground(color: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
