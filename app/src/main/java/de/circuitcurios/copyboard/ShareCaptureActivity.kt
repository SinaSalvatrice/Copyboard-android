package de.circuitcurios.copyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.util.UUID

class ShareCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        } else {
            ""
        }

        if (sharedText.isBlank()) {
            Toast.makeText(this, "Kein Text zum Speichern gefunden.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val store = SnippetStore(this)
        val firstLine = sharedText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = when {
            firstLine.isBlank() -> "Geteilter Text"
            firstLine.length > 56 -> firstLine.take(53) + "…"
            else -> firstLine
        }
        val snippet = Snippet(
            id = UUID.randomUUID().toString(),
            title = title,
            text = sharedText,
            category = store.getGroups().firstOrNull() ?: "General"
        )

        store.upsert(snippet)
        CopyboardWidgetProvider.updateAll(this)

        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID, snippet.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        Toast.makeText(this, "In Copyboard gespeichert.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
