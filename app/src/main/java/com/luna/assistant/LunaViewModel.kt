package com.luna.assistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

class LunaViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = LunaPrefs(app)
    private val engine = LunaEngine(app, prefs)
    private val main = Handler(Looper.getMainLooper())

    val messages = mutableStateListOf<ChatMessage>()

    var orbState by mutableStateOf(OrbState.IDLE)
        private set

    var level by mutableFloatStateOf(0f)
        private set

    /** What the microphone has understood so far, shown live while the person is still talking. */
    var liveText by mutableStateOf("")
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    var language by mutableStateOf(prefs.language)
        private set

    var speakReplies by mutableStateOf(prefs.speakReplies)
        private set

    var offlineMode by mutableStateOf(prefs.offlineMode)
        private set

    var wakeEnabled by mutableStateOf(prefs.wakeEnabled)
        private set

    var conversationMode by mutableStateOf(prefs.conversationMode)
        private set

    var speechRate by mutableFloatStateOf(prefs.speechRate)
        private set

    var speechPitch by mutableFloatStateOf(prefs.speechPitch)
        private set

    var voiceSi by mutableStateOf(prefs.voiceName("si"))
        private set

    var voiceEn by mutableStateOf(prefs.voiceName("en"))
        private set

    // Editable settings (saved when the user taps Save in Controls)
    var apiKey by mutableStateOf(prefs.apiKey)
    var model by mutableStateOf(prefs.model)

    /** True when the current listening round was started automatically (conversation mode). */
    private var autoListen = false

    private val voice = VoiceController(app, object : VoiceController.Callbacks {
        override fun onListeningStarted() {
            orbState = OrbState.LISTENING
        }

        override fun onLevel(level: Float) {
            this@LunaViewModel.level = level
        }

        override fun onPartial(text: String) {
            liveText = text
        }

        override fun onHeard(text: String) {
            level = 0f
            liveText = ""
            autoListen = false
            sendText(text)
        }

        override fun onListenFailed(message: String) {
            level = 0f
            liveText = ""
            orbState = OrbState.IDLE
            if (autoListen) {
                // Nobody answered Luna's follow-up listen: just end the conversation quietly.
                autoListen = false
            } else {
                notice = message
            }
        }

        override fun onSpeakingStarted() {
            orbState = OrbState.SPEAKING
        }

        override fun onSpeakingFinished() {
            if (orbState == OrbState.SPEAKING) {
                orbState = OrbState.IDLE
                if (prefs.conversationMode && AppState.foreground) startListening(auto = true)
            }
        }

        override fun onVoiceNotice(message: String) {
            notice = message
        }
    })

    init {
        messages.addAll(prefs.loadHistory())
    }

    fun onMicPressed() {
        when (orbState) {
            OrbState.IDLE -> startListening()
            OrbState.LISTENING -> {
                autoListen = false
                voice.cancelListening()
                level = 0f
                liveText = ""
                orbState = OrbState.IDLE
            }
            OrbState.SPEAKING -> {
                voice.stopSpeaking()
                orbState = OrbState.IDLE
            }
            OrbState.THINKING -> Unit
        }
    }

    fun onWakeWord() {
        if (orbState == OrbState.IDLE) startListening()
    }

    private fun startListening(auto: Boolean = false) {
        if (!auto) notice = null
        autoListen = auto
        liveText = ""
        orbState = OrbState.LISTENING
        LunaWakeService.pause() // the wake word listener must let go of the microphone first
        voice.startListening(if (language == "si") "si-LK" else "en-US", prefs.offlineMode)
    }

    fun sendText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || orbState == OrbState.THINKING) return
        voice.stopSpeaking()
        notice = null
        liveText = ""
        val context = messages.filter { !it.isError }.takeLast(10)
        messages.add(ChatMessage(fromUser = true, text = text))
        orbState = OrbState.THINKING

        val speak = speakReplies
        val speaker = StreamSpeaker(voice)
        if (speak) voice.beginSpeech()
        val lastVisible = AtomicReference("")
        var botIndex = -1

        viewModelScope.launch {
            val reply = engine.process(text, context) { visible ->
                lastVisible.set(visible)
                // Show the reply growing in the chat while Gemini is still writing it.
                main.post {
                    if (visible.isBlank()) return@post
                    if (botIndex < 0) {
                        messages.add(ChatMessage(fromUser = false, text = visible))
                        botIndex = messages.size - 1
                    } else if (botIndex < messages.size) {
                        messages[botIndex] = messages[botIndex].copy(text = visible)
                    }
                }
                if (speak) speaker.feed(visible)
            }

            val finalMessage = ChatMessage(fromUser = false, text = reply.text, isError = reply.isError)
            if (botIndex in 0 until messages.size) {
                messages[botIndex] = finalMessage
            } else {
                messages.add(finalMessage)
            }
            prefs.saveHistory(messages)

            when {
                reply.isError || !speak -> {
                    if (speak) voice.stopSpeaking()
                    orbState = OrbState.IDLE
                }
                reply.streamed -> {
                    speaker.finish(lastVisible.get())
                    orbState = if (speaker.spokeAnything) OrbState.SPEAKING else OrbState.IDLE
                    voice.endSpeech()
                }
                else -> {
                    val started = voice.speak(reply.text)
                    orbState = if (started) OrbState.SPEAKING else OrbState.IDLE
                }
            }
        }
    }

    @JvmName("applyLanguage")
    fun setLanguage(value: String) {
        language = value
        prefs.language = value
    }

    @JvmName("applySpeakReplies")
    fun setSpeakReplies(value: Boolean) {
        speakReplies = value
        prefs.speakReplies = value
        if (!value) voice.stopSpeaking()
    }

    @JvmName("applyOfflineMode")
    fun setOfflineMode(value: Boolean) {
        offlineMode = value
        prefs.offlineMode = value
    }

    @JvmName("applyConversationMode")
    fun setConversationMode(value: Boolean) {
        conversationMode = value
        prefs.conversationMode = value
    }

    @JvmName("applyWakeEnabled")
    fun setWakeEnabled(value: Boolean) {
        wakeEnabled = value
        prefs.wakeEnabled = value
        val app = getApplication<Application>()
        if (value) LunaWakeService.start(app) else LunaWakeService.stop(app)
    }

    // ---- voice picker (Controls > VOICE)

    fun voiceOptions(lang: String): List<VoiceOption> = voice.voiceOptions(lang)

    fun selectVoice(lang: String, name: String) {
        prefs.setVoiceName(lang, name)
        if (lang == "si") voiceSi = name else voiceEn = name
    }

    fun updateSpeechRate(value: Float) {
        speechRate = value
        prefs.speechRate = value
    }

    fun updateSpeechPitch(value: Float) {
        speechPitch = value
        prefs.speechPitch = value
    }

    fun previewVoice(lang: String) {
        voice.preview(lang)
    }

    /** The wake service can add messages while the app is closed. */
    fun reloadHistory() {
        if (orbState != OrbState.IDLE) return
        messages.clear()
        messages.addAll(prefs.loadHistory())
    }

    fun saveSettings() {
        prefs.apiKey = apiKey
        prefs.model = model
        apiKey = prefs.apiKey
        model = prefs.model
    }

    fun clearHistory() {
        messages.clear()
        prefs.saveHistory(emptyList())
    }

    fun showNotice(message: String) {
        notice = message
    }

    fun dismissNotice() {
        notice = null
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }
}
