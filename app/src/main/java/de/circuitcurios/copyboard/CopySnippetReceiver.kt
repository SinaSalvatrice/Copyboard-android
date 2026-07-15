package de.circuitcurios.copyboard

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class CopySnippetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CopyboardWidgetProvider.ACTION_COPY_SNIPPET) return

        val id = intent.getStringExtra(CopyboardWidgetProvider.EXTRA_SNIPPET_ID) ?: return
        val snippet = SnippetStore(context).getAll().firstOrNull { it.id == id } ?: return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(snippet.title, snippet.clipboardText()))
        Toast.makeText(context, "Kopiert: ${snippet.title}", Toast.LENGTH_SHORT).show()
    }
}
