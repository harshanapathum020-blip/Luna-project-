package com.luna.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionResult(
    val ok: Boolean,
    val message: String,
    val data: String? = null,
    val image: Bitmap? = null
)

/** Everything Luna can actually do on the phone. */
class PhoneActions(context: Context, private val prefs: LunaPrefs) {

    private val ctx = context.applicationContext
    private val si: Boolean get() = prefs.language == "si"

    private fun t(sinhala: String, english: String) = if (si) sinhala else english

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    private fun needPermission(name: String) = ActionResult(
        false,
        t("$name අවසරය දෙන්න ඕනේ. Controls tab එකෙන් Grant කරන්න.", "I need the $name permission. Grant it in the Controls tab.")
    )

    private fun needAccessibility() = ActionResult(
        false,
        t(
            "Accessibility access එක on කරලා නැහැ. Controls tab එකෙන් on කරන්න.",
            "Accessibility access is off. Turn it on in the Controls tab."
        )
    )

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun failed() = ActionResult(false, t("ඒක කරන්න බැරි වුණා.", "I couldn't do that."))

    suspend fun execute(a: JSONObject): ActionResult {
        return try {
            when (a.optString("name").lowercase(Locale.ROOT)) {
                "set_alarm" -> setAlarm(a)
                "set_timer" -> setTimer(a)
                "call" -> call(a)
                "send_sms" -> sendSms(a)
                "open_app" -> openApp(a)
                "flashlight" -> flashlight(a)
                "volume" -> volume(a)
                "media" -> media(a)
                "web_search" -> webSearch(a)
                "open_settings" -> openSettings(a)
                "phone" -> phoneCommand(a)
                "tap" -> tap(a)
                "type" -> typeText(a)
                "read_whatsapp" -> readWhatsApp(a)
                "reply_whatsapp" -> replyWhatsApp(a)
                "read_sms" -> readSms(a)
                "device_info" -> deviceInfo()
                "read_screen" -> readScreen()
                "look_screen" -> lookScreen()
                else -> ActionResult(false, t("ඒක කරන්න මට බැහැ.", "I can't do that."))
            }
        } catch (e: Exception) {
            ActionResult(false, t("වැඩේ කරද්දී අවුලක් වුණා.", "Something went wrong."))
        }
    }

    // ---- alarms & timers -------------------------------------------------------------------

