package com.luna.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.luna.assistant.ui.LunaApp
import com.luna.assistant.ui.theme.LunaTheme

class MainActivity : ComponentActivity() {

    private val vm: LunaViewModel by viewModels()

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.onWakeWord()
        }
    }

    // The microphone foreground service can only start after RECORD_AUDIO is granted,
    // otherwise Android 14+ throws a SecurityException and the app crashes.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            startWakeService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(LunaWakeService.ACTION_WAKE_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeReceiver, filter)
        }

        // Only run the always-listening wake word service when a real Picovoice key is set.
        // Without it the service would just show a notification and do nothing.
        if (wakeWordConfigured()) {
            requestPermissionsThenStartService()
        }

        setContent {
            LunaTheme {
                LunaApp(vm)
            }
        }
    }

    private fun wakeWordConfigured(): Boolean =
        !Config.PICOVOICE_ACCESS_KEY.startsWith("YOUR_")

    private fun requestPermissionsThenStartService() {
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (micGranted) {
            startWakeService()
        } else {
            val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startWakeService() {
        val serviceIntent = Intent(this, LunaWakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wakeReceiver)
    }
}
