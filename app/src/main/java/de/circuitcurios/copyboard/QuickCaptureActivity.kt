package de.circuitcurios.copyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class QuickCaptureActivity : Activity() {
    private lateinit var store: SnippetStore
    private lateinit var colors: AppColors

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SnippetStore(this)
        colors = resolveAppColors(this)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(colors.background)
        }

        val heading = TextView(this).apply {
            text = "Quick Add"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }

        val help = TextView(this).apply {
            text = "Text sofort als Copyboard-Snippet speichern."
            textSize = 13f
            setTextColor(colors.textSecondary)
            setPadding(0, dp(4), 0, dp(12))
        }

        val titleInput = EditText(this).apply {
            hint = "Titel (optional)"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
        }

        val bodyInput = EditText(this).apply {
            hint = "Text"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            minLines = 5
            maxLines = 12
            gravity = Gravity.TOP
        }

        val groups = store.getGroups().ifEmpty { mutableListOf("General") }
        val groupSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@QuickCaptureActivity, android.R.layout.simple_spinner_dropdown_item, groups)
        }

        val clipboardButton = Button(this).apply {
            text = "Zwischenablage einfügen"
            setTextColor(colors.accent)
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this@QuickCaptureActivity)
                    ?.toString()
                    .orEmpty()
                if (text.isBlank()) {
                    Toast.makeText(this@QuickCaptureActivity, "Zwischenablage ist leer.", Toast.LENGTH_SHORT).show()
                } else {
                    bodyInput.setText(text)
                    bodyInput.setSelection(bodyInput.text.length)
                    if (titleInput.text.isBlank()) {
                        titleInput.setText(deriveTitle(text))
                    }
                }
            }
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelButton = Button(this).apply {
            text = "Abbrechen"
            setTextColor(colors.textSecondary)
            setOnClickListener { finish() }
        }

        val saveButton = Button(this).apply {
            text = "Speichern"
            setTextColor(colors.accent)
            setOnClickListener {
                val body = bodyInput.text.toString().trim()
                if (body.isBlank()) {
                    Toast.makeText(this@QuickCaptureActivity, "Text darf nicht leer sein.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val title = titleInput.text.toString().trim().ifBlank { deriveTitle(body) }
                val snippet = Snippet(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    text = body,
                    category = groupSpinner.selectedItem?.toString().orEmpty().ifBlank { "General" }
                )
                store.upsert(snippet)
                CopyboardWidgetProvider.updateAll(this@QuickCaptureActivity)
                Toast.makeText(this@QuickCaptureActivity, "Gespeichert.", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this@QuickCaptureActivity, MainActivity::class.java).apply {
                    putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, snippet.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
        }

        actions.addView(cancelButton)
        actions.addView(saveButton)

        root.addView(heading)
        root.addView(help)
        root.addView(titleInput, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(groupSpinner, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(bodyInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(clipboardButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(actions, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setContentView(root)

        bodyInput.requestFocus()
    }

    private fun deriveTitle(text: String): String {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            firstLine.isBlank() -> "Quick Snippet"
            firstLine.length > 56 -> firstLine.take(53) + "…"
            else -> firstLine
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
