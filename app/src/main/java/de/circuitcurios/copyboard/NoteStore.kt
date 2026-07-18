package de.circuitcurios.copyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): MutableList<Note> {
        val raw = prefs.getString(KEY_NOTES, null)
        if (raw.isNullOrBlank()) {
            return mutableListOf()
        }

        return runCatching {
            parseNotes(JSONArray(raw))
        }.getOrElse {
            mutableListOf()
        }
    }

    fun get(id: String): Note? = getAll().firstOrNull { it.id == id }

    fun saveAll(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("updatedAt", note.updatedAt)
            )
        }
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
    }

    fun upsert(note: Note): Note {
        val now = Instant.now().toString()
        val clean = note.copy(
            title = note.title.trim().ifBlank { "Notiz" },
            body = note.body,
            updatedAt = now
        )
        val notes = getAll()
        val index = notes.indexOfFirst { it.id == clean.id }
        if (index >= 0) {
            notes[index] = clean
        } else {
            notes.add(0, clean)
        }
        saveAll(notes.sortedByDescending { it.updatedAt })
        return clean
    }

    fun create(title: String = "Neue Notiz", body: String = ""): Note {
        return upsert(
            Note(
                id = UUID.randomUUID().toString(),
                title = title,
                body = body,
                updatedAt = Instant.now().toString()
            )
        )
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    private fun parseNotes(array: JSONArray): MutableList<Note> {
        return MutableList(array.length()) { index ->
            val obj = array.getJSONObject(index)
            Note(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", "Notiz"),
                body = obj.optString("body", ""),
                updatedAt = obj.optString("updatedAt", Instant.now().toString())
            )
        }.sortedByDescending { it.updatedAt }.toMutableList()
    }

    companion object {
        private const val PREFS_NAME = "copyboard_notes"
        private const val KEY_NOTES = "notes_json"
    }
}
