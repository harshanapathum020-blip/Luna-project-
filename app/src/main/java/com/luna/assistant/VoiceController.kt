package com.luna.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speech-to-text (SpeechRecognizer) + text-to-speech (TextToSpeech) in one place. */
class VoiceController(
    context: Context,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onListeningStarted()
        fun onLevel(level: Float)
        fun onHeard(text: String)
        fun onListenFailed(message: String)
        fun onSpeakingStarted()
        fun onSpeakingFinished()
        fun onVoiceNotice(message: String)
    }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                callbacks.onSpeakingStarted()
            }

            override fun onDone(utteranceId: String?) {
                callbacks.onSpeakingFinished()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                callbacks.onSpeakingFinished()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                callbacks.onSpeakingFinished()
            }
        })
    }

    fun startListening(languageTag: String, preferOffline: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            callbacks.onListenFailed(
                "Me phone eke speech recognition nae. Google app eka install / update karanna."
            )
            return
        }
        stopSpeaking()
        try {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(appContext)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    callbacks.onListeningStarted()
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    callbacks.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    callbacks.onListenFailed(describe(error))
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isBlank()) {
                        callbacks.onListenFailed("Mama ahuwe nae. Aye mic eka tap karala katha karanna.")
                    } else {
                        callbacks.onHeard(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            }
            recognizer = r
            r.startListening(intent)
        } catch (e: Exception) {
            callbacks.onListenFailed("Mic eka start karanna bae giya: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    fun cancelListening() {
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Returns true when speech was started. */
    fun speak(text: String): Boolean {
        val engine = tts
        if (engine == null || !ttsReady) {
            callbacks.onVoiceNotice("Voice (text-to-speech) engine eka thama ready nae.")
            return false
        }
        val clean = text
            .replace(Regex("[*#_`>]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)
        if (clean.isEmpty()) return false

        val sinhala = clean.any { it in '඀'..'෿' }
        val locale = if (sinhala) Locale.forLanguageTag("si-LK") else Locale.US
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            callbacks.onVoiceNotice(
                if (sinhala) {
                    "Phone eke Sinhala voice nae. Settings > Text-to-speech eken Google engine ekata Sinhala voice data install karanna."
                } else {
                    "English voice data nae. Settings > Text-to-speech eken install karanna."
                }
            )
            return false
        }
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "luna-utterance")
        return true
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shutdown() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Mama ahuwe nae. Aye mic eka tap karala katha karanna."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Voice recognition ekata internet one. Connection eka balanna."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission denna one."
        SpeechRecognizer.ERROR_AUDIO ->
            "Microphone eka use karanna bae giya."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Voice recognizer eka busy. Tikak inna aye try karanna."
        12, 13 ->
            "Me bhashawa me phone eke voice recognition ekata support nae. Controls eken English select karanna."
        else -> "Voice error ($error). Aye try karanna."
    }
}
