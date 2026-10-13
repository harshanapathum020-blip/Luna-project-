package com.luna.assistant

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GeminiHttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")

/**
 * Direct streaming client for the Gemini REST API. Streaming lets Luna start speaking after the
 * first sentence instead of waiting for the whole answer, and we can turn "thinking" down, which is
 * what made the old SDK call slow.
 */
object GeminiClient {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    /**
     * Blocking call (run on Dispatchers.IO). [onText] receives the whole text received so far every
     * time a new piece arrives. Returns the final text.
     */
    fun streamGenerate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        image: Bitmap?,
        lowThinking: Boolean,
        onText: (String) -> Unit
    ): String {
        val parts = JSONArray()
        if (image != null) {
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().put("mimeType", "image/jpeg").put("data", encodeImage(image))
                )
            )
        }
        parts.put(JSONObject().put("text", userText))

        val generationConfig = JSONObject()
            .put("temperature", 0.7)
            .put("maxOutputTokens", 1024)
        if (lowThinking) {
            val thinking = thinkingConfigFor(model)
            if (thinking != null) generationConfig.put("thinkingConfig", thinking)
        }

        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            .put(
                "contents",
                JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
            )
            .put("generationConfig", generationConfig)

        val url = URL(BASE + model.trim() + ":streamGenerateContent?alt=sse")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 40_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) {
                val err = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (e: Exception) {
                    ""
                }
                throw GeminiHttpException(code, err)
            }

            val full = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val json = line.removePrefix("data:").trim()
                    if (json.isEmpty() || json == "[DONE]") continue
                    val piece = extractText(json)
                    if (piece.isNotEmpty()) {
                        full.append(piece)
                        onText(full.toString())
                    }
                }
            }
            return full.toString().trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun thinkingConfigFor(model: String): JSONObject? {
        val m = model.lowercase()
        return when {
            m.contains("gemini-3") -> JSONObject().put("thinkingLevel", "minimal")
            m.contains("gemini-2.5") -> JSONObject().put("thinkingBudget", 0)
            else -> null
        }
    }

    private fun extractText(json: String): String {
        return try {
            val root = JSONObject(json)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val maxSide = 1024
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
