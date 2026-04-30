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
 * The cursor is a white ring with a semi-transparent fill so it's visible against any phrase cell.
 * Disappears when gazePoint is null (no face detected).
 *
 * PERSON C OWNS THIS FILE.
 *
 * TODO(Person C): The gazePoint is currently in normalized [0,1] coordinates.
 * Convert to screen pixels by multiplying by the Canvas size.
 * This conversion happens inside the Canvas drawWithContent lambda where size is available.
 */
@Composable
fun GazeCursor(
    gazePoint: Offset?,   // normalized [0,1] coordinates; null = no face
    modifier: Modifier = Modifier
) {
    if (gazePoint == null) return

    Canvas(modifier = modifier.fillMaxSize()) {
        // Convert normalized gaze coordinates to screen pixels
        val screenX = gazePoint.x * size.width
        val screenY = gazePoint.y * size.height

        val cursorRadius = 24f
        val strokeWidth = 3f

        // Semi-transparent white fill
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = cursorRadius,
            center = Offset(screenX, screenY)
        )

        // Solid white ring
        drawCircle(
            color = Color.White,
            radius = cursorRadius,
            center = Offset(screenX, screenY),
            style = Stroke(width = strokeWidth)
        )

        // Small center dot for precision
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = Offset(screenX, screenY)
        )
    }
}
