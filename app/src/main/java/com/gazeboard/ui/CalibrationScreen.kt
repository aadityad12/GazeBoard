package com.gazeboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private val ScreenBg    = Color(0xFF030B14)
private val TargetColor = Color(0xFF00BFFF)

/**
 * Four-corner calibration screen.
 * User looks at each corner dot for 1.5 s to commit that corner.
 * Order: Top-Left → Top-Right → Bottom-Left → Bottom-Right.
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
    val cornerNames = listOf("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right")
    val currentCorner = cornerNames.getOrElse(state.step) { "Done" }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
    ) {
        // Instruction text
        Text(
            text = "Calibration  ${state.step + 1} / 4\nLook at the  $currentCorner  target",
            color = Color(0xFF8AAECA),
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )

        // Corner dots + dwell arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius  = 18f
            val ringRadius = 46f
            val margin     = 88f

            val corners = listOf(
                Offset(margin, margin),
                Offset(size.width - margin, margin),
                Offset(margin, size.height - margin),
                Offset(size.width - margin, size.height - margin)
            )

            corners.forEachIndexed { index, pos ->
                val isActive = index == state.step
                val alpha    = if (isActive) 1f else 0.22f

                // Ring outline
                drawCircle(
                    color = TargetColor.copy(alpha = alpha * 0.35f),
                    radius = ringRadius,
                    center = pos,
                    style = Stroke(width = 1.5f)
                )

                // Dwell progress arc
                if (isActive && state.dwellProgress > 0f) {
                    drawArc(
                        color = TargetColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.dwellProgress,
                        useCenter = false,
                        style = Stroke(width = 6f, cap = StrokeCap.Round),
                        topLeft = Offset(pos.x - ringRadius, pos.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2)
                    )
                }

                // Center dot
                drawCircle(color = TargetColor.copy(alpha = alpha), radius = dotRadius, center = pos)
                drawCircle(color = Color.White.copy(alpha = alpha), radius = dotRadius * 0.35f, center = pos)
            }
        }

        // Face indicator
        FaceIndicator(
            faceDetected = faceDetected,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        )

        // Step progress dots
        Text(
            text = (0..3).joinToString("  ") { if (it < state.step) "●" else if (it == state.step) "◉" else "○" },
            color = TargetColor,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)
        )

        // Gear icon → settings overlay
        TextButton(
            onClick = { settingsOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
        ) {
            Text("⚙", color = Color(0xFF4A7A9A), fontSize = 22.sp)
        }

        // Debug overlay
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
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
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
