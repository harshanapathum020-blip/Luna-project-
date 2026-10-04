package com.luna.assistant

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BrainReply(val text: String, val isError: Boolean)

class LunaBrain(private val prefs: LunaPrefs) {

    private fun resolveKey(): String {
        val saved = prefs.apiKey
        if (saved.isNotBlank()) return saved
        val built = Config.GEMINI_API_KEY
        return if (built.startsWith("YOUR_")) "" else built
    }

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
                systemInstruction = content { text(SYSTEM_PROMPT) }
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

    companion object {
        private val SYSTEM_PROMPT = """
            You are Luna, an advanced, friendly personal voice AI assistant.
            You are responsive, intelligent, and supportive.
            You natively comprehend and seamlessly communicate in English, Sinhala (සිංහල), and Singlish.
            Reply in the language the user used: Sinhala script for Sinhala, English for English.
            If the user writes Singlish (Sinhala in English letters), reply in Sinhala script.
            Keep answers short and natural because they are read aloud.
            Do not use markdown symbols such as asterisks, hashes or bullet characters.
        """.trimIndent()
    }
}
