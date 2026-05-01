package com.gazeboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.components.DebugOverlay
import com.gazeboard.ui.components.NpuBadge
import com.gazeboard.ui.components.QuadrantCell
import com.gazeboard.ui.components.SettingsOverlay

private val ScreenBg       = Color(0xFF030B14)
private val SentenceBarBg  = Color(0xFF0A1422)
private val SentenceText   = Color(0xFFB0C8E0)
private val DividerColor   = Color(0xFF0F2035)

/**
 * Quick Phrases home screen.
 *
 * Quadrant layout:
 *   1 = YES    2 = NO
 *   3 = HELP   4 = MORE ► (navigate to Spell mode)
 *
 * Gear icon (⚙) opens the SettingsOverlay for debug toggle and recalibrate.
 */
@Composable
fun QuickPhrasesScreen(
    state: AppState.QuickPhrases,
    accelerator: String,
    inferenceMs: Long,
    faceDetected: Boolean,
    onRecalibrate: () -> Unit = {},
    debugMode: Boolean = false,
    onToggleDebug: () -> Unit = {},
    fps: Float = 0f,
    faceDetectMs: Long = 0L,
    rawPitch: Float = 0f,
    rawYaw: Float = 0f,
    onPreviewSurfaceReady: ((Any) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var settingsOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
    ) {
        SentenceBar(sentence = state.sentence, accelerator = accelerator, inferenceMs = inferenceMs)

        // Top row: YES | NO
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            QuadrantCell(
                label = "YES",
                isActive = state.activeQuadrant == 1,
                dwellProgress = if (state.activeQuadrant == 1) state.dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(DividerColor))
            QuadrantCell(
                label = "NO",
                isActive = state.activeQuadrant == 2,
                dwellProgress = if (state.activeQuadrant == 2) state.dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(DividerColor))

        // Bottom row: HELP | MORE
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            QuadrantCell(
                label = "HELP",
                isActive = state.activeQuadrant == 3,
                dwellProgress = if (state.activeQuadrant == 3) state.dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(DividerColor))
            QuadrantCell(
                label = "MORE",
                subLabel = "► Spell Mode",
                isActive = state.activeQuadrant == 4,
                dwellProgress = if (state.activeQuadrant == 4) state.dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }
    }

    // Floating overlays
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        FaceIndicator(faceDetected = faceDetected, modifier = Modifier.align(Alignment.TopEnd))

        // Gear icon → settings overlay
        TextButton(
            onClick = { settingsOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text("⚙", color = Color(0xFF4A7A9A), fontSize = 22.sp)
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
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }

    if (settingsOpen) {
        SettingsOverlay(
            accelerator = accelerator,
            inferenceMs = inferenceMs,
            debugMode = debugMode,
            onToggleDebug = onToggleDebug,
            onRecalibrate = onRecalibrate,
            onClose = { settingsOpen = false }
        )
    }
}

@Composable
fun SentenceBar(sentence: String, accelerator: String, inferenceMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SentenceBarBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (sentence.isBlank()) "— speak a phrase or spell a word —" else sentence,
            color = if (sentence.isBlank()) SentenceText.copy(alpha = 0.4f) else SentenceText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        NpuBadge(accelerator = accelerator, inferenceMs = inferenceMs)
    }
}

@Composable
fun FaceIndicator(faceDetected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = if (faceDetected) Color(0xFF0F2D0F) else Color(0xFF2D0F0F),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (faceDetected) "● Face" else "○ No face",
            color = if (faceDetected) Color(0xFF4CAF50) else Color(0xFFEF5350),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
