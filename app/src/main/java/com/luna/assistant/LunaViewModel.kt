package com.luna.assistant

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

class LunaViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = LunaPrefs(app)
    private val engine = LunaEngine(app, prefs)

    val messages = mutableStateListOf<ChatMessage>()

    var orbState by mutableStateOf(OrbState.IDLE)
        private set

    var level by mutableFloatStateOf(0f)
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

    // Editable settings (saved when the user taps Save in Controls)
    var apiKey by mutableStateOf(prefs.apiKey)
    var model by mutableStateOf(prefs.model)

    private val voice = VoiceController(app, object : VoiceController.Callbacks {
        override fun onListeningStarted() {
            orbState = OrbState.LISTENING
        }

        override fun onLevel(level: Float) {
            this@LunaViewModel.level = level
        }

        override fun onHeard(text: String) {
            level = 0f
            sendText(text)
        }

        override fun onListenFailed(message: String) {
            level = 0f
            orbState = OrbState.IDLE
            notice = message
        }

        override fun onSpeakingStarted() {
            orbState = OrbState.SPEAKING
        }

        override fun onSpeakingFinished() {
            if (orbState == OrbState.SPEAKING) orbState = OrbState.IDLE
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
                voice.cancelListening()
                level = 0f
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

    private fun startListening() {
        notice = null
        orbState = OrbState.LISTENING
        voice.startListening(if (language == "si") "si-LK" else "en-US", prefs.offlineMode)
    }

    fun sendText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || orbState == OrbState.THINKING) return
        voice.stopSpeaking()
        notice = null
        val context = messages.filter { !it.isError }.takeLast(10)
        messages.add(ChatMessage(fromUser = true, text = text))
        orbState = OrbState.THINKING
        viewModelScope.launch {
            val reply = engine.process(text, context)
            messages.add(ChatMessage(fromUser = false, text = reply.text, isError = reply.isError))
            prefs.saveHistory(messages)
            if (!reply.isError && speakReplies) {
                val started = voice.speak(reply.text)
                orbState = if (started) OrbState.SPEAKING else OrbState.IDLE
            } else {
                orbState = OrbState.IDLE
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

    @JvmName("applyWakeEnabled")
    fun setWakeEnabled(value: Boolean) {
        wakeEnabled = value
        prefs.wakeEnabled = value
        val app = getApplication<Application>()
        if (value) LunaWakeService.start(app) else LunaWakeService.stop(app)
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
