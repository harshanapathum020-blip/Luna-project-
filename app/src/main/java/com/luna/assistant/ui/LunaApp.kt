package com.luna.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.luna.assistant.LunaViewModel
import com.luna.assistant.ui.theme.DarkSurface
import com.luna.assistant.ui.theme.DarkSurfaceContainer
import com.luna.assistant.ui.theme.NeonCyan
import com.luna.assistant.ui.theme.TextPrimary
import com.luna.assistant.ui.theme.TextSecondary

private val TABS = listOf(
    "Luna" to Icons.Default.Mic,
    "History" to Icons.Default.History,
    "Controls" to Icons.Default.Tune
)

@Composable
fun LunaApp(vm: LunaViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.onMicPressed()
        } else {
            vm.showNotice(
                "Mic permission nathuwa katha karanna ba. Phone Settings > Apps > Luna eken Microphone allow karanna."
            )
        }
    }

    val onMic: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.onMicPressed() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        bottomBar = {
            NavigationBar(containerColor = DarkSurfaceContainer, contentColor = TextPrimary) {
                TABS.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkSurface,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            when (tab) {
                0 -> LunaScreen(vm, onMic)
                1 -> HistoryScreen(vm)
                else -> ControlsScreen(vm)
            }
        }
    }
}
