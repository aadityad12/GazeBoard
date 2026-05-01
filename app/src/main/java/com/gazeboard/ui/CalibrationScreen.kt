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

private val ScreenBg = Color(0xFF060E1A)
private val TargetColor = Color(0xFF00BFFF)
private val RingColor = Color(0xFF00BFFF)

/**
 * Four-corner calibration screen.
 *
 * Shows a dot at the current corner target with a circular dwell progress arc.
 * User looks at the dot for 1.5 seconds to commit each corner.
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
    val cornerNames = listOf("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right")
    val currentCorner = cornerNames.getOrElse(state.step) { "Done" }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
    ) {
        // Instructions
        Text(
            text = "Calibration ${state.step + 1} / 4\nLook at the $currentCorner target",
            color = Color(0xFFB0C8E0),
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )

        // Corner targets drawn on canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius = 20f
            val ringRadius = 48f
            val margin = 80f

            // Corner positions: TL, TR, BL, BR
            val corners = listOf(
                Offset(margin, margin),
                Offset(size.width - margin, margin),
                Offset(margin, size.height - margin),
                Offset(size.width - margin, size.height - margin)
            )

            corners.forEachIndexed { index, pos ->
                val isActive = index == state.step
                val alpha = if (isActive) 1f else 0.25f

                // Ring outline
                drawCircle(
                    color = TargetColor.copy(alpha = alpha * 0.4f),
                    radius = ringRadius,
                    center = pos,
                    style = Stroke(width = 2f)
                )

                // Dwell progress arc (active corner only)
                if (isActive && state.dwellProgress > 0f) {
                    drawArc(
                        color = RingColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.dwellProgress,
                        useCenter = false,
                        style = Stroke(width = 6f, cap = StrokeCap.Round),
                        topLeft = Offset(pos.x - ringRadius, pos.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2)
                    )
                }

                // Center dot
                drawCircle(
                    color = TargetColor.copy(alpha = alpha),
                    radius = dotRadius,
                    center = pos
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = dotRadius * 0.4f,
                    center = pos
                )
            }
        }

        // Face indicator
        FaceIndicator(
            faceDetected = faceDetected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // Step progress dots at bottom
        Text(
            text = (0..3).joinToString("  ") { if (it < state.step) "●" else if (it == state.step) "◉" else "○" },
            color = Color(0xFF00BFFF),
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // Debug toggle
        TextButton(
            onClick = onToggleDebug,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
        ) {
            Text(if (debugMode) "⬛ Debug" else "□ Debug", color = Color(0xFF00FF88), fontSize = 12.sp)
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }
    }
}
