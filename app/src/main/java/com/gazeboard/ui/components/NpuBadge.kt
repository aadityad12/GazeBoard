package com.gazeboard.ui.components

import androidx.compose.foundation.background
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

/** Small overlay badge showing the active LiteRT accelerator and inference latency. */
@Composable
fun NpuBadge(accelerator: String, inferenceMs: Long, modifier: Modifier = Modifier) {
    val isNpu = accelerator.contains("NPU", ignoreCase = true) && !accelerator.contains("FAILED", ignoreCase = true)
    val badgeColor = when {
        isNpu             -> Color(0xFF0D4A1A)
        accelerator.contains("CPU", ignoreCase = true) -> Color(0xFF2A2A0A)
        else              -> Color(0xFF4A0D0D)
    }
    val textColor = when {
        isNpu             -> Color(0xFF4CAF50)
        accelerator.contains("CPU", ignoreCase = true) -> Color(0xFFFFEB3B)
        else              -> Color(0xFFEF5350)
    }
    val label = if (inferenceMs > 0L) "LiteRT: $accelerator · ${inferenceMs}ms" else "LiteRT: $accelerator"

    Text(
        text = label,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(badgeColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
