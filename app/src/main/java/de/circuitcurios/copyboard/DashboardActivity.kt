package de.circuitcurios.copyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DashboardActivity : Activity() {
    private lateinit var store: SnippetStore
    private lateinit var colors: AppColors
    private lateinit var searchInput: EditText
    private lateinit var filterContainer: LinearLayout
    private lateinit var snippetsContainer: LinearLayout
    private lateinit var countText: TextView

    private var snippets: List<Snippet> = emptyList()
    private var groups: List<String> = emptyList()
    private var selectedFilter: String = FILTER_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = resolveAppColors(this)
        store = SnippetStore(this)
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        snippets = store.getAll()
        groups = store.getGroups()
        if (selectedFilter != FILTER_ALL && selectedFilter != FILTER_FAVORITES && selectedFilter !in groups) {
            selectedFilter = FILTER_ALL
        }
        renderFilters()
        renderSnippets()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
            setBackgroundColor(colors.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headingBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            text = "Copyboard"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }
        countText = TextView(this).apply {
            textSize = 12f
            setTextColor(colors.textSecondary)
        }
        headingBlock.addView(title)
        headingBlock.addView(countText)

        val quickAdd = Button(this).apply {
            text = "+"
            textSize = 22f
            contentDescription = "Quick Add"
            setTextColor(colors.accent)
            setOnClickListener {
                startActivity(Intent(this@DashboardActivity, QuickCaptureActivity::class.java))
            }
        }

        val advanced = Button(this).apply {
            text = "⋯"
            textSize = 22f
            contentDescription = "Erweiterte Copyboard-Ansicht"
            setTextColor(colors.textSecondary)
            setOnClickListener {
                startActivity(Intent(this@DashboardActivity, MainActivity::class.java))
            }
        }

        header.addView(headingBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(quickAdd, LinearLayout.LayoutParams(dp(52), dp(48)))
        header.addView(advanced, LinearLayout.LayoutParams(dp(52), dp(48)))

        searchInput = EditText(this).apply {
            hint = "Suchen"
            setHintTextColor(colors.textSecondary)
            setTextColor(colors.textPrimary)
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(colors.inputSurface, colors.border, dp(10).toFloat())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSnippets()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        filterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterContainer)
        }

        snippetsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val listScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(snippetsContainer)
        }

        root.addView(header)
        root.addView(searchInput, LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
        root.addView(filterScroll, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(listScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun renderFilters() {
        filterContainer.removeAllViews()
        addFilter("Alle", FILTER_ALL)
        addFilter("★ Favoriten", FILTER_FAVORITES)
        groups.forEach { group -> addFilter(group, group) }
    }

    private fun addFilter(label: String, value: String) {
        val selected = selectedFilter == value
        val chip = TextView(this).apply {
            text = label
            textSize = 13f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (selected) Color.WHITE else colors.textSecondary)
            setPadding(dp(13), dp(7), dp(13), dp(7))
            background = rounded(
                if (selected) colors.accent else colors.surface,
                if (selected) colors.accent else colors.border,
                dp(18).toFloat()
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedFilter = value
                renderFilters()
                renderSnippets()
            }
        }
        filterContainer.addView(chip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dp(7), 0) })
    }

    private fun renderSnippets() {
        if (!::snippetsContainer.isInitialized || !::searchInput.isInitialized) return
        snippetsContainer.removeAllViews()
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()

        val filtered = snippets
            .asSequence()
            .filter { snippet ->
                when (selectedFilter) {
                    FILTER_ALL -> true
                    FILTER_FAVORITES -> snippet.favorite
                    else -> snippet.category == selectedFilter
                }
            }
            .filter { snippet -> query.isBlank() || snippet.searchText().contains(query) }
            .sortedWith(
                compareByDescending<Snippet> { it.favorite }
                    .thenByDescending { timestamp(it.updatedAt) }
                    .thenBy { it.title.lowercase() }
            )
            .toList()

        countText.text = "${filtered.size} von ${snippets.size}"

        if (filtered.isEmpty()) {
            snippetsContainer.addView(TextView(this).apply {
                text = "Keine passenden Snippets."
                textSize = 15f
                setTextColor(colors.textSecondary)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(44), dp(12), dp(12))
            })
            return
        }

        filtered.forEach { snippet -> snippetsContainer.addView(snippetCard(snippet)) }
    }

    private fun snippetCard(snippet: Snippet): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(colors.surface, colors.border, dp(10).toFloat())
            isClickable = true
            isFocusable = true
            setOnClickListener { copySnippet(snippet) }
            setOnLongClickListener {
                startActivity(Intent(this@DashboardActivity, MainActivity::class.java).apply {
                    putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, snippet.id)
                })
                true
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = snippet.title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textPrimary)
            maxLines = 1
        }
        val meta = TextView(this).apply {
            val mode = if (snippet.mode == "checklist") " · Liste" else ""
            text = (if (snippet.favorite) "★ " else "") + snippet.category + mode
            textSize = 11f
            setTextColor(colors.accent)
            gravity = Gravity.END
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(meta)

        val preview = TextView(this).apply {
            text = snippet.previewText()
            textSize = 13f
            setTextColor(colors.textSecondary)
            maxLines = 2
            setPadding(0, dp(6), 0, 0)
        }

        card.addView(header)
        card.addView(preview)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(7)) }
        return card
    }

    private fun copySnippet(snippet: Snippet) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(snippet.title, snippet.clipboardText()))
        Toast.makeText(this, "${snippet.title} kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun timestamp(value: String): Long = runCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILTER_ALL = "__all__"
        private const val FILTER_FAVORITES = "__favorites__"
    }
}
