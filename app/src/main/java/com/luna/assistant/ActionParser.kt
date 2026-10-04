package com.luna.assistant

import org.json.JSONObject

/** Splits a Gemini reply into spoken text and [[ACTION: {...}]] commands. */
object ActionParser {

    data class Parsed(val spoken: String, val actions: List<JSONObject>)

    private const val OPEN = "[[ACTION:"

    fun parse(raw: String): Parsed {
        val actions = mutableListOf<JSONObject>()
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val s = raw.indexOf(OPEN, i)
            if (s < 0) {
                sb.append(raw.substring(i))
                break
            }
            sb.append(raw.substring(i, s))
            val e = raw.indexOf("]]", s)
            if (e < 0) break
            val json = raw.substring(s + OPEN.length, e).trim()
            try {
                actions.add(JSONObject(json))
            } catch (ex: Exception) {
                // ignore malformed action
            }
            i = e + 2
        }
        val spoken = sb.toString().replace(Regex("\\s+"), " ").trim()
        return Parsed(spoken, actions)
    }
}
