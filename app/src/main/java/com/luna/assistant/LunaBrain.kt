package com.luna.assistant

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LunaBrain(apiKey: String = Config.GEMINI_API_KEY) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        systemInstruction = content {
            text(
                """
                You are Luna, an advanced, friendly personal voice AI assistant.
                You are responsive, intelligent, and supportive.
                You natively comprehend and seamlessly communicate in English, Sinhala (සිංහල), and Singlish.
                Keep spoken answers concise, informative, and engaging.
                """.trimIndent()
            )
        }
    )

    suspend fun askLuna(prompt: String, attachment: Bitmap? = null): String = withContext(Dispatchers.IO) {
        try {
            val response = if (attachment != null) {
                val inputContent = content {
                    image(attachment)
                    text(prompt.ifBlank { "Describe what you see and respond." })
                }
                generativeModel.generateContent(inputContent)
            } else {
                generativeModel.generateContent(prompt)
            }

            response.text ?: "I heard you, but I couldn't form a response."
        } catch (e: Exception) {
            "Error communicating with Luna engine: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}
