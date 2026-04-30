package com.gazeboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/**
 * Badge showing current inference accelerator and latency.
 * Green = NPU (good), Yellow = GPU (acceptable), Red = CPU (fail state).
 *
 * This badge is visible to judges during the demo and is worth points in the
 * Technological Implementation category (40 pts). Keep it always visible.
 *
 * PERSON C OWNS THIS FILE.
 */
@Composable
fun NpuBadge(
    accelerator: String,
    inferenceMs: Long,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (accelerator) {
        "NPU", "NPU+GPU" -> Color(0xFF00C853)  // green — confirmed Hexagon NPU execution
        "GPU"            -> Color(0xFFFFD600)  // yellow — GPU fallback
        "CPU"            -> Color(0xFFFF1744)  // red — CPU fallback (should not happen in demo)
        else             -> Color(0xFF757575)  // grey — not yet initialized
    }

    val textColor = when (accelerator) {
        "GPU"  -> Color.Black   // yellow background needs dark text
        else   -> Color.White
    }

    val label = if (accelerator == "—" || inferenceMs == 0L) {
        accelerator
    } else {
        "$accelerator · ${inferenceMs}ms"
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
