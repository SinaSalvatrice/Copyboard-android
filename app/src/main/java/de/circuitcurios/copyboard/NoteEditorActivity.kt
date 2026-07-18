package de.circuitcurios.copyboard

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import kotlin.math.roundToInt

class NoteEditorActivity : Activity() {
    private lateinit var store: NoteStore
    private lateinit var colors: AppColors
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private var noteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = resolveAppColors(this)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        store = NoteStore(this)
        noteId = intent?.getStringExtra(CopyNoteReceiver.EXTRA_NOTE_ID)
        buildUi(store.get(noteId.orEmpty()))
    }

    private fun buildUi(existing: Note?) {
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
            text = if (existing == null) "Neue Notiz" else "Notiz bearbeiten"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }

        val closeButton = Button(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(colors.accent)
            setOnClickListener { finish() }
        }

        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeButton, LinearLayout.LayoutParams(dp(56), dp(52)))

        titleInput = EditText(this).apply {
            hint = "Titel"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setText(existing?.title.orEmpty())
            background = roundedBackground(colors.inputSurface, colors.border)
            setPadding(dp(12), 0, dp(12), 0)
        }

        bodyInput = EditText(this).apply {
            hint = "Schreib hier deine Notiz …"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            setText(existing?.body.orEmpty())
            background = roundedBackground(colors.inputSurface, colors.border)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val saveButton = Button(this).apply {
            text = "Speichern"
            setTextColor(colors.accent)
            setOnClickListener { saveNote() }
        }

        buttonRow.addView(saveButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        if (existing != null) {
            val deleteButton = Button(this).apply {
                text = "Löschen"
                setTextColor(colors.accent)
                setOnClickListener { confirmDelete(existing) }
            }
            buttonRow.addView(deleteButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(8), 0, 0, 0)
            })
        }

        root.addView(header)
        root.addView(titleInput, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(bodyInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, dp(10), 0, 0)
        })
        root.addView(buttonRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setContentView(root)

        titleInput.requestFocus()
    }

    private fun saveNote() {
        val title = titleInput.text.toString().trim().ifBlank { "Notiz" }
        val body = bodyInput.text.toString()

        if (body.isBlank()) {
            Toast.makeText(this, "Notizinhalt fehlt.", Toast.LENGTH_SHORT).show()
            return
        }

        val note = Note(
            id = noteId ?: UUID.randomUUID().toString(),
            title = title,
            body = body
        )
        val saved = store.upsert(note)
        noteId = saved.id
        FloatingNotesWidgetProvider.updateAll(this)
        hideKeyboard(bodyInput)
        Toast.makeText(this, "Notiz gespeichert.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Notiz löschen")
            .setMessage("Diese Notiz wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                store.delete(note.id)
                FloatingNotesWidgetProvider.updateAll(this)
                Toast.makeText(this, "Notiz gelöscht.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun hideKeyboard(view: android.view.View) {
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
