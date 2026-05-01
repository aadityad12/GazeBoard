package com.gazeboard.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.components.DebugOverlay
import com.gazeboard.ui.components.SettingsOverlay
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassClickable
import com.gazeboard.ui.theme.glassPill

/**
 * Four-corner calibration screen.
 * User looks at each corner dot for 1.5 s to commit that corner.
 * Order: Top-Left -> Top-Right -> Bottom-Left -> Bottom-Right.
 */
@Composable
fun CalibrationScreen(
    state: AppState.Calibrating,
    faceDetected: Boolean,
    debugMode: Boolean = false,
    onToggleDebug: () -> Unit = {},
    fps: Float = 0f,
    inferenceMs: Long = 0L,
    faceDetectMs: Long = 0L,
    rawPitch: Float = 0f,
    rawYaw: Float = 0f,
    accelerator: String = "—",
    modifier: Modifier = Modifier
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "calibrationPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "calibrationPulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.Background)
            .systemBarsPadding()
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val activeIndex = state.step.coerceIn(0, 3)
            val targetRadius = 48.dp.toPx()
            val innerRadius = 24.dp.toPx()
            val dotRadius = 7.dp.toPx()

            val corners = listOf(
                Offset(size.width * 0.15f, size.height * 0.15f),
                Offset(size.width * 0.85f, size.height * 0.15f),
                Offset(size.width * 0.15f, size.height * 0.85f),
                Offset(size.width * 0.85f, size.height * 0.85f)
            )

            corners.forEachIndexed { index, pos ->
                val isActive = index == activeIndex
                val alpha = if (isActive) 1f else 0.24f
                val activeRadius = if (isActive) targetRadius * pulse else targetRadius

                drawCircle(
                    color = Color.White.copy(alpha = 0.05f * alpha),
                    radius = activeRadius,
                    center = pos
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f * alpha),
                    radius = activeRadius,
                    center = pos,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f * alpha),
                    radius = innerRadius,
                    center = pos
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = dotRadius,
                    center = pos
                )

                if (isActive && state.dwellProgress > 0f) {
                    val arcRadius = targetRadius + 7.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.88f),
                        startAngle = -90f,
                        sweepAngle = 360f * state.dwellProgress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(pos.x - arcRadius, pos.y - arcRadius),
                        size = Size(arcRadius * 2, arcRadius * 2)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Look at the dot",
                color = GlassColors.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(width = if (index == state.step) 24.dp else 8.dp, height = 8.dp)
                            .glassPill(
                                fill = if (index == state.step) Color.White else Color.White.copy(alpha = 0.18f),
                                border = Color.Transparent,
                                shadowElevation = 2.dp
                            )
                    )
                }
            }
        }

        FaceIndicator(
            faceDetected = faceDetected,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(46.dp)
                .glassPill(shadowElevation = 8.dp)
                .glassClickable { settingsOpen = true },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = GlassColors.TextPrimary, fontSize = 20.sp)
        }

        if (debugMode) {
            DebugOverlay(
                fps = fps,
                inferenceMs = inferenceMs,
                faceDetectMs = faceDetectMs,
                rawPitch = rawPitch,
                rawYaw = rawYaw,
                accelerator = accelerator,
                faceDetected = faceDetected,
                appState = state,
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            )
        }
    }

    if (settingsOpen) {
        SettingsOverlay(
            accelerator = accelerator,
            inferenceMs = inferenceMs,
            debugMode = debugMode,
            onToggleDebug = onToggleDebug,
            onRecalibrate = { /* no-op during calibration */ },
            onClose = { settingsOpen = false }
        )
    }
}
