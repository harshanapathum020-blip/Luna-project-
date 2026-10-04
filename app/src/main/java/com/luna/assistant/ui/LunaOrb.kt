package com.luna.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.luna.assistant.OrbState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val TWO_PI = (2.0 * PI).toFloat()
private val RAD_TO_DEG = (180.0 / PI).toFloat()

fun orbColor(state: OrbState): Color = when (state) {
    OrbState.IDLE -> Color(0xFF22E6E0)
    OrbState.LISTENING -> Color(0xFF3DFFB0)
    OrbState.THINKING -> Color(0xFFB07CFF)
    OrbState.SPEAKING -> Color(0xFF33C8FF)
}

/**
 * A phase that keeps increasing smoothly (wraps at 2*PI).
 * All animations use integer multiples of it, so the wrap is seamless.
 */
@Composable
fun rememberPhase(speed: Float): State<Float> {
    val speedState = rememberUpdatedState(speed)
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            phase.floatValue = (phase.floatValue + dt * speedState.value) % TWO_PI
        }
    }
    return phase
}

/** Holographic, Jarvis-style orb: wobbling energy rings, segmented ring, wireframe globe. */
@Composable
fun LunaOrb(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val speed = when (state) {
        OrbState.IDLE -> 0.7f
        OrbState.LISTENING -> 1.1f
        OrbState.THINKING -> 2.6f
        OrbState.SPEAKING -> 1.5f
    }
    val phase = rememberPhase(speed)
    val smooth by animateFloatAsState(level, tween(100), label = "orbLevel")
    val baseAmp by animateFloatAsState(
        when (state) {
            OrbState.IDLE -> 0.010f
            OrbState.LISTENING -> 0.018f
            OrbState.THINKING -> 0.028f
            OrbState.SPEAKING -> 0.032f
        },
        tween(500),
        label = "orbAmp"
    )
    val tint by animateColorAsState(orbColor(state), tween(500), label = "orbTint")
    val deep = lerp(tint, Color(0xFF1E5BD8), 0.55f)

    Canvas(modifier) {
        val p = phase.value
        val c = center
        val r = size.minDimension / 2f

        val amp = baseAmp +
            (if (state == OrbState.LISTENING) smooth * 0.06f else 0f) +
            (if (state == OrbState.SPEAKING) (0.5f + 0.5f * sin(3f * p)) * 0.02f else 0f)

        // soft glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.30f), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )

        // wobbling outlines
        for (layer in 0..2) {
            val path = Path()
            val steps = 120
            val baseR = r * (0.90f - layer * 0.035f)
            for (i in 0..steps) {
                val a = (i.toFloat() / steps) * TWO_PI
                val w = sin(5f * a + 2f * p + layer) +
                    0.6f * sin(3f * a - p + layer * 2f) +
                    0.4f * sin(9f * a + 3f * p)
                val rr = baseR + r * amp * w
                val x = c.x + rr * cos(a)
                val y = c.y + rr * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path = path,
                color = tint.copy(alpha = 0.55f - layer * 0.12f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // particles around the outer ring
        val dots = 120
        for (i in 0 until dots) {
            val a = (i.toFloat() / dots) * TWO_PI
            val rr = r * 0.93f + r * amp * 1.4f * sin(7f * a + 2f * p) + r * 0.012f * sin(i * 1.7f + 3f * p)
            val alpha = 0.30f + 0.55f * abs(sin(i * 0.37f + 3f * p))
            drawCircle(
                color = tint.copy(alpha = alpha),
                radius = 1.1.dp.toPx(),
                center = Offset(c.x + rr * cos(a), c.y + rr * sin(a))
            )
        }

        // segmented ring
        val ringR = r * 0.72f
        val segW = r * 0.11f
        val ringTopLeft = Offset(c.x - ringR, c.y - ringR)
        val ringSize = Size(ringR * 2f, ringR * 2f)
        val deg = p * RAD_TO_DEG
        for (i in 0 until 48) {
            val start = i * 7.5f - deg
            val alpha = 0.18f + 0.62f * abs(sin(i * 0.55f + 2f * p))
            drawArc(
                color = deep.copy(alpha = alpha),
                startAngle = start,
                sweepAngle = 5.2f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = segW)
            )
        }

        // thin rings (one dashed and slowly moving)
        drawCircle(
            color = tint.copy(alpha = 0.5f),
            radius = r * 0.64f,
            center = c,
            style = Stroke(width = 1.dp.toPx())
        )
        val dash = floatArrayOf(10f, 14f)
        drawCircle(
            color = tint.copy(alpha = 0.45f),
            radius = r * 0.57f,
            center = c,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(dash, (p / TWO_PI) * 96f)
            )
        )

        // wireframe globe
        val rs = r * 0.34f
        drawCircle(color = tint.copy(alpha = 0.10f), radius = rs, center = c)
        drawCircle(
            color = tint.copy(alpha = 0.85f),
            radius = rs,
            center = c,
            style = Stroke(width = 1.2.dp.toPx())
        )
        // meridians
        for (k in 0 until 6) {
            val theta = p + k * (PI.toFloat() / 6f)
            val w = abs(cos(theta)) * rs
            if (w > 1f) {
                drawOval(
                    color = tint.copy(alpha = 0.40f),
                    topLeft = Offset(c.x - w, c.y - rs),
                    size = Size(w * 2f, rs * 2f),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
        }
        // parallels
        for (j in -3..3) {
            val yy = rs * j / 4f
            val w = sqrt(rs * rs - yy * yy)
            val h = w * 0.28f
            drawOval(
                color = tint.copy(alpha = 0.40f),
                topLeft = Offset(c.x - w, c.y + yy - h),
                size = Size(w * 2f, h * 2f),
                style = Stroke(width = 0.8.dp.toPx())
            )
        }
        // glowing nodes on the sphere
        val n = 70
        val golden = 2.399963f
        for (i in 0 until n) {
            val yN = 1f - 2f * (i + 0.5f) / n
            val rad = sqrt(1f - yN * yN)
            val th = golden * i + p
            val x3 = rad * cos(th)
            val z3 = rad * sin(th)
            val depth = (z3 + 1f) / 2f
            val twinkle = 0.6f + 0.4f * abs(sin(i * 1.3f + 3f * p))
            drawCircle(
                color = Color.White.copy(alpha = 0.15f + 0.7f * depth * twinkle),
                radius = (0.8f + 1.4f * depth).dp.toPx(),
                center = Offset(c.x + x3 * rs, c.y + yN * rs)
            )
        }
        // core light
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), tint.copy(alpha = 0.25f), Color.Transparent),
                center = c,
                radius = rs * 0.9f
            ),
            radius = rs,
            center = c
        )
    }
}

