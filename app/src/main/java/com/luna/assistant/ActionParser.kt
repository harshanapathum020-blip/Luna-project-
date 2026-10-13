package com.luna.assistant

import org.json.JSONObject

/** Splits a Gemini reply into spoken text and [[ACTION: {...}]] commands. */
object ActionParser {

    data class Parsed(val spoken: String, val actions: List<JSONObject>)

    private const val OPEN = "[[ACTION:"

    /**
     * Text that is safe to show/speak while a reply is still streaming: complete action blocks are
     * removed and an unfinished "[[ACTION: ..." block is held back until it closes.
     */
    fun visible(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val s = raw.indexOf("[[", i)
            if (s < 0) {
                sb.append(raw.substring(i))
                break
            }
            sb.append(raw.substring(i, s))
            val e = raw.indexOf("]]", s)
            if (e < 0) return sb.toString()
            i = e + 2
        }
        var out = sb.toString()
        if (out.endsWith("[")) out = out.dropLast(1)
        return out
    }

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
