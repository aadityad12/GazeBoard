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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.components.DebugOverlay
import com.gazeboard.ui.components.NpuBadge
import com.gazeboard.ui.components.QuadrantCell
import com.gazeboard.ui.components.SettingsOverlay
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassClickable
import com.gazeboard.ui.theme.glassPill

/**
 * Quick Phrases home screen.
 *
 * Quadrant layout:
 *   1 = YES    2 = NO
 *   3 = HELP   4 = MORE (navigate to Spell mode)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.Background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, top = 82.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuadrantCell(
                    label = "Yes",
                    isActive = state.activeQuadrant == 1,
                    dwellProgress = if (state.activeQuadrant == 1) state.dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                QuadrantCell(
                    label = "No",
                    isActive = state.activeQuadrant == 2,
                    dwellProgress = if (state.activeQuadrant == 2) state.dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuadrantCell(
                    label = "Help",
                    isActive = state.activeQuadrant == 3,
                    dwellProgress = if (state.activeQuadrant == 3) state.dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                QuadrantCell(
                    label = "More",
                    subLabel = "SPELL MODE",
                    isActive = state.activeQuadrant == 4,
                    dwellProgress = if (state.activeQuadrant == 4) state.dwellProgress else 0f,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }

        CenterIsland(title = "GazeBoard", subtitle = "Ready", modifier = Modifier.align(Alignment.Center))

        SentenceBar(
            sentence = state.sentence,
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
fun SentenceBar(
    sentence: String,
    accelerator: String,
    inferenceMs: Long,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .glassPill()
            .padding(start = 20.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "▦",
            color = GlassColors.TextMuted,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = if (sentence.isBlank()) "Speak Your Mind..." else sentence,
            color = if (sentence.isBlank()) GlassColors.TextSecondary else GlassColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        NpuBadge(accelerator = accelerator, inferenceMs = inferenceMs)
        if (onSettingsClick != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .glassPill(shadowElevation = 8.dp)
                    .glassClickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", color = GlassColors.TextPrimary, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun FaceIndicator(faceDetected: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (faceDetected) "FACE" else "NO FACE",
        color = if (faceDetected) GlassColors.Good else GlassColors.Bad,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .glassPill(
                fill = if (faceDetected) Color(0x1415FF72) else Color(0x16FF6060),
                border = if (faceDetected) Color(0x3315FF72) else Color(0x33FF6060),
                shadowElevation = 8.dp
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun CenterIsland(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(min = 190.dp)
            .glassPill(shadowElevation = 28.dp)
            .padding(horizontal = 38.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = GlassColors.TextPrimary,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )
        Text(
            text = subtitle.uppercase(),
            color = GlassColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
