package com.luna.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.luna.assistant.ui.theme.DarkSurface
import com.luna.assistant.ui.theme.NeonCyan
import com.luna.assistant.ui.theme.NeonPurple

/**
 * Frosted "liquid glass" surface: translucent fill, a bright top-left edge highlight and a soft
 * cyan rim. Draws without a real backdrop blur (that is expensive on phones), so it stays smooth
 * even on older devices.
 */
fun Modifier.liquidGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF), Color(0x1A00F0FF)),
            start = Offset.Zero,
            end = Offset(600f, 900f)
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(Color(0x8CFFFFFF), Color(0x3300F0FF), Color(0x0D000000))
        ),
        shape = shape
    )

/** Full-screen backdrop with soft cyan and purple light blobs, so the glass has something to refract. */
@Composable
fun LiquidBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.1f),
                        radius = size.width * 0.8f
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.15f, size.height * 0.1f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.7f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.9f, size.height * 0.7f)
                )
            },
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}
