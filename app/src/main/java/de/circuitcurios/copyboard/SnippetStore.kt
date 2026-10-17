package de.circuitcurios.copyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class SnippetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): MutableList<Snippet> = getAllForSync()
        .filterNot { it.deleted }
        .toMutableList()

    fun getAllForSync(): MutableList<Snippet> {
        val raw = prefs.getString(KEY_SNIPPETS, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultSnippets().toMutableList()
            writeAll(defaults)
            return defaults
        }

        return runCatching {
            parseSnippetArray(JSONArray(raw))
        }.getOrElse {
            val defaults = defaultSnippets().toMutableList()
            writeAll(defaults)
            defaults
        }
    }

    fun saveAll(snippets: List<Snippet>) {
        val stored = getAllForSync()
        val incomingIds = snippets.map { it.id }.toSet()
        val preservedTombstones = stored.filter { it.deleted && it.id !in incomingIds }
        val now = Instant.now().toString()
        val storedById = stored.associateBy { it.id }
        val normalized = snippets.map { snippet ->
            val current = storedById[snippet.id]
            if (current != null && !sameContent(current, snippet) && timestamp(snippet.updatedAt) <= timestamp(current.updatedAt)) {
                snippet.copy(updatedAt = now)
            } else {
                snippet
            }
        }
        val combined = normalized + preservedTombstones
        writeAll(combined)
        val categories = combined.filterNot { it.deleted }.map { it.category.ifBlank { "General" } }
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

    fun getFolders(): MutableList<String> {
        val raw = prefs.getString(KEY_FOLDERS, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index -> array.optString(index).trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .sorted()
                .toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun saveFolders(folders: List<String>) {
        val normalized = folders.map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }.sorted()
        prefs.edit().putString(KEY_FOLDERS, JSONArray(normalized).toString()).apply()
        val valid = normalized.toSet()
        saveGroupFolders(getGroupFolders().filterValues { it in valid })
    }

    fun getGroupFolders(): MutableMap<String, String> {
        val raw = prefs.getString(KEY_GROUP_FOLDERS, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().mapNotNull { group ->
                obj.optString(group).takeIf { it.isNotBlank() }?.let { group to it }
            }.toMap().toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun saveGroupFolders(assignments: Map<String, String>) {
        val validGroups = getGroups().toSet()
        val validFolders = getFolders().toSet()
        val obj = JSONObject()
        assignments.filter { it.key in validGroups && it.value in validFolders }
            .forEach { (group, folder) -> obj.put(group, folder) }
        prefs.edit().putString(KEY_GROUP_FOLDERS, obj.toString()).apply()
    }

    fun exportBackupJson(): String {
        return JSONObject()
            .put("version", SYNC_SCHEMA_VERSION)
            .put("app", "Copyboard")
            .put("updatedAt", Instant.now().toString())
            .put("snippets", snippetsToJsonArray(getAllForSync()))
            .put("groups", JSONArray(getGroups()))
            .put("folders", JSONArray(getFolders()))
            .put("groupFolders", JSONObject(getGroupFolders()))
            .toString(2)
    }

    fun importBackupJson(raw: String, replaceExisting: Boolean): Int {
        val backup = parseBackup(raw)
        val shouldMerge = backup.syncMerge || !replaceExisting
        val finalList = if (shouldMerge) {
            mergeSnippets(getAllForSync(), backup.snippets)
        } else {
            backup.snippets
        }

        writeAll(finalList)

        val activeCategories = finalList.filterNot { it.deleted }.map { it.category.ifBlank { "General" } }
        val groupsToSave = if (shouldMerge) {
            mergeGroups(getGroups(), backup.groups + activeCategories)
        } else {
            mergeGroups(backup.groups, activeCategories)
        }
        saveGroups(groupsToSave)

        val foldersToSave = if (shouldMerge) {
            mergeFolders(getFolders(), backup.folders)
        } else {
            mergeFolders(emptyList(), backup.folders)
        }
        saveFolders(foldersToSave)

        val assignments = if (shouldMerge) {
            getGroupFolders().apply { putAll(backup.groupFolders) }
        } else {
            backup.groupFolders.toMutableMap()
        }
        saveGroupFolders(assignments)

        return backup.snippets.count { !it.deleted }
    }

    fun upsert(snippet: Snippet) {
        val list = getAllForSync()
        val now = Instant.now().toString()
        val clean = snippet.copy(updatedAt = now, deleted = false)
        val index = list.indexOfFirst { it.id == clean.id }
        if (index >= 0) {
            list[index] = clean
        } else {
            list.add(0, clean)
        }
        saveAll(list)
    }

    fun delete(id: String) {
        val list = getAllForSync()
        val now = Instant.now().toString()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        list[index] = list[index].copy(deleted = true, updatedAt = now)
        writeAll(list)
    }

    private fun writeAll(snippets: List<Snippet>) {
        prefs.edit().putString(KEY_SNIPPETS, snippetsToJsonArray(snippets).toString()).apply()
    }

    private fun parseBackup(raw: String): BackupData {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return BackupData(
                snippets = parseSnippetArray(JSONArray(trimmed)),
                groups = emptyList(),
                folders = emptyList(),
                groupFolders = emptyMap(),
                syncMerge = false
            )
        }

        val obj = JSONObject(trimmed)
        val groupArray = obj.optJSONArray("groups")
        val folderArray = obj.optJSONArray("folders")
        val assignments = obj.optJSONObject("groupFolders")

        return BackupData(
            snippets = parseSnippetArray(obj.getJSONArray("snippets")),
            groups = groupArray?.let { array -> List(array.length()) { array.optString(it) } } ?: emptyList(),
            folders = folderArray?.let { array -> List(array.length()) { array.optString(it) } } ?: emptyList(),
            groupFolders = assignments?.let { assignmentObj ->
                assignmentObj.keys().asSequence().associateWith { key -> assignmentObj.optString(key) }
            } ?: emptyMap(),
            syncMerge = obj.optBoolean(SYNC_MERGE_MARKER, false)
        )
    }

    private fun mergeSnippets(local: List<Snippet>, incoming: List<Snippet>): MutableList<Snippet> {
        val merged = linkedMapOf<String, Snippet>()
        local.forEach { merged[it.id] = it }
        incoming.forEach { candidate ->
            val current = merged[candidate.id]
            if (current == null || timestamp(candidate.updatedAt) >= timestamp(current.updatedAt)) {
                merged[candidate.id] = candidate
            }
        }
        return merged.values
            .sortedByDescending { timestamp(it.updatedAt) }
            .toMutableList()
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
                favorite = obj.optBoolean("favorite", false),
                updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() } ?: Instant.EPOCH.toString(),
                deleted = obj.optBoolean("deleted", false)
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
                    .put("updatedAt", snippet.updatedAt)
                    .put("deleted", snippet.deleted)
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

    private fun mergeFolders(existing: List<String>, incoming: List<String>): List<String> =
        (existing + incoming)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()

    private fun timestamp(value: String): Long = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)

    private fun sameContent(left: Snippet, right: Snippet): Boolean =
        left.id == right.id &&
            left.title == right.title &&
            left.text == right.text &&
            left.mode == right.mode &&
            left.checklistItems == right.checklistItems &&
            left.category == right.category &&
            left.favorite == right.favorite &&
            left.deleted == right.deleted

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

    private data class BackupData(
        val snippets: MutableList<Snippet>,
        val groups: List<String>,
        val folders: List<String>,
        val groupFolders: Map<String, String>,
        val syncMerge: Boolean
    )

    companion object {
        private const val PREFS_NAME = "copyboard_snippets"
        private const val KEY_SNIPPETS = "snippets_json"
        private const val KEY_GROUPS = "groups_json"
        private const val KEY_FOLDERS = "folders_json"
        private const val KEY_GROUP_FOLDERS = "group_folders_json"
        private const val SYNC_SCHEMA_VERSION = 2
        const val SYNC_MERGE_MARKER = "_syncMerge"
    }
}
