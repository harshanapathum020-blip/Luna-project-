package com.luna.assistant

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BrainReply(val text: String, val isError: Boolean)

class LunaBrain(private val prefs: LunaPrefs) {

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

    suspend fun askLuna(
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        attachment: Bitmap? = null
    ): BrainReply = withContext(Dispatchers.IO) {
        val key = resolveKey()
        if (key.isBlank()) {
            return@withContext BrainReply(
                "Gemini API key eka danne nae. Yata paatha 'Controls' tab ekata gihin key eka danna.",
                true
            )
        }
        try {
            val model = GenerativeModel(
                modelName = prefs.model,
                apiKey = key,
                systemInstruction = content { text(systemPrompt()) }
            )
            val fullPrompt = buildPrompt(prompt, history)
            val response = if (attachment != null) {
                model.generateContent(
                    content {
                        image(attachment)
                        text(fullPrompt.ifBlank { "Describe what you see and respond." })
                    }
                )
            } else {
                model.generateContent(fullPrompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) {
                BrainReply("Mama ahuwa, eth reply ekak hadaganna bae giya. Aye try karanna.", true)
            } else {
                BrainReply(text, false)
            }
        } catch (e: Exception) {
            BrainReply(friendlyError(e), true)
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.localizedMessage ?: e.javaClass.simpleName
        return when {
            msg.contains("API key not valid", ignoreCase = true) ||
                msg.contains("API_KEY_INVALID", ignoreCase = true) ->
                "API key eka waradi. Controls tab eke key eka aye balala danna."
            msg.contains("not found", ignoreCase = true) ||
                msg.contains("404") ->
                "Model eka (${prefs.model}) hambunae. Controls tab eke model nama wenas karanna (e.g. gemini-2.5-flash)."
            msg.contains("quota", ignoreCase = true) ||
                msg.contains("429") ->
                "Gemini free limit eka iwara. Tikak inna aye try karanna."
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
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
