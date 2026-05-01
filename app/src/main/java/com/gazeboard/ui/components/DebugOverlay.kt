package com.gazeboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState

@Composable
fun DebugOverlay(
    fps: Float,
    inferenceMs: Long,
    faceDetectMs: Long,
    rawPitch: Float,
    rawYaw: Float,
    accelerator: String,
    faceDetected: Boolean,
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val stateLabel = when (appState) {
        is AppState.Calibrating    -> "Calibrating[${appState.step}]"
        is AppState.QuickPhrases   -> "QuickPhrases q${appState.activeQuadrant}"
        is AppState.Spelling       -> "Spelling seq=${appState.gestureSequence}"
        is AppState.WordSelection  -> "WordSelect ${appState.candidates}"
        is AppState.ModelLoadError -> "Error"
    }

    Column(
        modifier = modifier
            .background(Color(0xE0000000), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x4000FF88), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        DebugLine("FPS",        "%.1f".format(fps))
        DebugLine("Infer",      "${inferenceMs}ms · $accelerator")
        DebugLine("FaceDetect", "${faceDetectMs}ms")
        DebugLine("Face",       if (faceDetected) "YES" else "NO")
        DebugLine("Pitch",      "%.3f".format(rawPitch))
        DebugLine("Yaw",        "%.3f".format(rawYaw))
        DebugLine("State",      stateLabel)
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text(
        text = "%-10s %s".format(label, value),
        color = Color(0xFF00FF88),
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace
    )
}
