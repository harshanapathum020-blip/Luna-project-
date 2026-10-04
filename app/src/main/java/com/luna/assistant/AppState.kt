package com.luna.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small process-wide flags shared between the UI and the background wake service. */
object AppState {
    /** True while MainActivity is visible. The wake service answers by itself when this is false. */
    @Volatile
    var foreground: Boolean = false

    /** Human readable wake-word status shown in Controls. */
    var wakeStatus by mutableStateOf("Off")
}
