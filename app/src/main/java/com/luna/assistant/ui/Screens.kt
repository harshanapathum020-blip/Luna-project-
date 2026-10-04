package com.luna.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.assistant.ChatMessage
import com.luna.assistant.LunaViewModel
import com.luna.assistant.OrbState
import com.luna.assistant.ui.theme.DarkSurface
import com.luna.assistant.ui.theme.DarkSurfaceContainer
import com.luna.assistant.ui.theme.ErrorRed
import com.luna.assistant.ui.theme.GlassBorder
import com.luna.assistant.ui.theme.NeonCyan
import com.luna.assistant.ui.theme.NeonPurple
import com.luna.assistant.ui.theme.TextPrimary
import com.luna.assistant.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun statusLabel(state: OrbState): String = when (state) {
    OrbState.IDLE -> "READY"
    OrbState.LISTENING -> "LISTENING"
    OrbState.THINKING -> "THINKING"
    OrbState.SPEAKING -> "SPEAKING"
}

private fun captionFor(state: OrbState): String = when (state) {
    OrbState.IDLE -> "Luna Online"
    OrbState.LISTENING -> "Mama ahan inne..."
    OrbState.THINKING -> "Hithanawa..."
    OrbState.SPEAKING -> "Katha karanawa..."
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = GlassBorder,
    focusedLabelColor = NeonCyan,
    unfocusedLabelColor = TextSecondary,
    cursorColor = NeonCyan,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    focusedContainerColor = DarkSurfaceContainer,
    unfocusedContainerColor = DarkSurfaceContainer
)

// ---------------------------------------------------------------- Luna tab

@Composable
fun LunaScreen(vm: LunaViewModel, onMic: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val count = vm.messages.size
    LaunchedEffect(count) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    val micActive = vm.orbState == OrbState.LISTENING || vm.orbState == OrbState.SPEAKING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LUNA ASSISTANT",
                    color = NeonCyan,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    style = TextStyle(shadow = Shadow(NeonCyan.copy(alpha = 0.6f), Offset.Zero, 18f))
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(orbColor(vm.orbState))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusLabel(vm.orbState),
                        color = NeonPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "  •  ${vm.model}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            LanguagePill(
                language = vm.language,
                onToggle = { vm.setLanguage(if (vm.language == "si") "en" else "si") }
            )
        }

        // orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMic
                ),
            contentAlignment = Alignment.Center
        ) {
            LunaOrb(
                state = vm.orbState,
                level = vm.level,
                modifier = Modifier.size(210.dp)
            )
        }
        Text(
            text = captionFor(vm.orbState),
            color = orbColor(vm.orbState),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // spectrum
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            LunaSpectrum(
                state = vm.orbState,
                level = vm.level,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // conversation
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (vm.messages.isEmpty()) {
                item {
                    Surface(
                        color = DarkSurfaceContainer,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("LUNA", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Mama Luna. Mic eka tap karala katha karanna, nathnam yata type karanna. English, Sinhala, Singlish dekama puluwan.",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
            } else {
                items(vm.messages) { m -> MessageBubble(m) }
            }
        }

        vm.notice?.let { msg ->
            Surface(
                color = NeonPurple.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { vm.dismissNotice() }
            ) {
                Text(msg, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
        }

        if (vm.messages.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Hi Luna, kohomada?",
                    "Mata joke ekak kiyanna",
                    "Tell me an interesting fact"
                ).forEach { s -> SuggestionChip(s) { vm.sendText(s) } }
            }
        }

        // input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (micActive) NeonPurple else NeonCyan)
                    .clickable(onClick = onMic),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (micActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = DarkSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask in English or Sinhala...") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    vm.sendText(input)
                    input = ""
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        vm.sendText(input)
                        input = ""
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = NeonCyan)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LanguagePill(language: String, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, NeonPurple.copy(alpha = 0.6f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (language == "si") "සිංහල" else "English",
            color = NeonPurple,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, GlassBorder, shape)
            .background(DarkSurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
fun MessageBubble(m: ChatMessage) {
    val mine = m.fromUser
    val bg = when {
        m.isError -> ErrorRed.copy(alpha = 0.14f)
        mine -> NeonCyan.copy(alpha = 0.16f)
        else -> DarkSurfaceContainer
    }
    val line = when {
        m.isError -> ErrorRed.copy(alpha = 0.5f)
        mine -> NeonCyan.copy(alpha = 0.35f)
        else -> GlassBorder
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp
            ),
            border = BorderStroke(1.dp, line),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = m.text,
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// -------------------------------------------------------------- History tab

@Composable
fun HistoryScreen(vm: LunaViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HISTORY",
                color = NeonCyan,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            if (vm.messages.isNotEmpty()) {
                TextButton(onClick = {
                    if (confirm) {
                        vm.clearHistory()
                        confirm = false
                    } else {
                        confirm = true
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                    Spacer(Modifier.width(6.dp))
                    Text(if (confirm) "Tap again to clear" else "Clear", color = ErrorRed)
                }
            }
        }

        if (vm.messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "History khali. Luna ekka katha karapu gaman methana penawa.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(vm.messages.asReversed()) { m ->
                    val stamp = remember(m.time) {
                        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(m.time))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = (if (m.fromUser) "You" else "Luna") + "  •  " + stamp,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        MessageBubble(m)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- Controls tab

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = DarkSurfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
fun ControlsScreen(vm: LunaViewModel) {
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "CONTROLS",
            color = NeonCyan,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )

        SectionCard("GEMINI AI") {
            OutlinedTextField(
                value = vm.apiKey,
                onValueChange = {
                    vm.apiKey = it
                    saved = false
                },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Show or hide key",
                            tint = TextSecondary
                        )
                    }
                },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Free key ekak ganna: aistudio.google.com/apikey",
                color = TextSecondary,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = vm.model,
                onValueChange = {
                    vm.model = it
                    saved = false
                },
                label = { Text("Model") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Default: gemini-2.5-flash. Model not found error ekak awoth wenas model eka danna.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        SectionCard("VOICE") {
            Text("Katha karana bhashawa", color = TextPrimary, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LangChoice("සිංහල", vm.language == "si") { vm.setLanguage("si") }
                LangChoice("English", vm.language == "en") { vm.setLanguage("en") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Luna reply eka katha karanna", color = TextPrimary, fontSize = 14.sp)
                Switch(
                    checked = vm.speakReplies,
                    onCheckedChange = { vm.setSpeakReplies(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkSurface,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurface,
                        uncheckedBorderColor = TextSecondary
                    )
                )
            }
            Text(
                "Sinhala voice ekak nathnam: phone Settings > Text-to-speech eken Google engine ekata Sinhala voice data install karanna.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        SectionCard("WAKE WORD") {
            Text(
                "'Wake up baby' wake word eka dan off. Eka wada karanna Picovoice key ekak saha .ppn file ekak one.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        Button(
            onClick = {
                vm.saveSettings()
                saved = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (saved) "Saved ✓" else "Save", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LangChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) NeonCyan else Color.Transparent)
            .border(1.dp, if (selected) NeonCyan else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (selected) DarkSurface else TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}
