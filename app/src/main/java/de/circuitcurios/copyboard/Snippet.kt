package de.circuitcurios.copyboard

data class Snippet(
    val id: String,
    val title: String,
    val text: String,
    val category: String = "General",
    val favorite: Boolean = false
)
