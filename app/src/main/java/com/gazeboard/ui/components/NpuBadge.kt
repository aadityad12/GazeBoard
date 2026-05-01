package com.gazeboard.ui.components

import androidx.compose.foundation.border
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
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassPill

/** Small overlay badge showing the active LiteRT accelerator and inference latency. */
@Composable
fun NpuBadge(accelerator: String, inferenceMs: Long, modifier: Modifier = Modifier) {
    val isNpu = accelerator.contains("NPU", ignoreCase = true) &&
        !accelerator.contains("FAILED", ignoreCase = true)
    val textColor = when {
        isNpu -> GlassColors.Good
        accelerator.contains("CPU", ignoreCase = true) -> GlassColors.Warn
        else -> GlassColors.Bad
    }
    val label = if (inferenceMs > 0L) {
        "LiteRT · $accelerator · ${inferenceMs}ms"
    } else {
        "LiteRT · $accelerator"
    }

    Box(
        modifier = modifier
            .glassPill(
                fill = if (isNpu) Color(0x1415FF72) else GlassColors.GlassStrong,
                border = if (isNpu) Color(0x3315FF72) else GlassColors.Border,
                shadowElevation = 10.dp
            )
            .border(1.dp, GlassColors.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp
        )
    }
}
