package de.circuitcurios.copyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SnippetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): MutableList<Snippet> {
        val raw = prefs.getString(KEY_SNIPPETS, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultSnippets().toMutableList()
            saveAll(defaults)
            return defaults
        }

        return runCatching {
            parseSnippetArray(JSONArray(raw))
        }.getOrElse {
            val defaults = defaultSnippets().toMutableList()
            saveAll(defaults)
            defaults
        }
    }

    fun saveAll(snippets: List<Snippet>) {
        prefs.edit().putString(KEY_SNIPPETS, snippetsToJsonArray(snippets).toString()).apply()
        val categories = snippets.map { it.category.ifBlank { "General" } }
        saveGroups(mergeGroups(getGroups(), categories))
    }

    fun getGroups(): MutableList<String> {
        val raw = prefs.getString(KEY_GROUPS, null)
        val fromPrefs = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                val parsed = JSONArray(raw)
                MutableList(parsed.length()) { index ->
                    parsed.optString(index).trim()
                }
            }.getOrDefault(emptyList())
        }

        val categories = getAll().map { it.category.ifBlank { "General" } }
        val merged = mergeGroups(fromPrefs, categories)
        saveGroups(merged)
        return merged.toMutableList()
    }

    fun saveGroups(groups: List<String>) {
        val normalized = mergeGroups(groups, emptyList())
        val array = JSONArray()
        normalized.forEach { group -> array.put(group) }
        prefs.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    fun exportBackupJson(): String {
        return JSONObject()
            .put("version", 1)
            .put("app", "Copyboard")
            .put("snippets", snippetsToJsonArray(getAll()))
            .toString(2)
    }

    fun importBackupJson(raw: String, replaceExisting: Boolean): Int {
        val imported = parseBackup(raw)
        val finalList = if (replaceExisting) {
            imported
        } else {
            val existing = getAll()
            val existingIds = existing.map { it.id }.toMutableSet()
            val additions = imported.map { snippet ->
                if (existingIds.add(snippet.id)) {
                    snippet
                } else {
                    snippet.copy(id = UUID.randomUUID().toString())
                }
            }
            existing.apply { addAll(0, additions) }
        }

        saveAll(finalList)
        return imported.size
    }

    fun upsert(snippet: Snippet) {
        val list = getAll()
        val index = list.indexOfFirst { it.id == snippet.id }
        if (index >= 0) {
            list[index] = snippet
        } else {
            list.add(0, snippet)
        }
        saveAll(list)
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    private fun parseBackup(raw: String): MutableList<Snippet> {
        val trimmed = raw.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).getJSONArray("snippets")
        }
        return parseSnippetArray(array)
    }

    private fun parseSnippetArray(array: JSONArray): MutableList<Snippet> {
        return MutableList(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val mode = if (obj.optString("mode") == "checklist") "checklist" else "text"
            val checklistItems = parseChecklistArray(obj.optJSONArray("checklistItems"))
            Snippet(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", "Untitled"),
                text = obj.optString("text", ""),
                mode = mode,
                checklistItems = checklistItems,
                category = obj.optString("category", "General"),
                favorite = obj.optBoolean("favorite", false)
            )
        }
    }

    private fun snippetsToJsonArray(snippets: List<Snippet>): JSONArray {
        val array = JSONArray()
        snippets.forEach { snippet ->
            array.put(
                JSONObject()
                    .put("id", snippet.id)
                    .put("title", snippet.title)
                    .put("text", snippet.text)
                    .put("mode", snippet.mode)
                    .put("checklistItems", checklistToJsonArray(snippet.checklistItems))
                    .put("category", snippet.category)
                    .put("favorite", snippet.favorite)
            )
        }
        return array
    }

    private fun parseChecklistArray(array: JSONArray?): List<ChecklistItem> {
        if (array == null) {
            return emptyList()
        }

        val items = mutableListOf<ChecklistItem>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val text = obj.optString("text", "").trim()
            if (text.isBlank()) {
                continue
            }
            items += ChecklistItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                text = text,
                done = obj.optBoolean("done", false)
            )
        }

        return items
    }

    private fun checklistToJsonArray(items: List<ChecklistItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            val text = item.text.trim()
            if (text.isBlank()) {
                return@forEach
            }
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", text)
                    .put("done", item.done)
            )
        }
        return array
    }

    private fun mergeGroups(existing: List<String>, categories: List<String>): List<String> {
        val merged = (existing + categories)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (merged.none { it.equals("General", ignoreCase = true) }) {
            merged.add("General")
        }

        return merged.distinctBy { it.lowercase() }.sorted()
    }

    private fun defaultSnippets(): List<Snippet> = listOf(
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Etsy Tags - Aluminium Ring",
            text = "adjustable band, aluminum jewelry, handmade metal, industrial style, minimalist, unisex jewelry, nickel free, brushed aluminum, everyday piece, made to order",
            mode = "text",
            checklistItems = emptyList(),
            category = "Etsy",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Material DE",
            text = "Nickelfreier, sehr leichter Aluminiumschmuck, von Hand gebogen, flachgehämmert und poliert.",
            mode = "text",
            checklistItems = emptyList(),
            category = "Schmuck",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Material EN",
            text = "Nickel-free, lightweight aluminum jewelry, bent, flattened and polished by hand.",
            mode = "text",
            checklistItems = emptyList(),
            category = "Jewelry",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Kundenantwort EN",
            text = "Thank you so much for your message. I will check this and get back to you.",
            mode = "text",
            checklistItems = emptyList(),
            category = "Customer",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "AI Prompt - Schmuckfoto",
            text = "product photo of handmade aluminum jewelry, industrial neutral background, soft directional light, natural shadows, realistic surface texture, no redesign",
            mode = "text",
            checklistItems = emptyList(),
            category = "Prompts",
            favorite = false
        )
    )

    companion object {
        private const val PREFS_NAME = "copyboard_snippets"
        private const val KEY_SNIPPETS = "snippets_json"
        private const val KEY_GROUPS = "groups_json"
    }
}
