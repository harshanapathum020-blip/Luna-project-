package com.luna.assistant

/**
 * Secret keys are NOT stored in code. Put them in local.properties (git-ignored):
 *   GEMINI_API_KEY=xxxx
 *   PICOVOICE_ACCESS_KEY=xxxx
 */
object Config {
    val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
    val PICOVOICE_ACCESS_KEY: String = BuildConfig.PICOVOICE_ACCESS_KEY
    const val KEYWORD_ASSET_PATH = "wake_up_luna_android.ppn"
    const val WAKE_WORD_TRIGGER = "wake up luna"
    const val ASSISTANT_NAME = "Luna"
}
