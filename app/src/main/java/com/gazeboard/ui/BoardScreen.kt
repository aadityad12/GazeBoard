package com.gazeboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.state.GazeState
import com.gazeboard.ui.components.GazeCursor
import com.gazeboard.ui.components.NpuBadge
import com.gazeboard.ui.components.PhraseCell

/**
 * The main communication board: a 2×3 grid of phrase cells occupying the full screen.
 *
 * PERSON C OWNS THIS FILE.
 *
 * Layout:
 *   ┌──────────┬──────────┬──────────┐
 *   │  cell 0  │  cell 1  │  cell 2  │
 *   ├──────────┼──────────┼──────────┤
 *   │  cell 3  │  cell 4  │  cell 5  │
 *   └──────────┴──────────┴──────────┘
 *
 * The gaze cursor and NPU badge float as overlays above the grid.
 * A "no face" warning overlay appears when the camera loses the face.
 */
@Composable
fun BoardScreen(
    gazeState: GazeState,
    appState: AppState,
    phrases: List<String>,
    onRecalibrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCell = (appState as? AppState.Selected)?.cellIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070D14))
    ) {
        // 2×3 phrase grid — takes full screen
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Row 0: cells 0, 1, 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (col in 0..2) {
                    val cellIndex = col
                    PhraseCell(
                        phrase = phrases.getOrElse(cellIndex) { "" },
                        isHovered = gazeState.hoveredCell == cellIndex,
                        isSelected = selectedCell == cellIndex,
                        dwellProgress = if (gazeState.hoveredCell == cellIndex)
                            gazeState.dwellProgress else 0f,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            }

            // Row 1: cells 3, 4, 5
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (col in 0..2) {
                    val cellIndex = 3 + col
                    PhraseCell(
                        phrase = phrases.getOrElse(cellIndex) { "" },
                        isHovered = gazeState.hoveredCell == cellIndex,
                        isSelected = selectedCell == cellIndex,
                        dwellProgress = if (gazeState.hoveredCell == cellIndex)
                            gazeState.dwellProgress else 0f,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            }
        }

        // Gaze cursor overlay — floats above everything
        GazeCursor(gazePoint = gazeState.gazePoint)

        // NPU badge — top-right corner, always visible
        NpuBadge(
            accelerator = gazeState.accelerator,
            inferenceMs = gazeState.inferenceMs,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // Distance warning — top-center
        gazeState.distanceWarning?.let { warning ->
            Text(
                text = warning,
                color = Color(0xFFFFD600),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // No face detected overlay
        if (!gazeState.faceDetected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No face detected\nCenter your face in the camera",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Triple-tap anywhere to recalibrate (hidden but discoverable)
        // TODO(Person C): Make this more discoverable in the UI — maybe a small
        // "Recalibrate" button that appears after 5s of no face detection.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { /* ignore */ },
                        onLongPress = { onRecalibrate() }  // long press = recalibrate
                    )
                }
        )
    }
}
