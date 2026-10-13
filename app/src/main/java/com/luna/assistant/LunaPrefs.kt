package com.luna.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    val time: Long = System.currentTimeMillis()
)

/** Small wrapper around SharedPreferences: settings + chat history. */
class LunaPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("luna_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "").orEmpty()
        set(value) {
            sp.edit().putString("api_key", value.trim()).apply()
        }

    var model: String
        get() {
            val saved = sp.getString("model", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
            // gemini-2.5-* is being retired (404), so move old saved values to the new default.
            return if (saved.startsWith("gemini-2.5")) DEFAULT_MODEL else saved
        }
        set(value) {
            sp.edit().putString("model", value.trim().ifBlank { DEFAULT_MODEL }).apply()
        }

    /** "si" (Sinhala) or "en" (English). */
    var language: String
        get() = sp.getString("language", "si").orEmpty().ifBlank { "si" }
        set(value) {
            sp.edit().putString("language", value).apply()
        }

    var speakReplies: Boolean
        get() = sp.getBoolean("speak_replies", true)
        set(value) {
            sp.edit().putBoolean("speak_replies", value).apply()
        }

    /** Use only on-device features: no Gemini calls, speech recognition prefers offline packs. */
    var offlineMode: Boolean
        get() = sp.getBoolean("offline_mode", false)
        set(value) {
            sp.edit().putBoolean("offline_mode", value).apply()
        }

    /** Keep the always-listening "Wake up Luna" service running. */
    var wakeEnabled: Boolean
        get() = sp.getBoolean("wake_enabled", true)
        set(value) {
            sp.edit().putBoolean("wake_enabled", value).apply()
        }

    /** After Luna finishes speaking she listens again (hands-free back and forth chat). */
    var conversationMode: Boolean
        get() = sp.getBoolean("conversation_mode", false)
        set(value) {
            sp.edit().putBoolean("conversation_mode", value).apply()
        }

    /** Chosen text-to-speech voice name per language ("si" or "en"); blank = engine default. */
    fun voiceName(lang: String): String = sp.getString("voice_$lang", "").orEmpty()

    fun setVoiceName(lang: String, name: String) {
        sp.edit().putString("voice_$lang", name).apply()
    }

    var speechRate: Float
        get() = sp.getFloat("speech_rate", 1.0f)
        set(value) {
            sp.edit().putFloat("speech_rate", value.coerceIn(0.5f, 2.0f)).apply()
        }

    var speechPitch: Float
        get() = sp.getFloat("speech_pitch", 1.0f)
        set(value) {
            sp.edit().putFloat("speech_pitch", value.coerceIn(0.5f, 2.0f)).apply()
        }

    fun loadHistory(): List<ChatMessage> {
        val raw = sp.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    fromUser = o.getBoolean("u"),
                    text = o.getString("t"),
                    isError = false,
                    time = o.optLong("ts", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.filter { !it.isError }.takeLast(MAX_HISTORY).forEach { m ->
            arr.put(
                JSONObject()
                    .put("u", m.fromUser)
                    .put("t", m.text)
                    .put("ts", m.time)
            )
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        private const val MAX_HISTORY = 200
    }
}
