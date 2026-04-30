package com.gazeboard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single phrase cell in the 2×3 communication board.
 *
 * Displays the phrase text and animates a progress ring around the cell border
 * as the user dwells on it. Full ring (1.0) triggers selection.
 *
 * PERSON C OWNS THIS FILE.
 *
 * Visual states:
 *   - Idle:    dark background, white text, no ring
 *   - Hovered: slightly lighter background, progress ring fills around edge
 *   - Selected: bright accent color, no ring (brief flash before cooldown)
 */
@Composable
fun PhraseCell(
    phrase: String,
    isHovered: Boolean,
    isSelected: Boolean,
    dwellProgress: Float,  // 0.0–1.0; only meaningful when isHovered
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isHovered) dwellProgress else 0f,
        animationSpec = tween(durationMillis = 80),  // fast enough to feel real-time
        label = "dwell_progress"
    )

    val backgroundColor = when {
        isSelected -> Color(0xFF00C853)               // green — selected
        isHovered  -> Color(0xFF1565C0)               // bright blue — hovered
        else       -> Color(0xFF1A2744)               // visible dark blue — idle
    }

    val borderColor = when {
        isSelected -> Color(0xFF69F0AE)
        isHovered  -> Color(0xFF82CAFF)               // bright border when hovered
        else       -> Color(0xFF3D6B9E)               // visible border — idle
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(2.dp, borderColor),
        contentAlignment = Alignment.Center
    ) {
        // Phrase text — large enough to read at arm's length
        Text(
            text = phrase,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        // Dwell progress ring — drawn over the full cell
        if (isHovered && animatedProgress > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8f
                val padding = strokeWidth / 2

                drawArc(
                    color = Color(0xFF4FC3F7),         // cyan progress ring
                    startAngle = -90f,                 // start at top
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(padding, padding),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - strokeWidth,
                        size.height - strokeWidth
                    )
                )
            }
        }
    }
}
