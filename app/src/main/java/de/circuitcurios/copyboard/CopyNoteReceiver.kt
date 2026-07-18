package de.circuitcurios.copyboard

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class CopyNoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_NOTE) return

        val id = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val note = NoteStore(context).get(id) ?: return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(note.title, note.body))
        Toast.makeText(context, "Kopiert: ${note.title}", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_COPY_NOTE = "de.circuitcurios.copyboard.ACTION_COPY_NOTE"
        const val EXTRA_NOTE_ID = "note_id"
    }
}
