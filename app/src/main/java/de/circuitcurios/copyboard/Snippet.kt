package de.circuitcurios.copyboard

data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean = false
)

data class Snippet(
    val id: String,
    val title: String,
    val text: String,
    val mode: String = "text",
    val checklistItems: List<ChecklistItem> = emptyList(),
    val category: String = "General",
    val favorite: Boolean = false
) {
    fun clipboardText(): String {
        if (mode == "checklist") {
            return checklistItems
                .mapNotNull { item -> item.text.trim().takeIf { it.isNotBlank() }?.let { "- [${if (item.done) "x" else " "}] $it" } }
                .joinToString("\n")
        }
        return text
    }

    fun previewText(): String {
        if (mode == "checklist") {
            val valid = checklistItems.filter { it.text.trim().isNotBlank() }
            if (valid.isEmpty()) {
                return "Leere Checkliste"
            }
            val doneCount = valid.count { it.done }
            val head = valid.take(2).joinToString(" • ") { item ->
                "${if (item.done) "x" else " "} ${item.text.trim()}"
            }
            val suffix = if (valid.size > 2) " +${valid.size - 2}" else ""
            return "$doneCount/${valid.size} erledigt • $head$suffix"
        }

        return text
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Leerer Textbaustein" }
    }

    fun searchText(): String {
        val checklist = checklistItems.joinToString(" ") { it.text }
        return "$title $category $text $checklist".lowercase()
    }
}
