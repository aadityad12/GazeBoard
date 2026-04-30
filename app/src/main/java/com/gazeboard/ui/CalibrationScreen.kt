package com.gazeboard.ui

import android.graphics.PointF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.GazeState
import kotlinx.coroutines.delay

/**
 * 4-point corner calibration screen.
 *
 * PERSON C owns the UI. PERSON B owns the calibration math triggered here.
 *
 * Sequence:
 *  1. Show red dot at corner[currentPoint]
 *  2. Monitor gazeState.dwellProgress — when user dwells on the dot area for 1.5s,
 *     capture the current raw gaze and notify onCalibrationPointCaptured()
 *  3. Advance to next corner; after 4 points, call onCalibrationComplete()
 *
 * Corner order: top-left (0), top-right (1), bottom-left (2), bottom-right (3)
 *
 * TODO(Person C): Wire the dwell detection in this screen to the CalibrationEngine.
 * Currently the screen uses a timed auto-advance as a stub; replace with
 * actual gaze-dwell detection from gazeState once CameraManager is connected.
 */
@Composable
fun CalibrationScreen(
    gazeState: GazeState,
    onCalibrationPointCaptured: (screenPoint: PointF, rawGaze: PointF) -> Unit,
    onCalibrationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPoint by remember { mutableIntStateOf(0) }
    var screenSize by remember { mutableStateOf(Pair(0f, 0f)) }
    var dwellStarted by remember { mutableStateOf(false) }
    var captureDone by remember { mutableStateOf(false) }

    val CALIBRATION_DWELL_MS = 1500L
    val TRANSITION_DELAY_MS  = 400L

    // Corner target positions — computed from screen size once measured
    val cornerFraction = 0.12f  // distance from edge as fraction of screen dimension
    val corners: List<Offset> = if (screenSize.first > 0f) listOf(
        Offset(screenSize.first * cornerFraction,           screenSize.second * cornerFraction),           // TL
        Offset(screenSize.first * (1f - cornerFraction),    screenSize.second * cornerFraction),           // TR
        Offset(screenSize.first * cornerFraction,           screenSize.second * (1f - cornerFraction)),    // BL
        Offset(screenSize.first * (1f - cornerFraction),    screenSize.second * (1f - cornerFraction))     // BR
    ) else List(4) { Offset.Zero }

    val cornerLabels = listOf("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right")

    // Animated progress ring around the calibration dot
    val dwellProgress by animateFloatAsState(
        targetValue = 0f,  // TODO(Person C): replace with gazeState.dwellProgress when wired
        animationSpec = tween(CALIBRATION_DWELL_MS.toInt()),
        label = "calibration_dwell"
    )

    // Auto-advance after dwell time. Key on both currentPoint AND screenSize so the effect
    // re-runs once the layout is measured (fixes stale Offset.Zero capture bug).
    LaunchedEffect(currentPoint, screenSize) {
        if (screenSize.first == 0f) return@LaunchedEffect  // wait for layout
        captureDone = false
        delay(CALIBRATION_DWELL_MS + TRANSITION_DELAY_MS)

        if (!captureDone && corners.isNotEmpty() && corners[currentPoint] != Offset.Zero) {
            val corner = corners[currentPoint]
            val screenPt = PointF(corner.x, corner.y)
            val pitchYaw = PointF(gazeState.rawPitch, gazeState.rawYaw)

            onCalibrationPointCaptured(screenPt, pitchYaw)
            captureDone = true

            if (currentPoint < 3) {
                currentPoint++
            } else {
                onCalibrationComplete()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050A10))
            .onGloballyPositioned { coords ->
                screenSize = Pair(
                    coords.size.width.toFloat(),
                    coords.size.height.toFloat()
                )
            }
    ) {
        // Instruction text
        Text(
            text = "Calibration\nLook at the red dot · Point ${currentPoint + 1} of 4",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        )

        // Corner label
        if (currentPoint < cornerLabels.size) {
            Text(
                text = cornerLabels[currentPoint],
                color = Color(0xFF4FC3F7),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }

        // Calibration dot with dwell ring
        if (corners.isNotEmpty() && corners[currentPoint] != Offset.Zero) {
            val targetPos = corners[currentPoint]

            Canvas(modifier = Modifier.fillMaxSize()) {
                val dotRadius = 20f
                val ringRadius = 36f
                val strokeWidth = 6f

                // Red calibration dot
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = dotRadius,
                    center = targetPos
                )

                // White outer ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = ringRadius,
                    center = targetPos,
                    style = Stroke(width = strokeWidth)
                )

                // Cyan progress arc — fills clockwise from top
                if (dwellProgress > 0f) {
                    drawArc(
                        color = Color(0xFF00E5FF),
                        startAngle = -90f,
                        sweepAngle = 360f * dwellProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth),
                        topLeft = Offset(targetPos.x - ringRadius, targetPos.y - ringRadius),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2)
                    )
                }
            }
        }
    }
}
