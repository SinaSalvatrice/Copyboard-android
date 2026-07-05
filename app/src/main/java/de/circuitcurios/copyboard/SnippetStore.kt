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
            Snippet(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", "Untitled"),
                text = obj.optString("text", ""),
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
                    .put("category", snippet.category)
                    .put("favorite", snippet.favorite)
            )
        }
        return array
    }

    private fun defaultSnippets(): List<Snippet> = listOf(
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Etsy Tags - Aluminium Ring",
            text = "adjustable band, aluminum jewelry, handmade metal, industrial style, minimalist, unisex jewelry, nickel free, brushed aluminum, everyday piece, made to order",
            category = "Etsy",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Material DE",
            text = "Nickelfreier, sehr leichter Aluminiumschmuck, von Hand gebogen, flachgehämmert und poliert.",
            category = "Schmuck",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Material EN",
            text = "Nickel-free, lightweight aluminum jewelry, bent, flattened and polished by hand.",
            category = "Jewelry",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "Kundenantwort EN",
            text = "Thank you so much for your message. I will check this and get back to you.",
            category = "Customer",
            favorite = true
        ),
        Snippet(
            id = UUID.randomUUID().toString(),
            title = "AI Prompt - Schmuckfoto",
            text = "product photo of handmade aluminum jewelry, industrial neutral background, soft directional light, natural shadows, realistic surface texture, no redesign",
            category = "Prompts",
            favorite = false
        )
    )

    companion object {
        private const val PREFS_NAME = "copyboard_snippets"
        private const val KEY_SNIPPETS = "snippets_json"
    }
}