/** Voice spectrum bars. */
@Composable
fun LunaSpectrum(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val phase = rememberPhase(
        when (state) {
            OrbState.IDLE -> 0.8f
            OrbState.LISTENING -> 4f
            OrbState.THINKING -> 5f
            OrbState.SPEAKING -> 6f
        }
    )
    val smooth by animateFloatAsState(level, tween(90), label = "specLevel")
    val tint by animateColorAsState(orbColor(state), tween(500), label = "specTint")

    Canvas(modifier) {
        val bars = 32
        val gap = 4.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        val p = phase.value
        for (i in 0 until bars) {
            val wave = (0.5f + 0.25f * sin(i * 0.45f + 2f * p) + 0.25f * sin(i * 0.9f - 3f * p))
                .coerceIn(0f, 1f)
            val a = when (state) {
                OrbState.IDLE -> 0.07f
                OrbState.LISTENING -> 0.10f + smooth * 0.85f * (0.4f + 0.6f * wave)
                OrbState.THINKING -> 0.15f + 0.45f * wave
                OrbState.SPEAKING -> 0.12f + 0.7f * wave
            }.coerceIn(0.06f, 1f)
            val h = size.height * a
            drawRoundRect(
                color = tint.copy(alpha = 0.35f + 0.6f * a),
                topLeft = Offset(i * (bw + gap), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}
