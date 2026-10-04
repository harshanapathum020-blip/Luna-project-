# Luna Assistant

Android voice assistant (Kotlin + Jetpack Compose) powered by Gemini, with a Picovoice wake word.

## What Luna can do
- Talk in Sinhala / English / Singlish (speech in, speech out)
- "Wake up Luna" wake word, also when the app is closed (foreground service)
- Alarms, timers, phone calls, SMS (send and read)
- Read WhatsApp messages and reply (via notification access)
- See the screen (screenshot to Gemini), read screen text, Back / Home / scroll / tap / type
- Open apps, flashlight, volume, media keys, web search, open settings pages
- Offline mode: phone commands work with no internet (rule based, no Gemini)

## Setup
1. Create `local.properties` in the project root (git-ignored):
   ```
   GEMINI_API_KEY=your_gemini_key
   PICOVOICE_ACCESS_KEY=your_picovoice_key
   ```
   (You can also type the Gemini key inside the app: Controls tab.)
2. Train "Wake up Luna" on https://console.picovoice.ai (platform: Android) and put the file at
   `app/src/main/assets/wake_up_luna_android.ppn`.
   The old `wake_up_baby_android.ppn` in this repo is only a placeholder.
3. Push to GitHub. The Actions workflow builds a debug APK (Actions tab > Luna-APK artifact).
4. Install the APK, open Luna > Controls and turn on:
   Permissions, Display over other apps, Accessibility, Notification access.

## Notes
- WhatsApp has no public API. Luna reads/answers messages that arrive while Notification access is on.
- Screen view sends a screenshot to Gemini. Turn Accessibility off if you do not want that.
- Offline speech recognition needs the offline speech pack in the Google app.
