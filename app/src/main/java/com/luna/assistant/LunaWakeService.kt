package com.luna.assistant

import ai.picovoice.porcupine.PorcupineManager
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Always-on foreground service. Listens for the wake word with Picovoice Porcupine. When the app
 * screen is closed it runs the whole conversation itself: listen -> think/act -> speak.
 * When the app is open it just tells the screen to start listening.
 */
class LunaWakeService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var prefs: LunaPrefs
    private lateinit var engine: LunaEngine
    private var voice: VoiceController? = null
    private var porcupine: PorcupineManager? = null

    private var busy = false
    private var speaking = false
    private var paused = false

    /** Safety net: if a voice round never ends (recognizer stuck), wake listening is restored. */
    private val watchdog = Runnable {
        if (busy) finishSession()
    }

    companion object {
        const val CHANNEL_ID = "LunaWakeChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_WAKE_DETECTED = "com.luna.assistant.WAKE_DETECTED"
        private const val FALLBACK_KEYWORD = "wake_up_baby_android.ppn"
        private const val WATCHDOG_MS = 90_000L

        @Volatile
        private var instance: LunaWakeService? = null

        /** The app screen is using the microphone: stop wake listening so the two do not collide. */
        fun pause() {
            instance?.pauseWake()
        }

        /** The app screen is idle again: listen for the wake word. */
        fun resume() {
            instance?.resumeWake()
        }

        fun start(context: Context) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
            try {
                val intent = Intent(context, LunaWakeService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppState.wakeStatus = "Could not start: ${e.localizedMessage}"
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LunaWakeService::class.java))
            AppState.wakeStatus = "Off"
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = LunaPrefs(this)
        engine = LunaEngine(this, prefs)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting…"))
        voice = VoiceController(this, voiceCallbacks)
        initWakeEngine()
    }

    /** First usable .ppn in assets (real keyword files are far bigger than the 12-byte placeholder). */
    private fun pickKeyword(): String? {
        val names = mutableListOf(Config.KEYWORD_ASSET_PATH, FALLBACK_KEYWORD)
        try {
            assets.list("")?.filter { it.endsWith(".ppn") }?.forEach { if (it !in names) names.add(it) }
        } catch (e: Exception) {
            // ignore
        }
        for (name in names) {
            try {
                val size = assets.open(name).use { it.readBytes().size }
                if (size > 1000) return name
            } catch (e: Exception) {
                // try next
            }
        }
        return null
    }

    private fun initWakeEngine() {
        val key = Config.PICOVOICE_ACCESS_KEY
        if (key.isBlank() || key.startsWith("YOUR_")) {
            setStatus("Picovoice key missing (build ekata PICOVOICE_ACCESS_KEY ekak denna one)")
            return
        }
        val keyword = pickKeyword()
        if (keyword == null) {
            setStatus("Wake word file (.ppn) missing: assets/${Config.KEYWORD_ASSET_PATH}")
            return
        }
        try {
            porcupine = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeywordPath(keyword)
                .setSensitivity(0.7f)
                .build(applicationContext) { keywordIndex ->
                    if (keywordIndex == 0) main.post { onWake() }
                }
            porcupine?.start()
            paused = false
            setStatus("Listening for '${Config.WAKE_WORD_TRIGGER}'")
        } catch (e: Throwable) {
            val m = e.localizedMessage.orEmpty()
            setStatus(
                when {
                    m.contains("AccessKey", ignoreCase = true) && m.contains("invalid", ignoreCase = true) ->
                        "Picovoice key eka waradi"
                    m.contains("limit", ignoreCase = true) ->
                        "Picovoice device limit iwara (Console eke wena device ekak ain karanna)"
                    m.contains("version", ignoreCase = true) ->
                        "Wake word file eka Porcupine version ekata match nae. Console eken aye download karanna"
                    m.contains("network", ignoreCase = true) || m.contains("connect", ignoreCase = true) ->
                        "Picovoice activation ekata internet one (eka parak witharai)"
                    else -> "Wake engine error: ${m.take(120)}"
                }
            )
        }
    }

    fun pauseWake() {
        if (busy || porcupine == null) return
        try {
            porcupine?.stop()
        } catch (e: Exception) {
            // ignore
        }
        paused = true
    }

    fun resumeWake() {
        if (busy || !paused || porcupine == null) return
        try {
            porcupine?.start()
            paused = false
            setStatus("Listening for '${Config.WAKE_WORD_TRIGGER}'")
        } catch (e: Exception) {
            setStatus("Wake engine error: ${e.localizedMessage}")
        }
    }

    private fun onWake() {
        if (AppState.foreground) {
            // The app screen handles the conversation; free the microphone for it.
            pauseWake()
            sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
            return
        }
        if (busy) return
        busy = true
        try {
            porcupine?.stop()
        } catch (e: Exception) {
            // ignore
        }
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
        setStatus("Luna is listening…")
        voice?.startListening(if (prefs.language == "si") "si-LK" else "en-US", prefs.offlineMode)
    }

    private fun handleHeard(text: String) {
        setStatus("Thinking…")
        val v = voice ?: run {
            finishSession()
            return
        }
        scope.launch {
            val history = prefs.loadHistory().filter { !it.isError }.takeLast(10)
            val speak = prefs.speakReplies
            val speaker = StreamSpeaker(v)
            val last = AtomicReference("")
            if (speak) v.beginSpeech()

            val reply = engine.process(text, history) { visible ->
                last.set(visible)
                if (speak) speaker.feed(visible)
            }

            val all = prefs.loadHistory().toMutableList()
            all.add(ChatMessage(fromUser = true, text = text))
            all.add(ChatMessage(fromUser = false, text = reply.text, isError = reply.isError))
            prefs.saveHistory(all)

            when {
                reply.isError || reply.text.isBlank() || !speak -> {
                    if (speak) v.stopSpeaking()
                    finishSession()
                }
                reply.streamed -> {
                    speaker.finish(last.get())
                    speaking = speaker.spokeAnything
                    v.endSpeech()
                    if (!speaking) finishSession()
                }
                else -> {
                    speaking = v.speak(reply.text)
                    if (!speaking) finishSession()
                }
            }
        }
    }

    private fun finishSession() {
        main.removeCallbacks(watchdog)
        speaking = false
        busy = false
        try {
            porcupine?.start()
            paused = false
            setStatus("Listening for '${Config.WAKE_WORD_TRIGGER}'")
        } catch (e: Exception) {
            setStatus("Wake engine error: ${e.localizedMessage}")
        }
    }

    private val voiceCallbacks = object : VoiceController.Callbacks {
        override fun onListeningStarted() {}
        override fun onLevel(level: Float) {}
        override fun onHeard(text: String) {
            if (busy && !AppState.foreground) handleHeard(text)
        }

        override fun onListenFailed(message: String) {
            if (busy) finishSession()
        }

        override fun onSpeakingStarted() {}
        override fun onSpeakingFinished() {
            if (busy && speaking) finishSession()
        }

        override fun onVoiceNotice(message: String) {}
    }

    private fun setStatus(text: String) {
        AppState.wakeStatus = text
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Luna Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        main.removeCallbacks(watchdog)
        scope.cancel()
        try {
            porcupine?.stop()
            porcupine?.delete()
        } catch (e: Exception) {
            // ignore
        }
        porcupine = null
        voice?.shutdown()
        voice = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Luna Wake Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
