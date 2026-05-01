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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.AppState
import com.gazeboard.ui.components.NpuBadge
import com.gazeboard.ui.components.QuadrantCell

private val ScreenBg = Color(0xFF060E1A)
private val SentenceBarBg = Color(0xFF0D1B2A)
private val SentenceTextColor = Color(0xFFB0C8E0)
private val DividerColor = Color(0xFF1A3050)

/**
 * Quick Phrases home screen.
 *
 * Quadrant layout:
 *   1 = YES    2 = NO
 *   3 = HELP   4 = MORE ► (navigate to Spell mode)
 *
 * User looks at a quadrant for 1 second to trigger it.
 */
@Composable
fun QuickPhrasesScreen(
    state: AppState.QuickPhrases,
    accelerator: String,
    inferenceMs: Long,
    faceDetected: Boolean,
    onPreviewSurfaceReady: ((Any) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
    ) {
        // Sentence bar + NPU badge
        SentenceBar(sentence = state.sentence, accelerator = accelerator, inferenceMs = inferenceMs)

        // Top row
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

        // Bottom row
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

    // Face indicator — top-right corner overlay
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        FaceIndicator(faceDetected = faceDetected, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun SentenceBar(sentence: String, accelerator: String, inferenceMs: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SentenceBarBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (sentence.isBlank()) "— speak a phrase or spell a word —" else sentence,
            color = if (sentence.isBlank()) SentenceTextColor.copy(alpha = 0.5f) else SentenceTextColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
            modifier = Modifier.align(Alignment.CenterStart).padding(end = 130.dp)
        )
        NpuBadge(
            accelerator = accelerator,
            inferenceMs = inferenceMs,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun FaceIndicator(faceDetected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = if (faceDetected) Color(0xFF1A4A1A) else Color(0xFF4A1A1A),
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
