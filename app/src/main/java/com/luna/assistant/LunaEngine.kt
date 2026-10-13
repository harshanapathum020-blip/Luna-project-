package com.luna.assistant

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * One place that turns "what the user said" into "what Luna answers", including running phone
 * actions. Used by both the app screen and the background wake-word service.
 */
class LunaEngine(private val context: Context, private val prefs: LunaPrefs) {

    /** [streamed] is true when [onPartial] already delivered (parts of) this reply while it was generated. */
    data class Result(val text: String, val isError: Boolean, val streamed: Boolean = false)

    private val brain = LunaBrain(prefs)
    private val actions = PhoneActions(context, prefs)

    private val si: Boolean get() = prefs.language == "si"

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * [onPartial] receives the reply text that is safe to show or speak so far (action blocks
     * removed), growing over time, possibly from a background thread. The text only ever grows
     * across the whole call, even when a second Gemini round is needed for tool data.
     */
    suspend fun process(
        userText: String,
        history: List<ChatMessage>,
        onPartial: ((String) -> Unit)? = null
    ): Result {
        if (prefs.offlineMode || !isOnline()) return runOffline(userText)

        var streamed = false
        var committed = "" // visible text from earlier Gemini rounds
        var lastVisible = ""
        val firstCb: ((String) -> Unit)? = onPartial?.let { out ->
            { raw: String ->
                val vis = ActionParser.visible(raw)
                if (vis.isNotBlank()) streamed = true
                lastVisible = vis
                out(committed + vis)
            }
        }

        val reply = brain.askLuna(userText, history, null, firstCb)
        if (reply.isError) {
            val cmd = OfflineCommands.parse(userText)
            return if (cmd != null) execOffline(cmd) else Result(reply.text, true)
        }

        val parsed = ActionParser.parse(reply.text)
        if (parsed.actions.isEmpty()) {
            return Result(parsed.spoken.ifBlank { reply.text }, false, streamed)
        }

        val messages = mutableListOf<String>()
        val data = StringBuilder()
        var image: Bitmap? = null
        for (a in parsed.actions) {
            val r = actions.execute(a)
            if (!r.ok) return Result(r.message, false, false)
            if (r.message.isNotBlank()) messages.add(r.message)
            if (r.data != null) data.append(r.data).append('\n')
            if (r.image != null) image = r.image
        }

        if (data.isEmpty() && image == null) {
            return Result(parsed.spoken.ifBlank { messages.joinToString(" ") }, false, streamed)
        }

        val toolText = data.toString().take(3000)
        val follow = "The user said: \"$userText\"\nTOOL_RESULT:\n$toolText\n" +
            "Now answer the user briefly in their language. Do not output any ACTION line."
        committed = if (lastVisible.isBlank()) "" else lastVisible.trimEnd() + " "
        var streamed2 = false
        val secondCb: ((String) -> Unit)? = onPartial?.let { out ->
            { raw: String ->
                val vis = ActionParser.visible(raw)
                if (vis.isNotBlank()) streamed2 = true
                out(committed + vis)
            }
        }
        val second = brain.askLuna(follow, history, image, secondCb)
        if (second.isError) return Result(toolText.take(600).trim(), false, false)
        val spoken = ActionParser.parse(second.text).spoken
        val finalText = spoken.ifBlank { toolText.take(600).trim() }
        return Result(finalText, false, streamed2)
    }

    private suspend fun runOffline(userText: String): Result {
        val cmd = OfflineCommands.parse(userText)
        if (cmd == null) {
            return Result(
                if (si) {
                    "ඔෆ්ලයින් නිසා මට තේරෙන්නේ ෆෝන් එකේ දේවල් විතරයි. අලාම්, ටයිමර්, කෝල්, ෆ්ලෑෂ්ලයිට්, වොලියුම් වගේ දේවල් කියන්න."
                } else {
                    "I'm offline, so I only understand phone commands like alarm, timer, call, flashlight and volume."
                },
                false
            )
        }
        return execOffline(cmd)
    }

    private suspend fun execOffline(cmd: org.json.JSONObject): Result {
        val r = actions.execute(cmd)
        val text = when {
            !r.ok -> r.message
            r.data != null -> r.data.take(600).trim()
            else -> r.message
        }
        return Result(text, false)
    }
}
