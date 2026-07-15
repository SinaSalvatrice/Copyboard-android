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
import android.text.InputType
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
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
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
    private var groups: MutableList<String> = mutableListOf()
    private var importReplaceExisting: Boolean = false
    private var selectedGroup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = resolveAppColors(this)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        store = SnippetStore(this)
        snippets = store.getAll()
        groups = store.getGroups()
        buildUi()
        renderSnippets()
        openSnippetFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        snippets = store.getAll()
        groups = store.getGroups()
        renderSnippets()
        openSnippetFromIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT_BACKUP -> exportBackup(uri)
            REQUEST_IMPORT_BACKUP -> importBackup(uri)
            REQUEST_CREATE_SYNC_FILE -> {
                rememberSyncFile(uri, data.flags)
                syncSave()
            }
            REQUEST_OPEN_SYNC_FILE -> {
                rememberSyncFile(uri, data.flags)
                askLoadFromSyncFile()
            }
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

        val actionRow = LinearLayout(this).apply {
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
            text = "Restore"
            setTextColor(colors.accent)
            setOnClickListener { showImportModeDialog() }
        }

        val syncButton = Button(this).apply {
            text = "Sync"
            setTextColor(colors.accent)
            setOnClickListener { showSyncDialog() }
        }

        val groupsButton = Button(this).apply {
            text = "Groups"
            setTextColor(colors.accent)
            setOnClickListener { showGroupManagementDialog() }
        }

        actionRow.addView(exportButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        actionRow.addView(importButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(8), 0, 0, 0)
        })
        actionRow.addView(syncButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(8), 0, 0, 0)
        })
        actionRow.addView(groupsButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
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
        root.addView(actionRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
                    snippet.searchText().contains(query)
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
            val typeBadge = if (snippet.mode == "checklist") " · checklist" else ""
            text = if (snippet.favorite) "★ ${snippet.category}$typeBadge" else snippet.category + typeBadge
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(groupColor)
            gravity = Gravity.END
        }

        val preview = TextView(this).apply {
            text = snippet.previewText().let { if (it.length > 140) it.take(140) + "…" else it }
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

    private fun openSnippetFromIntent(intent: Intent?) {
        val snippetId = intent?.getStringExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID) ?: return
        val snippet = snippets.firstOrNull { it.id == snippetId }
        if (snippet != null) {
            showEditor(snippet)
        }
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

        val groupOptions = groups.ifEmpty { mutableListOf("General") }
        val categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, groupOptions)
            val initialGroup = existing?.category ?: groupOptions.first()
            val initialIndex = groupOptions.indexOf(initialGroup).let { if (it >= 0) it else 0 }
            setSelection(initialIndex)
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

        val modeOptions = listOf("Text", "Checklist")
        val modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modeOptions)
            setSelection(if (existing?.mode == "checklist") 1 else 0)
        }

        val checklistInput = EditText(this).apply {
            setHint("Checklist, eine Zeile pro Punkt. Optional: [x] erledigt")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            minLines = 5
            maxLines = 10
            gravity = Gravity.TOP
            setText(existing?.checklistItems?.joinToString("\n") {
                "[${if (it.done) "x" else " "}] ${it.text}"
            }.orEmpty())
            visibility = if (existing?.mode == "checklist") View.VISIBLE else View.GONE
        }

        textInput.visibility = if (existing?.mode == "checklist") View.GONE else View.VISIBLE

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val checklist = position == 1
                textInput.visibility = if (checklist) View.GONE else View.VISIBLE
                checklistInput.visibility = if (checklist) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val favoriteBox = CheckBox(this).apply {
            text = "Favorit / im Widget anzeigen"
            setTextColor(colors.textPrimary)
            isChecked = existing?.favorite ?: false
        }

        layout.addView(titleInput)
        layout.addView(categorySpinner)
        layout.addView(modeSpinner)
        layout.addView(textInput)
        layout.addView(checklistInput)
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
                val mode = if (modeSpinner.selectedItemPosition == 1) "checklist" else "text"
                val checklistItems = parseChecklistInput(checklistInput.text.toString())
                val category = categorySpinner.selectedItem?.toString().orEmpty().ifBlank { "General" }

                val hasContent = if (mode == "checklist") checklistItems.isNotEmpty() else text.isNotBlank()
                if (title.isBlank() || !hasContent) {
                    Toast.makeText(this, "Titel und Notizinhalt dürfen nicht leer sein.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val snippet = Snippet(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    text = text,
                    mode = mode,
                    checklistItems = checklistItems,
                    category = category,
                    favorite = favoriteBox.isChecked
                )
                store.upsert(snippet)
                snippets = store.getAll()
                groups = store.getGroups()
                refreshWidgets()
                hideKeyboard(if (mode == "checklist") checklistInput else textInput)
                renderSnippets()
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                existing?.let { snippet ->
                    store.delete(snippet.id)
                    snippets = store.getAll()
                    groups = store.getGroups()
                    refreshWidgets()
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

    private fun showGroupManagementDialog() {
        val options = mutableListOf<String>()
        options.add("Neue Gruppe erstellen")
        options.add("Gruppe umbenennen")
        options.add("Textbausteine verschieben")
        groups.filter { it != "General" }.forEach { group ->
            options.add("Gruppe löschen: $group")
        }

        AlertDialog.Builder(this)
            .setTitle("Gruppen verwalten")
            .setItems(options.toTypedArray()) { _, which ->
                val selected = options[which]
                if (selected == "Neue Gruppe erstellen") {
                    showCreateGroupDialog()
                } else if (selected == "Gruppe umbenennen") {
                    showRenameGroupDialog()
                } else if (selected == "Textbausteine verschieben") {
                    showMoveSnippetsDialog()
                } else {
                    val group = selected.removePrefix("Gruppe löschen: ").trim()
                    deleteGroup(group)
                }
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply {
            setHint("Gruppenname")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Neue Gruppe")
            .setView(input)
            .setPositiveButton("Erstellen") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Gruppenname fehlt.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (groups.any { it.equals(name, ignoreCase = true) }) {
                    Toast.makeText(this, "Gruppe existiert bereits.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                groups.add(name)
                groups = groups.distinct().sorted().toMutableList()
                store.saveGroups(groups)
                renderSnippets()
                Toast.makeText(this, "Gruppe erstellt.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showRenameGroupDialog() {
        val renameableGroups = groups.filter { it != "General" }
        if (renameableGroups.isEmpty()) {
            Toast.makeText(this, "Keine Gruppe zum Umbenennen vorhanden.", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }

        val sourceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, renameableGroups)
        }

        val targetInput = EditText(this).apply {
            setHint("Neuer Gruppenname")
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
        }

        layout.addView(sourceSpinner)
        layout.addView(targetInput)

        AlertDialog.Builder(this)
            .setTitle("Gruppe umbenennen")
            .setView(layout)
            .setPositiveButton("Speichern") { _, _ ->
                val source = sourceSpinner.selectedItem?.toString().orEmpty()
                val target = targetInput.text.toString().trim()
                renameGroup(source, target)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun renameGroup(source: String, target: String) {
        if (source.isBlank()) {
            Toast.makeText(this, "Keine Gruppe ausgewählt.", Toast.LENGTH_SHORT).show()
            return
        }

        if (target.isBlank()) {
            Toast.makeText(this, "Neuer Gruppenname fehlt.", Toast.LENGTH_SHORT).show()
            return
        }

        if (source.equals(target, ignoreCase = true)) {
            Toast.makeText(this, "Name ist unverändert.", Toast.LENGTH_SHORT).show()
            return
        }

        if (groups.any { it.equals(target, ignoreCase = true) }) {
            Toast.makeText(this, "Zielgruppe existiert bereits.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedSnippets = snippets.map { snippet ->
            if (snippet.category == source) {
                snippet.copy(category = target)
            } else {
                snippet
            }
        }

        groups = groups.map { if (it == source) target else it }.distinctBy { it.lowercase() }.sorted().toMutableList()
        store.saveGroups(groups)
        store.saveAll(updatedSnippets)
        snippets = store.getAll()
        groups = store.getGroups()
        if (selectedGroup == source) {
            selectedGroup = target
        }
        renderSnippets()
        Toast.makeText(this, "Gruppe umbenannt.", Toast.LENGTH_SHORT).show()
    }

    private fun showMoveSnippetsDialog() {
        if (groups.size < 2) {
            Toast.makeText(this, "Mindestens zwei Gruppen nötig.", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }

        val sourceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, groups)
        }

        val targetSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, groups)
            if (groups.size > 1) {
                setSelection(1)
            }
        }

        layout.addView(sourceSpinner)
        layout.addView(targetSpinner)

        AlertDialog.Builder(this)
            .setTitle("Textbausteine verschieben")
            .setView(layout)
            .setPositiveButton("Verschieben") { _, _ ->
                val source = sourceSpinner.selectedItem?.toString().orEmpty()
                val target = targetSpinner.selectedItem?.toString().orEmpty()
                moveSnippetsBetweenGroups(source, target)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun moveSnippetsBetweenGroups(source: String, target: String) {
        if (source.isBlank() || target.isBlank()) {
            Toast.makeText(this, "Quelle und Ziel wählen.", Toast.LENGTH_SHORT).show()
            return
        }

        if (source == target) {
            Toast.makeText(this, "Quelle und Ziel dürfen nicht gleich sein.", Toast.LENGTH_SHORT).show()
            return
        }

        val affected = snippets.count { it.category == source }
        if (affected == 0) {
            Toast.makeText(this, "Keine Textbausteine zum Verschieben.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedSnippets = snippets.map { snippet ->
            if (snippet.category == source) {
                snippet.copy(category = target)
            } else {
                snippet
            }
        }

        store.saveAll(updatedSnippets)
        snippets = store.getAll()
        groups = store.getGroups()
        if (selectedGroup == source) {
            selectedGroup = target
        }
        renderSnippets()
        Toast.makeText(this, "$affected Textbausteine verschoben.", Toast.LENGTH_SHORT).show()
    }

    private fun deleteGroup(group: String) {
        if (group == "General") {
            Toast.makeText(this, "General kann nicht gelöscht werden.", Toast.LENGTH_SHORT).show()
            return
        }

        if (snippets.any { it.category == group }) {
            Toast.makeText(this, "Gruppe wird noch verwendet.", Toast.LENGTH_SHORT).show()
            return
        }

        groups = groups.filter { it != group }.toMutableList()
        store.saveGroups(groups)
        if (selectedGroup == group) {
            selectedGroup = null
        }
        renderSnippets()
        Toast.makeText(this, "Gruppe gelöscht.", Toast.LENGTH_SHORT).show()
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
            writeBackupToUri(uri)
        }.onSuccess {
            Toast.makeText(this, "Backup gespeichert.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Backup konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importBackup(uri: Uri) {
        runCatching {
            importBackupFromUri(uri, importReplaceExisting)
        }.onSuccess { count ->
            refreshAfterImport()
            Toast.makeText(this, "$count Textbausteine wiederhergestellt.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Backup konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSyncDialog() {
        val options = mutableListOf<String>()
        val hasFileSync = getSyncUri() != null
        val hasGitHubSync = loadGitHubConfig().isComplete()

        if (hasFileSync) {
            options.add("Datei: laden")
            options.add("Datei: sichern")
            options.add("Datei: andere wählen")
            options.add("Datei: neu erstellen")
            options.add("Datei: trennen")
        } else {
            options.add("Datei: neu erstellen")
            options.add("Datei: bestehende wählen")
        }

        options.add("GitHub: einrichten")
        if (hasGitHubSync) {
            options.add("GitHub: laden")
            options.add("GitHub: sichern")
            options.add("GitHub: trennen")
        }

        AlertDialog.Builder(this)
            .setTitle("Synchronisierung")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Datei: laden" -> confirmSyncLoad()
                    "Datei: sichern" -> syncSave()
                    "Datei: andere wählen", "Datei: bestehende wählen" -> startSyncFileOpen()
                    "Datei: neu erstellen" -> startSyncFileCreate()
                    "Datei: trennen" -> clearSyncFile()
                    "GitHub: einrichten" -> showGitHubSetupDialog()
                    "GitHub: laden" -> confirmGitHubLoad()
                    "GitHub: sichern" -> gitHubSave()
                    "GitHub: trennen" -> clearGitHubConfig()
                }
            }
            .show()
    }

    private fun startSyncFileCreate() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "copyboard-sync.json")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CREATE_SYNC_FILE)
    }

    private fun startSyncFileOpen() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_SYNC_FILE)
    }

    private fun rememberSyncFile(uri: Uri, grantFlags: Int) {
        runCatching {
            val flags = grantFlags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            if (flags != 0) {
                contentResolver.takePersistableUriPermission(uri, flags)
            }
        }
        syncPrefs().edit().putString(KEY_SYNC_URI, uri.toString()).apply()
        Toast.makeText(this, "Sync-Datei verknüpft.", Toast.LENGTH_SHORT).show()
    }

    private fun askLoadFromSyncFile() {
        AlertDialog.Builder(this)
            .setTitle("Sync-Datei verknüpft")
            .setMessage("Möchtest du jetzt die Daten aus dieser Datei laden? Deine aktuelle Liste wird dadurch ersetzt.")
            .setPositiveButton("Laden") { _, _ -> syncLoad() }
            .setNegativeButton("Nur merken", null)
            .show()
    }

    private fun confirmSyncLoad() {
        AlertDialog.Builder(this)
            .setTitle("Aus Sync-Datei laden")
            .setMessage("Die aktuelle Liste wird durch den Inhalt der Sync-Datei ersetzt.")
            .setPositiveButton("Laden") { _, _ -> syncLoad() }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun syncSave() {
        val uri = getSyncUri()
        if (uri == null) {
            Toast.makeText(this, "Noch keine Sync-Datei gewählt.", Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            writeBackupToUri(uri)
        }.onSuccess {
            Toast.makeText(this, "Sync-Datei aktualisiert.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Sync-Datei konnte nicht geschrieben werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun syncLoad() {
        val uri = getSyncUri()
        if (uri == null) {
            Toast.makeText(this, "Noch keine Sync-Datei gewählt.", Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            importBackupFromUri(uri, replaceExisting = true)
        }.onSuccess { count ->
            refreshAfterImport()
            Toast.makeText(this, "$count Textbausteine synchronisiert.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Sync-Datei konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearSyncFile() {
        syncPrefs().edit().remove(KEY_SYNC_URI).apply()
        Toast.makeText(this, "Sync-Verknüpfung entfernt.", Toast.LENGTH_SHORT).show()
    }

    private fun showGitHubSetupDialog() {
        val current = loadGitHubConfig()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }

        val ownerInput = syncInput("GitHub Owner", current.owner.ifBlank { "SinaSalvatrice" })
        val repoInput = syncInput("Repo", current.repo.ifBlank { "copyboard-sync" })
        val branchInput = syncInput("Branch", current.branch.ifBlank { "main" })
        val pathInput = syncInput("Dateipfad", current.path.ifBlank { "copyboard-sync.json" })
        val tokenInput = syncInput("Fine-grained Token", current.token, password = true)

        layout.addView(ownerInput)
        layout.addView(repoInput)
        layout.addView(branchInput)
        layout.addView(pathInput)
        layout.addView(tokenInput)

        AlertDialog.Builder(this)
            .setTitle("GitHub-Sync einrichten")
            .setMessage("Token lokal speichern. Empfohlen: Fine-grained Token nur für dieses Repo mit Contents read/write.")
            .setView(layout)
            .setPositiveButton("Speichern") { _, _ ->
                val config = GitHubSyncConfig(
                    owner = ownerInput.text.toString().trim(),
                    repo = repoInput.text.toString().trim(),
                    branch = branchInput.text.toString().trim().ifBlank { "main" },
                    path = pathInput.text.toString().trim().ifBlank { "copyboard-sync.json" },
                    token = tokenInput.text.toString().trim()
                )

                if (!config.isComplete()) {
                    Toast.makeText(this, "GitHub-Sync ist unvollständig.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                saveGitHubConfig(config)
                Toast.makeText(this, "GitHub-Sync gespeichert.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun syncInput(hint: String, value: String, password: Boolean = false): EditText {
        return EditText(this).apply {
            setHint(hint)
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setText(value)
            if (password) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    private fun confirmGitHubLoad() {
        AlertDialog.Builder(this)
            .setTitle("Von GitHub laden")
            .setMessage("Die aktuelle Liste wird durch die Datei aus GitHub ersetzt.")
            .setPositiveButton("Laden") { _, _ -> gitHubLoad() }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun gitHubLoad() {
        val config = loadGitHubConfig()
        if (!config.isComplete()) {
            Toast.makeText(this, "GitHub-Sync ist noch nicht eingerichtet.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Lade von GitHub …", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                val raw = GitHubSyncClient(config).load()
                store.importBackupJson(raw, replaceExisting = true)
            }
            runOnUiThread {
                result.onSuccess { count ->
                    refreshAfterImport()
                    Toast.makeText(this, "$count Textbausteine von GitHub geladen.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "GitHub-Sync fehlgeschlagen.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun gitHubSave() {
        val config = loadGitHubConfig()
        if (!config.isComplete()) {
            Toast.makeText(this, "GitHub-Sync ist noch nicht eingerichtet.", Toast.LENGTH_SHORT).show()
            return
        }

        runGitHubTask(
            loadingMessage = "Sichere nach GitHub …",
            successMessage = "GitHub-Sync aktualisiert."
        ) {
            GitHubSyncClient(config).save(store.exportBackupJson())
        }
    }

    private fun runGitHubTask(loadingMessage: String, successMessage: String, task: () -> Unit) {
        Toast.makeText(this, loadingMessage, Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { task() }
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "GitHub-Sync fehlgeschlagen.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun loadGitHubConfig(): GitHubSyncConfig {
        val prefs = syncPrefs()
        return GitHubSyncConfig(
            owner = prefs.getString(KEY_GITHUB_OWNER, "").orEmpty(),
            repo = prefs.getString(KEY_GITHUB_REPO, "").orEmpty(),
            branch = prefs.getString(KEY_GITHUB_BRANCH, "main").orEmpty(),
            path = prefs.getString(KEY_GITHUB_PATH, "copyboard-sync.json").orEmpty(),
            token = prefs.getString(KEY_GITHUB_TOKEN, "").orEmpty()
        )
    }

    private fun saveGitHubConfig(config: GitHubSyncConfig) {
        syncPrefs().edit()
            .putString(KEY_GITHUB_OWNER, config.owner)
            .putString(KEY_GITHUB_REPO, config.repo)
            .putString(KEY_GITHUB_BRANCH, config.branch)
            .putString(KEY_GITHUB_PATH, config.path)
            .putString(KEY_GITHUB_TOKEN, config.token)
            .apply()
    }

    private fun clearGitHubConfig() {
        syncPrefs().edit()
            .remove(KEY_GITHUB_OWNER)
            .remove(KEY_GITHUB_REPO)
            .remove(KEY_GITHUB_BRANCH)
            .remove(KEY_GITHUB_PATH)
            .remove(KEY_GITHUB_TOKEN)
            .apply()
        Toast.makeText(this, "GitHub-Sync entfernt.", Toast.LENGTH_SHORT).show()
    }

    private fun getSyncUri(): Uri? {
        val raw = syncPrefs().getString(KEY_SYNC_URI, null) ?: return null
        return Uri.parse(raw)
    }

    private fun syncPrefs() = getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)

    private fun writeBackupToUri(uri: Uri) {
        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(store.exportBackupJson())
        } ?: error("No output stream")
    }

    private fun importBackupFromUri(uri: Uri, replaceExisting: Boolean): Int {
        val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error("No input stream")
        return store.importBackupJson(raw, replaceExisting)
    }

    private fun refreshAfterImport() {
        snippets = store.getAll()
        groups = store.getGroups()
        selectedGroup = null
        refreshWidgets()
        renderSnippets()
    }

    private fun refreshWidgets() {
        CopyboardWidgetProvider.updateAll(this)
        FloatingNotesWidgetProvider.updateAll(this)
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
        clipboard.setPrimaryClip(ClipData.newPlainText(snippet.title, snippet.clipboardText()))
        Toast.makeText(this, "Kopiert: ${snippet.title}", Toast.LENGTH_SHORT).show()
    }

    private fun parseChecklistInput(raw: String): List<ChecklistItem> {
        return raw
            .lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) {
                    return@mapNotNull null
                }

                val done = trimmed.startsWith("[x]", ignoreCase = true)
                val text = trimmed.removePrefix("[x]").removePrefix("[X]").removePrefix("[ ]").trim()
                if (text.isBlank()) {
                    return@mapNotNull null
                }

                ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    done = done
                )
            }
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
        private const val REQUEST_CREATE_SYNC_FILE = 1003
        private const val REQUEST_OPEN_SYNC_FILE = 1004
        private const val SYNC_PREFS_NAME = "copyboard_sync"
        private const val KEY_SYNC_URI = "sync_file_uri"
        private const val KEY_GITHUB_OWNER = "github_owner"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_GITHUB_BRANCH = "github_branch"
        private const val KEY_GITHUB_PATH = "github_path"
        private const val KEY_GITHUB_TOKEN = "github_token"
    }
}
