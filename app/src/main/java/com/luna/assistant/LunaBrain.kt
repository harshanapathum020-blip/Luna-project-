package com.luna.assistant

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BrainReply(val text: String, val isError: Boolean)

class LunaBrain(private val prefs: LunaPrefs) {

    private companion object {
        val FALLBACK_MODELS = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash")
    }

    private fun resolveKey(): String {
        val saved = prefs.apiKey
        if (saved.isNotBlank()) return saved
        val built = Config.GEMINI_API_KEY
        return if (built.startsWith("YOUR_")) "" else built
    }

    fun hasKey(): Boolean = resolveKey().isNotBlank()

    private fun buildPrompt(prompt: String, history: List<ChatMessage>): String {
        if (history.isEmpty()) return prompt
        val transcript = history.joinToString("\n") { m ->
            (if (m.fromUser) "User: " else "Luna: ") + m.text
        }
        return "Conversation so far:\n$transcript\n\nUser: $prompt"
    }

    /** Models to try, in order. A retired model (404) falls through to the next one. */
    private fun candidateModels(): List<String> {
        val chosen = prefs.model.trim()
        val list = mutableListOf(chosen)
        if (!chosen.endsWith("-preview")) list.add("$chosen-preview")
        for (m in FALLBACK_MODELS) if (m !in list) list.add(m)
        return list
    }

    /**
     * [onText] gets the whole reply text received so far each time a new piece streams in
     * (called from a background thread).
     */
    suspend fun askLuna(
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        attachment: Bitmap? = null,
        onText: ((String) -> Unit)? = null
    ): BrainReply = withContext(Dispatchers.IO) {
        val key = resolveKey()
        if (key.isBlank()) {
            return@withContext BrainReply(
                "Gemini API key eka danne nae. Yata paatha 'Controls' tab ekata gihin key eka danna.",
                true
            )
        }
        val fullPrompt = buildPrompt(prompt, history)
            .ifBlank { "Describe what you see and respond." }
        val system = systemPrompt()
        var lastError: Exception? = null

        for (model in candidateModels()) {
            var lowThinking = true
            // Second pass (only if the API rejects the thinking setting): same model, no thinking config.
            for (attempt in 0..1) {
                try {
                    val text = GeminiClient.streamGenerate(
                        apiKey = key,
                        model = model,
                        systemPrompt = system,
                        userText = fullPrompt,
                        image = attachment,
                        lowThinking = lowThinking,
                        onText = { partial -> onText?.invoke(partial) }
                    )
                    if (text.isEmpty()) {
                        return@withContext BrainReply(
                            "Mama ahuwa, eth reply ekak hadaganna bae giya. Aye try karanna.",
                            true
                        )
                    }
                    if (model != prefs.model) prefs.model = model
                    return@withContext BrainReply(text, false)
                } catch (e: GeminiHttpException) {
                    lastError = e
                    val thinkingRejected = e.code == 400 &&
                        e.body.contains("think", ignoreCase = true)
                    if (thinkingRejected && lowThinking) {
                        lowThinking = false
                        continue
                    }
                    break
                } catch (e: Exception) {
                    return@withContext BrainReply(friendlyError(e), true)
                }
            }
            val code = (lastError as? GeminiHttpException)?.code
            if (code != 404) break // only a missing/retired model is worth trying another one
        }
        BrainReply(friendlyError(lastError ?: Exception("unknown")), true)
    }

    private fun friendlyError(e: Exception): String {
        if (e is GeminiHttpException) {
            val body = e.body
            return when {
                e.code == 400 && (body.contains("API key", ignoreCase = true) ||
                    body.contains("API_KEY", ignoreCase = true)) ->
                    "API key eka waradi. Controls tab eke key eka aye balala danna."
                e.code == 403 ->
                    "Gemini API key ekata permission nae (403). Key eka aye balanna."
                e.code == 404 ->
                    "Model eka (${prefs.model}) hambunae. Controls tab eke model nama wenas karanna (e.g. gemini-3.1-flash-lite)."
                e.code == 429 ->
                    "Gemini free limit eka iwara. Tikak inna aye try karanna."
                e.code == 503 || e.code == 500 ->
                    "Gemini server eka busy. Tikak inna aye try karanna."
                else -> "Gemini error ${e.code}. ${body.take(160)}"
            }
        }
        val msg = e.localizedMessage ?: e.javaClass.simpleName
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("connect", ignoreCase = true) ->
                "Internet awulak wage. Connection eka balala aye try karanna."
            else -> "Luna engine error: $msg"
        }
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return """
            You are Luna, an advanced, friendly personal voice AI assistant living inside the user's Android phone.
            You natively comprehend and seamlessly communicate in English, Sinhala (සිංහල), and Singlish.
            Reply in the language the user used: Sinhala script for Sinhala, English for English.
            If the user writes Singlish (Sinhala in English letters), reply in Sinhala script.
            Keep answers short and natural because they are read aloud.
            Do not use markdown symbols such as asterisks, hashes or bullet characters.
            Current local date and time: $now

            PHONE ACTIONS
            You can control the phone. When the user asks you to do something on the phone, write one or more
            action lines in exactly this format (valid JSON on one line), plus one short confirmation sentence:
            [[ACTION: {"name":"set_alarm","hour":7,"minute":30,"label":"Wake up"}]]

            Available actions (name and parameters):
            set_alarm: hour (0-23), minute, label
            set_timer: seconds, label
            call: contact (name as spoken) or number
            send_sms: contact or number, text
            open_app: app (app name)
            flashlight: on (true or false)
            volume: direction (up, down, mute, max)
            media: command (play_pause, next, previous)
            web_search: query
            open_settings: page (wifi, bluetooth, display, sound, battery, location, main)
            phone: command (back, home, recents, notifications, scroll_down, scroll_up, lock)
            tap: text (visible text of the button to tap on screen)
            type: text (types into the focused text box)
            read_whatsapp: count (latest WhatsApp messages)
            reply_whatsapp: contact, text
            read_sms: count (latest SMS messages)
            device_info: no parameters (battery level, date, time)
            read_screen: no parameters (text currently on the screen)
            look_screen: no parameters (a screenshot is attached so you can see the screen)

            Rules:
            - For relative times (in 10 minutes, tomorrow morning) calculate from the current time above.
            - read_whatsapp, read_sms, device_info, read_screen and look_screen return data. For these, output ONLY the
              action line and no other text. You will then receive TOOL_RESULT and must answer the user from it.
            - For send_sms, reply_whatsapp and call, only act when the user clearly asked. Put exactly the words the user
              wants to send in text, and repeat the message in your confirmation sentence.
            - Never claim an action succeeded unless you emitted its action line.
            - Do not use an action for ordinary questions or chat.
        """.trimIndent()
    }
}
