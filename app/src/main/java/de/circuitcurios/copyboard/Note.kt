package de.circuitcurios.copyboard

import java.time.Instant

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: String = Instant.now().toString()
) {
    fun previewText(): String {
        return body
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Leere Notiz" }
    }

    fun searchText(): String {
        return "$title $body".lowercase()
    }
}
