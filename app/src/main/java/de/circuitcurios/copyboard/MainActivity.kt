package de.circuitcurios.copyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var store: SnippetStore
    private lateinit var snippetsContainer: LinearLayout
    private lateinit var groupFilterContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var colors: AppColors
    private var snippets: MutableList<Snippet> = mutableListOf()
    private var importReplaceExisting: Boolean = false
    private var selectedGroup: String? = null

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT_BACKUP -> exportBackup(uri)
            REQUEST_IMPORT_BACKUP -> importBackup(uri)
        }
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
            text = "Antippen kopiert. Lange drücken bearbeitet. Gruppen filtern die Liste."
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

        val backupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val exportButton = Button(this).apply {
            text = "Backup"
            setTextColor(colors.accent)
            setOnClickListener { startBackupExport() }
        }

        val importButton = Button(this).apply {
            text = "Wiederherstellen"
            setTextColor(colors.accent)
            setOnClickListener { showImportModeDialog() }
        }

        backupRow.addView(exportButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        backupRow.addView(importButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(8), 0, 0, 0)
        })

        groupFilterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val groupFilterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(groupFilterContainer)
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
        root.addView(backupRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(groupFilterScroll, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun renderSnippets() {
        renderGroupFilters()
        snippetsContainer.removeAllViews()
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val activeGroup = selectedGroup
        val filtered = snippets
            .sortedWith(compareByDescending<Snippet> { it.favorite }.thenBy { it.category.lowercase() }.thenBy { it.title.lowercase() })
            .filter { snippet -> activeGroup == null || snippet.category == activeGroup }
            .filter { snippet ->
                query.isBlank() ||
                    snippet.title.lowercase().contains(query) ||
                    snippet.category.lowercase().contains(query) ||
                    snippet.text.lowercase().contains(query)
            }

        if (filtered.isEmpty()) {
            snippetsContainer.addView(
                TextView(this).apply {
                    text = if (activeGroup == null) {
                        "Nichts gefunden. Zeit für mehr Textbausteine."
                    } else {
                        "Keine Textbausteine in dieser Gruppe."
                    }
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

    private fun renderGroupFilters() {
        groupFilterContainer.removeAllViews()
        val groups = snippets.map { it.category.ifBlank { "General" } }.distinct().sorted()
        if (selectedGroup != null && selectedGroup !in groups) {
            selectedGroup = null
        }

        addGroupFilter("Alle", selectedGroup == null, colors.accent) {
            selectedGroup = null
            renderSnippets()
        }

        groups.forEach { group ->
            addGroupFilter(group, selectedGroup == group, groupColor(group)) {
                selectedGroup = group
                renderSnippets()
            }
        }
    }

    private fun addGroupFilter(label: String, selected: Boolean, markerColor: Int, onClick: () -> Unit) {
        val backgroundColor = if (selected) markerColor else colors.surface
        val textColor = if (selected) Color.WHITE else colors.textPrimary
        val chip = TextView(this).apply {
            text = label
            textSize = 13f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(textColor)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = roundedBackground(backgroundColor, stroke = if (selected) markerColor else colors.border)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        groupFilterContainer.addView(chip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, dp(8), 0)
        })
    }

    private fun buildSnippetCard(snippet: Snippet): View {
        val groupColor = groupColor(snippet.category)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(colors.surface, stroke = colors.border)
            isClickable = true
            isFocusable = true
            setOnClickListener { copySnippet(snippet) }
            setOnLongClickListener {
                showEditor(snippet)
                true
            }
        }

        val marker = View(this).apply {
            setBackgroundColor(groupColor)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(14), dp(12))
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
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(groupColor)
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
        content.addView(titleLine)
        content.addView(preview)
        card.addView(marker, LinearLayout.LayoutParams(dp(6), LinearLayout.LayoutParams.MATCH_PARENT))
        card.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

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
            setHint("Gruppe")
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

    private fun startBackupExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, backupFileName())
        }
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP)
    }

    private fun showImportModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Backup wiederherstellen")
            .setMessage("Willst du deine aktuellen Textbausteine ersetzen oder das Backup hinzufügen?")
            .setPositiveButton("Ersetzen") { _, _ ->
                importReplaceExisting = true
                startBackupImport()
            }
            .setNegativeButton("Hinzufügen") { _, _ ->
                importReplaceExisting = false
                startBackupImport()
            }
            .setNeutralButton("Abbrechen", null)
            .show()
    }

    private fun startBackupImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_IMPORT_BACKUP)
    }

    private fun exportBackup(uri: Uri) {
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(store.exportBackupJson())
            } ?: error("No output stream")
        }.onSuccess {
            Toast.makeText(this, "Backup gespeichert.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Backup konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importBackup(uri: Uri) {
        runCatching {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("No input stream")
            store.importBackupJson(raw, importReplaceExisting)
        }.onSuccess { count ->
            snippets = store.getAll()
            selectedGroup = null
            CopyboardWidgetProvider.updateAll(this)
            renderSnippets()
            Toast.makeText(this, "$count Textbausteine wiederhergestellt.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Backup konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun backupFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        return "copyboard-backup-$date.json"
    }

    private fun groupColor(group: String): Int {
        val palette = intArrayOf(
            Color.rgb(231, 111, 81),
            Color.rgb(42, 157, 143),
            Color.rgb(38, 70, 83),
            Color.rgb(131, 56, 236),
            Color.rgb(33, 150, 243),
            Color.rgb(244, 120, 0),
            Color.rgb(0, 137, 123),
            Color.rgb(156, 39, 176)
        )
        return palette[Math.floorMod(group.hashCode(), palette.size)]
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

    companion object {
        private const val REQUEST_EXPORT_BACKUP = 1001
        private const val REQUEST_IMPORT_BACKUP = 1002
    }
}
