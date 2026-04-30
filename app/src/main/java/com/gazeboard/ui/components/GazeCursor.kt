package com.gazeboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

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

    Canvas(modifier = modifier.fillMaxSize()) {
        val (x, y) = gazePoint
        val center = Offset(x, y)
        val cursorRadius = 24f
        val strokeWidth = 3f

        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = cursorRadius,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = cursorRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = center
        )
    }
}
