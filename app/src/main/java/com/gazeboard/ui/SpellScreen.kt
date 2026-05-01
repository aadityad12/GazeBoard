package com.gazeboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.components.DebugOverlay
import com.gazeboard.ui.components.QuadrantCell
import com.gazeboard.ui.components.SettingsOverlay

private val ScreenBg       = Color(0xFF030B14)
private val DividerColor   = Color(0xFF0F2035)
private val GestureColor   = Color(0xFF00BFFF)

/**
 * Spell Mode screen — shared for Spelling and WordSelection states.
 *
 * Spelling:      quadrants show letter groups (A-G / H-M / N-S / T-Z)
 * WordSelection: quadrants show word candidates + BACK ◄
 *
 * Gear icon (⚙) opens SettingsOverlay.
 */
@Composable
fun SpellScreen(
    state: AppState,
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
    modifier: Modifier = Modifier
) {
    var settingsOpen by remember { mutableStateOf(false) }

    val isWordSelection = state is AppState.WordSelection
    val candidates      = (state as? AppState.WordSelection)?.candidates ?: emptyList()
    val gestureSequence = when (state) {
        is AppState.Spelling      -> state.gestureSequence
        is AppState.WordSelection -> state.gestureSequence
        else -> emptyList()
    }
    val activeQuadrant = when (state) {
        is AppState.Spelling      -> state.activeQuadrant
        is AppState.WordSelection -> state.activeQuadrant
        else -> null
    }
    val dwellProgress = when (state) {
        is AppState.Spelling      -> state.dwellProgress
        is AppState.WordSelection -> state.dwellProgress
        else -> 0f
    }
    val sentence = when (state) {
        is AppState.Spelling      -> state.sentence
        is AppState.WordSelection -> state.sentence
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
    ) {
        SentenceBar(sentence = sentence, accelerator = accelerator, inferenceMs = inferenceMs)

        // Only show gesture trail in Spelling mode; in WordSelection, user is choosing a word
        if (state is AppState.Spelling && gestureSequence.isNotEmpty()) {
            GestureSequenceRow(gestureSequence = gestureSequence)
        }

        // Top row
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            QuadrantCell(
                label = if (isWordSelection) candidates.getOrElse(0) { "" } else "A B C D\nE F G",
                isActive = activeQuadrant == 1,
                dwellProgress = if (activeQuadrant == 1) dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(DividerColor))
            QuadrantCell(
                label = if (isWordSelection) candidates.getOrElse(1) { "" } else "H I J K\nL M",
                isActive = activeQuadrant == 2,
                dwellProgress = if (activeQuadrant == 2) dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(DividerColor))

        // Bottom row
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            QuadrantCell(
                label = if (isWordSelection) candidates.getOrElse(2) { "" } else "N O P Q\nR S",
                isActive = activeQuadrant == 3,
                dwellProgress = if (activeQuadrant == 3) dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(DividerColor))
            QuadrantCell(
                label = if (isWordSelection) "BACK ◄" else "T U V W\nX Y Z",
                isActive = activeQuadrant == 4,
                dwellProgress = if (activeQuadrant == 4) dwellProgress else 0f,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }
    }

    // Floating overlays
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        FaceIndicator(faceDetected = faceDetected, modifier = Modifier.align(Alignment.TopEnd))

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
fun GestureSequenceRow(gestureSequence: List<Int>) {
    val groupLabels = mapOf(1 to "A-G", 2 to "H-M", 3 to "N-S", 4 to "T-Z")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF08111C))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Gestures: ", color = Color(0xFF8AAECA), fontSize = 13.sp)
        gestureSequence.forEachIndexed { i, g ->
            if (i > 0) {
                Text("·", color = Color(0xFF3A5A7A), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }
            Text(
                text = groupLabels[g] ?: g.toString(),
                color = GestureColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
