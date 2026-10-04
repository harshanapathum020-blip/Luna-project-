package com.luna.assistant

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Reads incoming WhatsApp notifications (sender + text) and can answer them through the
 * notification's own quick-reply action. WhatsApp has no public API, so this only works for
 * messages that arrive while this access is enabled.
 */
class LunaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val n = sbn.notification ?: return
        if (sbn.packageName !in WA_PACKAGES) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() || text.isEmpty()) return
        if (title.equals("WhatsApp", ignoreCase = true)) return
        if (Regex("\\d+ (new )?messages?", RegexOption.IGNORE_CASE).containsMatchIn(text)) return

        messages.add(WaMessage(title, text, System.currentTimeMillis()))
        while (messages.size > MAX_MESSAGES) messages.removeAt(0)

        val reply = n.actions?.firstOrNull { a -> !a.remoteInputs.isNullOrEmpty() }
        if (reply != null) replyActions[title] = reply
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    data class WaMessage(val sender: String, val text: String, val time: Long)

    companion object {
        private val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private const val MAX_MESSAGES = 50

        val messages = CopyOnWriteArrayList<WaMessage>()
        private val replyActions = ConcurrentHashMap<String, Notification.Action>()

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun latest(count: Int): List<WaMessage> = messages.toList().takeLast(count.coerceIn(1, 10))

        /** Sends [text] to the latest conversation whose name contains [contact]. */
        fun reply(context: Context, contact: String, text: String): Boolean {
            val wanted = contact.lowercase(Locale.ROOT).trim()
            val key = replyActions.keys.firstOrNull {
                val k = it.lowercase(Locale.ROOT)
                k == wanted || k.contains(wanted) || wanted.contains(k)
            } ?: return false
            val action = replyActions[key] ?: return false
            val inputs = action.remoteInputs ?: return false
            if (inputs.isEmpty()) return false
            return try {
                val intent = Intent()
                val results = Bundle()
                for (input in inputs) results.putCharSequence(input.resultKey, text)
                RemoteInput.addResultsToIntent(inputs, intent, results)
                action.actionIntent.send(context, 0, intent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
