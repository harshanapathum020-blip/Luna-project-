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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.luna.assistant.ui.theme.DarkSurface
import com.luna.assistant.ui.theme.DarkSurfaceContainer
import com.luna.assistant.ui.theme.NeonCyan
import com.luna.assistant.ui.theme.NeonPurple
import com.luna.assistant.ui.theme.TextPrimary
import com.luna.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val brain = LunaBrain()
    private var isListeningState = mutableStateOf(false)

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isListeningState.value = true
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

        requestPermissionsThenStartService()

        setContent {
            MaterialTheme {
                LunaAppScaffold(brain, isListeningState)
            }
        }
    }

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

@Composable
fun LunaAppScaffold(brain: LunaBrain, isListening: MutableState<Boolean>) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var promptInput by remember { mutableStateOf("") }
    var responseOutput by remember { mutableStateOf("Say 'wake up baby' or tap mic to speak...") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = DarkSurfaceContainer) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Luna") },
                    label = { Text("Luna", color = NeonCyan) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Controls") },
                    label = { Text("Controls") }
                )
            }
        },
        containerColor = DarkSurface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LUNA ASSISTANT", color = NeonCyan, fontSize = 20.sp)
                Text(if (isListening.value) "ACTIVE" else "READY", color = NeonPurple, fontSize = 12.sp)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            ) {
                Surface(
                    modifier = Modifier.size(160.dp),
                    shape = CircleShape,
                    color = DarkSurfaceContainer.copy(alpha = 0.85f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isLoading) "Processing..." else if (isListening.value) "Listening..." else "Luna Online",
                            color = TextPrimary
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceContainer,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = responseOutput,
                    modifier = Modifier.padding(16.dp),
                    color = TextPrimary,
                    fontSize = 15.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Ask in English or Sinhala...") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceContainer,
                        unfocusedContainerColor = DarkSurfaceContainer,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedPlaceholderColor = TextSecondary,
                        unfocusedPlaceholderColor = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            val text = promptInput
                            promptInput = ""
                            isLoading = true
                            scope.launch {
                                responseOutput = brain.askLuna(text)
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.background(NeonCyan, CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = DarkSurface)
                }
            }
        }
    }
}
