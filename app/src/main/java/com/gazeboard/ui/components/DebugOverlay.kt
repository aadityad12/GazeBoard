package com.gazeboard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassPanel

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
        is AppState.Calibrating -> "Calibrating[${appState.step}]"
        is AppState.QuickPhrases -> "QuickPhrases q${appState.activeQuadrant}"
        is AppState.Spelling -> "Spelling seq=${appState.gestureSequence}"
        is AppState.WordSelection -> "WordSelect ${appState.candidates}"
        is AppState.ModelLoadError -> "Error"
    }

    Column(
        modifier = modifier
            .glassPanel(fill = GlassColors.GlassStrong, shadowElevation = 10.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        DebugLine("FPS", "%.1f".format(fps))
        DebugLine("Infer", "${inferenceMs}ms · $accelerator")
        DebugLine("Face", "${faceDetectMs}ms · ${if (faceDetected) "YES" else "NO"}")
        DebugLine("Pitch", "%.3f".format(rawPitch))
        DebugLine("Yaw", "%.3f".format(rawYaw))
        DebugLine("State", stateLabel)
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text(
        text = "%-8s %s".format(label, value),
        color = GlassColors.Good,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace
    )
}
