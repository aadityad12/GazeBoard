package com.gazeboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.components.DebugOverlay
import com.gazeboard.ui.components.QuadrantCell
import com.gazeboard.ui.components.SettingsOverlay
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassPill

/**
 * Spell Mode screen — shared for Spelling and WordSelection states.
 *
 * Spelling:      quadrants show letter groups (A-G / H-M / N-S / T-Z)
 * WordSelection: quadrants show word candidates + BACK
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
    val candidates = (state as? AppState.WordSelection)?.candidates ?: emptyList()
    val gestureSequence = when (state) {
        is AppState.Spelling -> state.gestureSequence
        is AppState.WordSelection -> state.gestureSequence
        else -> emptyList()
    }
    val activeQuadrant = when (state) {
        is AppState.Spelling -> state.activeQuadrant
        is AppState.WordSelection -> state.activeQuadrant
        else -> null
    }
    val dwellProgress = when (state) {
        is AppState.Spelling -> state.dwellProgress
        is AppState.WordSelection -> state.dwellProgress
        else -> 0f
    }
    val sentence = when (state) {
        is AppState.Spelling -> state.sentence
        is AppState.WordSelection -> state.sentence
        else -> ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.Background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 82.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadrantCell(
                    label = if (isWordSelection) candidates.getOrElse(0) { "" } else "A B C D\nE F G",
                    isActive = activeQuadrant == 1,
                    dwellProgress = if (activeQuadrant == 1) dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                QuadrantCell(
                    label = if (isWordSelection) candidates.getOrElse(1) { "" } else "H I J K\nL M",
                    isActive = activeQuadrant == 2,
                    dwellProgress = if (activeQuadrant == 2) dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadrantCell(
                    label = if (isWordSelection) candidates.getOrElse(2) { "" } else "N O P Q\nR S",
                    isActive = activeQuadrant == 3,
                    dwellProgress = if (activeQuadrant == 3) dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                QuadrantCell(
                    label = if (isWordSelection) "Back" else "T U V W\nX Y Z",
                    subLabel = if (isWordSelection) "SPELL MODE" else "",
                    isActive = activeQuadrant == 4,
                    dwellProgress = if (activeQuadrant == 4) dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }

        SpellCenterIsland(
            gestureSequence = gestureSequence,
            isWordSelection = isWordSelection,
            modifier = Modifier.align(Alignment.Center)
        )

        SentenceBar(
            sentence = sentence,
            accelerator = accelerator,
            inferenceMs = inferenceMs,
            onSettingsClick = { settingsOpen = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        FaceIndicator(
            faceDetected = faceDetected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 24.dp)
        )

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
                modifier = Modifier.align(Alignment.TopStart).padding(top = 70.dp, start = 24.dp)
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
private fun SpellCenterIsland(
    gestureSequence: List<Int>,
    isWordSelection: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(min = 220.dp)
            .glassPill(shadowElevation = 28.dp)
            .padding(horizontal = 34.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                val filled = index < gestureSequence.size
                Box(
                    modifier = Modifier
                        .size(if (filled) 13.dp else 12.dp)
                        .clip(CircleShape)
                        .background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
                )
            }
        }
        Text(
            text = if (isWordSelection) "Select Word" else "Spell Mode",
            color = GlassColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = if (isWordSelection) "DWELL TO CHOOSE" else "${gestureSequence.size} GROUPS SELECTED",
            color = GlassColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun GestureSequenceRow(gestureSequence: List<Int>) {
    val groupLabels = mapOf(1 to "A-G", 2 to "H-M", 3 to "N-S", 4 to "T-Z")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassPill(shadowElevation = 8.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Gestures", color = GlassColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        gestureSequence.forEachIndexed { i, g ->
            Text(
                text = if (i == 0) "  ${groupLabels[g] ?: g}" else "  ·  ${groupLabels[g] ?: g}",
                color = GlassColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
