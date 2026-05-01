package com.gazeboard.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Full-screen Canvas overlay drawing the gaze cursor at the current gaze position.
 *
 * Accepts screen pixel coordinates directly from CalibrationEngine.toScreenPoint().
 * Disappears when gazePoint is null (no face detected).
 */
@Composable
fun GazeCursor(
    gazePoint: Pair<Float, Float>?,
    modifier: Modifier = Modifier
) {
    if (gazePoint == null) return

    val pulse by rememberInfiniteTransition(label = "gazePulse").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gazePulseScale"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val (x, y) = gazePoint
        val center = Offset(x, y)
        val radius = 30.dp.toPx()

        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.20f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.34f),
            radius = radius * pulse,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx(),
            center = center
        )
    }
}
