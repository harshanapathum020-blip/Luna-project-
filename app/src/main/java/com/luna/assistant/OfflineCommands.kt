package com.luna.assistant

import org.json.JSONObject
import java.util.Locale

/**
 * Tiny rule based command understanding that works with no internet and no Gemini.
 * Understands English, Singlish and Sinhala keywords for the most common phone commands.
 */
object OfflineCommands {

    private fun obj(name: String) = JSONObject().put("name", name)

    private fun hasAny(t: String, vararg words: String) = words.any { t.contains(it) }

    fun parse(raw: String): JSONObject? {
        val t = raw.lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return null

        if (hasAny(t, "timer", "ටයිමර්", "time on", "ටයිම් ඔන්")) {
            val secs = parseDuration(t)
            if (secs > 0) return obj("set_timer").put("seconds", secs)
        }

        if (hasAny(t, "alarm", "alaram", "ඇලාම්", "අලාම්", "එලාම්")) {
            val clock = parseClock(t)
            if (clock != null) return obj("set_alarm").put("hour", clock.first).put("minute", clock.second)
        }

        if (hasAny(t, "whatsapp", "වට්සැප්", "වට්ස්ඇප්")) {
            if (hasAny(t, "read", "message", "බලන්න", "කියවන්න", "මැසේජ්")) {
                return obj("read_whatsapp").put("count", 3)
            }
        }

        if (hasAny(t, "sms", "text message", "පණිවිඩ")) {
            if (hasAny(t, "read", "check", "බලන්න", "කියවන්න")) return obj("read_sms").put("count", 3)
        }

        if (hasAny(t, "call", "dial", "කෝල්", "ඇමතුම", "ෆෝන් කරන්න", "කතා කරන්න")) {
            val number = Regex("\\+?\\d[\\d\\s-]{6,}").find(t)?.value?.replace(Regex("[\\s-]"), "")
            if (number != null) return obj("call").put("number", number)
            val name = extractName(t)
            if (name != null) return obj("call").put("contact", name)
        }

        if (hasAny(t, "flashlight", "flash light", "torch", "ටෝච්", "ෆ්ලෑෂ්")) {
            val off = hasAny(t, " off", "නිවන්න", "නවත්වන්න", "ඕෆ්", "නිවාදාන්න")
            return obj("flashlight").put("on", !off)
        }

        if (hasAny(t, "volume", "වොලියුම්", "සද්දය", "සද්දෙ", "සවුන්ඩ්")) {
            val dir = when {
                hasAny(t, "max", "full", "උපරිම") -> "max"
                hasAny(t, "mute", "silent", "නිශ්ශබ්ද") -> "mute"
                hasAny(t, "down", "decrease", "lower", "අඩු") -> "down"
                hasAny(t, "up", "increase", "raise", "වැඩි", "ඉහළ") -> "up"
                else -> null
            }
            if (dir != null) return obj("volume").put("direction", dir)
        }

        if (hasAny(t, "battery", "බැටරි", "what time", "වෙලාව", "කීයද", "today's date", "what date")) {
            return obj("device_info")
        }

        if (hasAny(t, "scroll down", "පහළට")) return obj("phone").put("command", "scroll_down")
        if (hasAny(t, "scroll up", "උඩට")) return obj("phone").put("command", "scroll_up")
        if (hasAny(t, "go home", "home screen", "හෝම් එකට")) return obj("phone").put("command", "home")
        if (Regex("\\b(go back|back)\\b").containsMatchIn(t) || hasAny(t, "ආපසු")) {
            return obj("phone").put("command", "back")
        }

        if (hasAny(t, "read screen", "read the screen", "screen eke", "ස්ක්‍රීන් එකේ")) return obj("read_screen")

        val openEn = Regex("^(?:open|launch|start)\\s+(.+)$").find(t)
        if (openEn != null) return obj("open_app").put("app", openEn.groupValues[1].trim())
        val openSi = Regex("^(.+?)\\s*(?:ඕපන්|විවෘත|open)").find(t)
        if (openSi != null) return obj("open_app").put("app", openSi.groupValues[1].trim())

        return null
    }

    private fun extractName(t: String): String? {
        val en = Regex("(?:call|dial|phone)\\s+(.+)$").find(t)
        if (en != null) return clean(en.groupValues[1])
        val si = Regex("^(.+?)\\s*(?:ට|ව|ටත්)?\\s*(?:call|කෝල්|ඇමතුම|ෆෝන්|කතා)").find(t)
        if (si != null) return clean(si.groupValues[1])
        return null
    }

    private fun clean(s: String): String? {
        val c = s.replace(Regex("\\b(now|please|a call)\\b"), "").trim().trimEnd('.', '?', '!')
        return c.ifBlank { null }
    }

    private fun parseDuration(t: String): Int {
        var total = 0
        Regex("(\\d+)\\s*(?:hours?|hrs?|පැය)").find(t)?.let { total += it.groupValues[1].toInt() * 3600 }
        Regex("(\\d+)\\s*(?:minutes?|mins?|මිනිත්තු)").find(t)?.let { total += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*(?:seconds?|secs?|තත්පර)").find(t)?.let { total += it.groupValues[1].toInt() }
        return total
    }

    private fun parseClock(t: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?").find(t) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val marker = m.groupValues[3]
        val pm = marker.startsWith("p") || hasAny(t, "හවස", "රෑ", "රාත්‍රී", "evening", "night")
        val am = marker.startsWith("a") || hasAny(t, "උදේ", "පාන්දර", "morning")
        if (pm && hour < 12) hour += 12
        if (am && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return Pair(hour, minute)
    }
}