    private fun setAlarm(a: JSONObject): ActionResult {
        val hour = a.optInt("hour", -1)
        val minute = a.optInt("minute", 0)
        if (hour !in 0..23 || minute !in 0..59) {
            return ActionResult(false, t("වෙලාව තේරුණේ නැහැ.", "I didn't catch the time."))
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Luna"))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val shown = String.format(Locale.US, "%02d:%02d", hour, minute)
        return if (launch(intent)) {
            ActionResult(true, t("අලාම් එක $shown ට දැම්මා.", "Alarm set for $shown."))
        } else failed()
    }

    private fun setTimer(a: JSONObject): ActionResult {
        val seconds = a.optInt("seconds", 0)
        if (seconds <= 0) return ActionResult(false, t("කාලය තේරුණේ නැහැ.", "I didn't catch the duration."))
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Luna"))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val mins = seconds / 60
        val rest = seconds % 60
        val shown = when {
            mins > 0 && rest > 0 -> "$mins min $rest sec"
            mins > 0 -> "$mins min"
            else -> "$rest sec"
        }
        return if (launch(intent)) {
            ActionResult(true, t("ටයිමර් එක $shown ට දැම්මා.", "Timer set for $shown."))
        } else failed()
    }

    // ---- calls & SMS -----------------------------------------------------------------------

    /** Returns (displayName, number) for the first contact whose name contains [name]. */
    private fun findContact(name: String): Pair<String, String>? {
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) return Pair(it.getString(0).orEmpty(), it.getString(1).orEmpty())
        }
        return null
    }

    private fun lookupName(number: String): String {
        if (!has(Manifest.permission.READ_CONTACTS)) return number
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: number
        } catch (e: Exception) {
            number
        }
    }

    private fun resolveTarget(a: JSONObject): Pair<String, String>? {
        val number = a.optString("number").trim()
        if (number.isNotEmpty()) return Pair(number, number)
        val contact = a.optString("contact").trim()
        if (contact.isEmpty()) return null
        return findContact(contact)
    }

    private fun call(a: JSONObject): ActionResult {
        if (!has(Manifest.permission.CALL_PHONE)) return needPermission("Phone")
        val target = resolveTarget(a)
            ?: return ActionResult(
                false,
                if (!has(Manifest.permission.READ_CONTACTS)) t("Contacts අවසරය දෙන්න ඕනේ.", "I need the Contacts permission.")
                else t("ඒ නමින් contact එකක් හම්බුණේ නැහැ.", "I couldn't find that contact.")
            )
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(Uri.fromParts("tel", target.second, null), Bundle())
        return ActionResult(true, t("${target.first} ට කෝල් කරනවා.", "Calling ${target.first}."))
    }

    private fun sendSms(a: JSONObject): ActionResult {
        if (!has(Manifest.permission.SEND_SMS)) return needPermission("SMS")
        val text = a.optString("text").trim()
        if (text.isEmpty()) return ActionResult(false, t("මැසේජ් එක මොකක්ද?", "What should the message say?"))
        val target = resolveTarget(a)
            ?: return ActionResult(false, t("ඒ නමින් contact එකක් හම්බුණේ නැහැ.", "I couldn't find that contact."))
        val sms = if (android.os.Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        sms.sendMultipartTextMessage(target.second, null, sms.divideMessage(text), null, null)
        return ActionResult(true, t("${target.first} ට මැසේජ් එක යැව්වා.", "Message sent to ${target.first}."))
    }

    private fun readSms(a: JSONObject): ActionResult {
        if (!has(Manifest.permission.READ_SMS)) return needPermission("SMS")
        val count = a.optInt("count", 3).coerceIn(1, 8)
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, count)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf("date"))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val sb = StringBuilder()
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("address", "body"),
            args,
            null
        )?.use {
            while (it.moveToNext()) {
                val who = lookupName(it.getString(0).orEmpty())
                sb.append("From $who: ${it.getString(1).orEmpty().take(300)}\n")
            }
        }
        return if (sb.isEmpty()) {
            ActionResult(true, t("SMS කිසිවක් නැහැ.", "No SMS messages."), data = "No SMS messages.")
        } else {
            ActionResult(true, "", data = sb.toString())
        }
    }

    // ---- WhatsApp --------------------------------------------------------------------------

    private fun readWhatsApp(a: JSONObject): ActionResult {
        if (!LunaNotificationListener.isEnabled(ctx)) {
            return ActionResult(
                false,
                t("Notification access එක on කරලා නැහැ. Controls tab එකෙන් on කරන්න.", "Notification access is off. Turn it on in the Controls tab.")
            )
        }
        val list = LunaNotificationListener.latest(a.optInt("count", 3))
        if (list.isEmpty()) {
            return ActionResult(
                true,
                t("අලුත් WhatsApp මැසේජ් නැහැ.", "No new WhatsApp messages."),
                data = "No new WhatsApp messages."
            )
        }
        val sb = StringBuilder()
        list.forEach { sb.append("From ${it.sender}: ${it.text.take(300)}\n") }
        return ActionResult(true, "", data = sb.toString())
    }

    private fun replyWhatsApp(a: JSONObject): ActionResult {
        if (!LunaNotificationListener.isEnabled(ctx)) {
            return ActionResult(
                false,
                t("Notification access එක on කරලා නැහැ. Controls tab එකෙන් on කරන්න.", "Notification access is off. Turn it on in the Controls tab.")
            )
        }
        val contact = a.optString("contact").trim()
        val text = a.optString("text").trim()
        if (contact.isEmpty() || text.isEmpty()) {
            return ActionResult(false, t("කාට මොකක්ද යවන්න ඕනේ?", "Who should I reply to, and what should I say?"))
        }
        return if (LunaNotificationListener.reply(ctx, contact, text)) {
            ActionResult(true, t("$contact ට රිප්ලයි කළා.", "Replied to $contact."))
        } else {
            ActionResult(
                false,
                t("$contact ගෙන් අලුත් WhatsApp මැසේජ් එකක් නැති නිසා රිප්ලයි කරන්න බැරි වුණා.", "I can only reply to a recent WhatsApp message from $contact, and there isn't one.")
            )
        }
    }

    // ---- apps, settings, media -------------------------------------------------------------

    private fun openApp(a: JSONObject): ActionResult {
        val wanted = a.optString("app").trim().lowercase(Locale.ROOT)
        if (wanted.isEmpty()) return failed()
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(main, 0).firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase(Locale.ROOT)
            label == wanted || label.contains(wanted) || wanted.contains(label)
        } ?: return ActionResult(false, t("ඒ app එක හම්බුණේ නැහැ.", "I couldn't find that app."))
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return failed()
        return if (launch(intent)) {
            ActionResult(true, t("${match.loadLabel(pm)} open කළා.", "Opened ${match.loadLabel(pm)}."))
        } else failed()
    }

    private fun flashlight(a: JSONObject): ActionResult {
        val on = a.optBoolean("on", true)
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionResult(false, t("මේ ෆෝන් එකේ ෆ්ලෑෂ් එකක් නැහැ.", "This phone has no flashlight."))
        cm.setTorchMode(id, on)
        return ActionResult(true, if (on) t("ෆ්ලෑෂ්ලයිට් එක on කළා.", "Flashlight on.") else t("ෆ්ලෑෂ්ලයිට් එක off කළා.", "Flashlight off."))
    }

    private fun volume(a: JSONObject): ActionResult {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        when (a.optString("direction").lowercase(Locale.ROOT)) {
            "up" -> repeat(2) { am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
            "down" -> repeat(2) { am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
            "mute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "max" -> am.setStreamVolume(stream, am.getStreamMaxVolume(stream), AudioManager.FLAG_SHOW_UI)
            else -> return failed()
        }
        return ActionResult(true, t("වොලියුම් එක හැදුවා.", "Volume adjusted."))
    }

    private fun media(a: JSONObject): ActionResult {
        val code = when (a.optString("command").lowercase(Locale.ROOT)) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return failed()
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return ActionResult(true, t("හරි.", "Done."))
    }

    private fun webSearch(a: JSONObject): ActionResult {
        val q = a.optString("query").trim()
        if (q.isEmpty()) return failed()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
        return if (launch(intent)) ActionResult(true, t("සර්ච් කරනවා.", "Searching.")) else failed()
    }

    private fun openSettings(a: JSONObject): ActionResult {
        val action = when (a.optString("page").lowercase(Locale.ROOT)) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (launch(Intent(action))) ActionResult(true, t("Settings open කළා.", "Opened settings.")) else failed()
    }

    // ---- accessibility based control -------------------------------------------------------

    private fun phoneCommand(a: JSONObject): ActionResult {
        val s = LunaAccessibilityService.instance ?: return needAccessibility()
        val ok = when (a.optString("command").lowercase(Locale.ROOT)) {
            "back" -> s.global(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> s.global(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> s.global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> s.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "lock" -> s.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "scroll_down" -> s.scroll(true)
            "scroll_up" -> s.scroll(false)
            else -> false
        }
        return if (ok) ActionResult(true, t("හරි.", "Done.")) else failed()
    }

    private fun tap(a: JSONObject): ActionResult {
        val s = LunaAccessibilityService.instance ?: return needAccessibility()
        val text = a.optString("text").trim()
        if (text.isEmpty()) return failed()
        return if (s.clickByText(text)) {
            ActionResult(true, t("\"$text\" එක ඔබළා.", "Tapped \"$text\"."))
        } else {
            ActionResult(false, t("\"$text\" කියලා එකක් screen එකේ හම්බුණේ නැහැ.", "I couldn't find \"$text\" on the screen."))
        }
    }

    private fun typeText(a: JSONObject): ActionResult {
        val s = LunaAccessibilityService.instance ?: return needAccessibility()
        val text = a.optString("text")
        if (text.isEmpty()) return failed()
        return if (s.typeText(text)) {
            ActionResult(true, t("ටයිප් කළා.", "Typed it."))
        } else {
            ActionResult(false, t("ටයිප් කරන්න text box එකක් select කරලා නැහැ.", "No text box is selected."))
        }
    }

    private fun readScreen(): ActionResult {
        val s = LunaAccessibilityService.instance ?: return needAccessibility()
        val text = s.readScreenText()
        val shown = text.ifBlank { "The screen has no readable text." }
        return ActionResult(true, "", data = shown)
    }

    private suspend fun lookScreen(): ActionResult {
        val s = LunaAccessibilityService.instance ?: return needAccessibility()
        val shot = s.screenshot()
        return if (shot != null) {
            ActionResult(true, "", data = "A screenshot of the current screen is attached.", image = shot)
        } else {
            val text = s.readScreenText().ifBlank { "The screen has no readable text." }
            ActionResult(true, "", data = "Screenshot not available. Screen text:\n$text")
        }
    }

    // ---- info ------------------------------------------------------------------------------

    private fun deviceInfo(): ActionResult {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val now = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return ActionResult(true, "", data = "Battery: $battery%. Now: $now.")
    }
}
