package de.circuitcurios.copyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.EditText
import android.widget.Toast

class SpeechToTextController(
    private val activity: Activity,
    private val onListeningChanged: (Boolean) -> Unit = {}
) {
    private data class PendingStart(
        val target: EditText,
        val separator: String
    )

    private val preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var recognizer: SpeechRecognizer? = null
    private var target: EditText? = null
    private var baseText: String = ""
    private var separator: String = " "
    private var listening = false
    private var stoppingByUser = false
    private var pendingStart: PendingStart? = null

    val isListening: Boolean
        get() = listening

    fun currentLanguageIndex(): Int {
        val current = preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE).orEmpty()
        return LANGUAGE_OPTIONS.indexOfFirst { it.second == current }.let { if (it >= 0) it else 0 }
    }

    fun selectLanguage(index: Int) {
        val tag = LANGUAGE_OPTIONS.getOrNull(index)?.second ?: DEFAULT_LANGUAGE
        preferences.edit().putString(KEY_LANGUAGE, tag).apply()
    }

    fun toggle(target: EditText, separator: String = " ") {
        if (listening) {
            stop()
        } else {
            start(target, separator)
        }
    }

    fun start(target: EditText, separator: String = " ") {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, "Auf diesem Gerät ist keine Spracherkennung verfügbar.", Toast.LENGTH_SHORT).show()
            return
        }

        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = PendingStart(target, separator)
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        startInternal(target, separator)
    }

    fun stop() {
        if (!listening) return
        stoppingByUser = true
        recognizer?.stopListening()
        setListening(false)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != REQUEST_RECORD_AUDIO) return false

        val pending = pendingStart
        pendingStart = null

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pending?.let { startInternal(it.target, it.separator) }
        } else {
            Toast.makeText(activity, "Mikrofonzugriff ist für Diktieren nötig.", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        pendingStart = null
        target = null
        setListening(false)
    }

    private fun startInternal(target: EditText, separator: String) {
        this.target = target
        this.baseText = target.text?.toString().orEmpty()
        this.separator = separator
        stoppingByUser = false

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        val wasStoppedByUser = stoppingByUser
                        stoppingByUser = false
                        setListening(false)
                        if (!wasStoppedByUser && error != SpeechRecognizer.ERROR_NO_MATCH) {
                            Toast.makeText(activity, errorMessage(error), Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        bestResult(results)?.let(::applyTranscript)
                        stoppingByUser = false
                        setListening(false)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        bestResult(partialResults)?.let(::applyTranscript)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich jetzt")
        }

        try {
            recognizer?.startListening(intent)
            setListening(true)
        } catch (_: Exception) {
            setListening(false)
            Toast.makeText(activity, "Spracherkennung konnte nicht gestartet werden.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bestResult(results: Bundle?): String? {
        return results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun applyTranscript(transcript: String) {
        val editText = target ?: return
        val join = when {
            baseText.isBlank() -> ""
            baseText.lastOrNull()?.isWhitespace() == true -> ""
            else -> separator
        }
        val value = baseText + join + transcript
        editText.setText(value)
        editText.setSelection(value.length)
    }

    private fun selectedLanguageTag(): String {
        return preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    private fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        onListeningChanged(value)
    }

    private fun errorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Mikrofonfehler bei der Spracherkennung."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofonberechtigung fehlt."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Spracherkennung: Netzwerkfehler."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennung ist gerade beschäftigt."
            SpeechRecognizer.ERROR_SERVER -> "Spracherkennungsdienst ist gerade nicht verfügbar."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Sprache erkannt."
            else -> "Spracherkennung wurde beendet."
        }
    }

    companion object {
        const val REQUEST_RECORD_AUDIO = 2101
        val LANGUAGE_OPTIONS = listOf(
            "DE" to "de-DE",
            "RU" to "ru-RU",
            "EN" to "en-US"
        )

        private const val PREFS_NAME = "copyboard_speech"
        private const val KEY_LANGUAGE = "speech_language"
        private const val DEFAULT_LANGUAGE = "de-DE"
    }
}
