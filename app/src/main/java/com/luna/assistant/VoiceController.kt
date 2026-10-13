package com.luna.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class VoiceOption(val name: String, val label: String)

/** Speech-to-text (SpeechRecognizer) + text-to-speech (TextToSpeech) in one place. */
class VoiceController(
    context: Context,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onListeningStarted()
        fun onLevel(level: Float)
        /** Live transcript while the user is still talking. */
        fun onPartial(text: String) {}
        fun onHeard(text: String)
        fun onListenFailed(message: String)
        fun onSpeakingStarted()
        fun onSpeakingFinished()
        fun onVoiceNotice(message: String)
    }

    private val appContext = context.applicationContext
    private val prefs = LunaPrefs(appContext)
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Queue bookkeeping so "speaking finished" only fires after the LAST queued sentence.
    private val generation = AtomicInteger(0)
    private val utteranceCounter = AtomicInteger(0)
    private val pending = AtomicInteger(0)
    @Volatile private var holdOpen = false
    private var noticeShown = false

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (isCurrent(utteranceId)) main.post { callbacks.onSpeakingStarted() }
            }

            override fun onDone(utteranceId: String?) {
                utteranceEnded(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceEnded(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                utteranceEnded(utteranceId)
            }
        })
    }

    // ------------------------------------------------------------------ listening (STT)

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

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) callbacks.onPartial(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Stop soon after the person stops talking (not all recognizers obey these).
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
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

    // ------------------------------------------------------------------ speaking (TTS)

    private fun isCurrent(utteranceId: String?): Boolean {
        val gen = utteranceId?.split("-")?.getOrNull(1)?.toIntOrNull() ?: return false
        return gen == generation.get()
    }

    private fun utteranceEnded(utteranceId: String?) {
        if (!isCurrent(utteranceId)) return
        val left = pending.decrementAndGet()
        if (left <= 0) {
            pending.set(0)
            if (!holdOpen) main.post { callbacks.onSpeakingFinished() }
        }
    }

    /** Call before feeding streamed sentences so "finished" is not reported between sentences. */
    fun beginSpeech() {
        stopSpeaking()
        holdOpen = true
    }

    /** Call when no more sentences will be queued. */
    fun endSpeech() {
        holdOpen = false
        if (pending.get() <= 0) main.post { callbacks.onSpeakingFinished() }
    }

    /** Queue one chunk of text behind whatever is already being spoken. */
    fun speakQueued(text: String): Boolean {
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
        val lang = if (sinhala) "si" else "en"
        val locale = if (sinhala) Locale.forLanguageTag("si-LK") else Locale.US
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (!noticeShown) {
                noticeShown = true
                callbacks.onVoiceNotice(
                    if (sinhala) {
                        "Phone eke Sinhala voice nae. Settings > Text-to-speech eken Google engine ekata Sinhala voice data install karanna."
                    } else {
                        "English voice data nae. Settings > Text-to-speech eken install karanna."
                    }
                )
            }
            return false
        }
        val chosen = prefs.voiceName(lang)
        if (chosen.isNotBlank()) {
            try {
                val voice: Voice? = engine.voices?.firstOrNull { it.name == chosen }
                if (voice != null) engine.voice = voice
            } catch (e: Exception) {
                // keep the default voice
            }
        }
        engine.setSpeechRate(prefs.speechRate)
        engine.setPitch(prefs.speechPitch)

        val id = "luna-${generation.get()}-${utteranceCounter.incrementAndGet()}"
        pending.incrementAndGet()
        val rc = engine.speak(clean, TextToSpeech.QUEUE_ADD, null, id)
        if (rc != TextToSpeech.SUCCESS) {
            pending.decrementAndGet()
            return false
        }
        return true
    }

    /** Speak a whole reply (replaces anything currently being spoken). Returns true if started. */
    fun speak(text: String): Boolean {
        stopSpeaking()
        val started = speakQueued(text)
        if (!started) main.post { callbacks.onSpeakingFinished() }
        return started
    }

    fun stopSpeaking() {
        generation.incrementAndGet()
        pending.set(0)
        holdOpen = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Voices installed for [lang] ("si" or "en"), best quality first. */
    fun voiceOptions(lang: String): List<VoiceOption> {
        val engine = tts ?: return emptyList()
        if (!ttsReady) return emptyList()
        return try {
            (engine.voices ?: emptySet())
                .filter { it.locale.language == lang && !it.features.contains("notInstalled") }
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
                .map { v ->
                    val tag = v.locale.toLanguageTag()
                    val online = if (v.isNetworkConnectionRequired) " online" else ""
                    VoiceOption(v.name, "${v.name} ($tag$online)")
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Say a short sample using the saved voice / speed / pitch. */
    fun preview(lang: String) {
        speak(if (lang == "si") "හෙලෝ, මම ලූනා. මේ තමයි මගේ කටහඬ." else "Hello, I am Luna. This is my voice.")
    }

    fun shutdown() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        recognizer = null
        generation.incrementAndGet()
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

/**
 * Feeds a growing reply to the voice sentence by sentence, so Luna starts talking after the first
 * sentence while Gemini is still writing the rest.
 */
class StreamSpeaker(private val voice: VoiceController) {

    private var consumed = 0
    var spokeAnything = false
        private set

    /** [visible] is the whole safe-to-speak text so far (it only grows). */
    fun feed(visible: String) {
        if (visible.length <= consumed) return
        val pending = visible.substring(consumed)
        var cut = -1
        for (k in pending.indices.reversed()) {
            val c = pending[k]
            val endsSentence = when (c) {
                '\n', '।' -> true
                '.', '!', '?' -> k + 1 < pending.length && pending[k + 1].isWhitespace()
                else -> false
            }
            if (endsSentence) {
                cut = k
                break
            }
        }
        if (cut < 0 && pending.length > 90) {
            val sp = pending.lastIndexOf(' ')
            if (sp > 20) cut = sp
        }
        if (cut < 0) return
        val chunk = pending.substring(0, cut + 1)
        consumed += cut + 1
        if (voice.speakQueued(chunk)) spokeAnything = true
    }

    /** Speak whatever is left once the reply is complete. */
    fun finish(visible: String) {
        if (visible.length > consumed) {
            val rest = visible.substring(consumed)
            consumed = visible.length
            if (voice.speakQueued(rest)) spokeAnything = true
        }
    }
}
