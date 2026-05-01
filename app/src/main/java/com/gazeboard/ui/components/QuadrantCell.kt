package com.gazeboard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.ui.theme.GlassCardShape
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassPanel

/**
 * A single full-quadrant cell for the 2×2 gaze layout.
 *
 * isActive and dwellProgress are still driven by the existing ViewModel dwell
 * state; this component only changes the visual treatment.
 */
@Composable
fun QuadrantCell(
    label: String,
    subLabel: String = "",
    isActive: Boolean,
    dwellProgress: Float,
    modifier: Modifier = Modifier
) {
    val activeAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(150),
        label = "quadrantActive"
    )
    val normalizedProgress = dwellProgress.coerceIn(0f, 1f)
    val isLetterGroup = label.any { it == '\n' } || label.count { it == ' ' } >= 4

    Box(
        modifier = modifier
            .padding(8.dp)
            .glassPanel(
                shape = GlassCardShape,
                fill = if (isActive) GlassColors.GlassStrong else GlassColors.Glass,
                border = if (isActive) GlassColors.BorderStrong else GlassColors.Border
            )
            .drawWithContent {
                val corner = 36.dp.toPx()
                if (activeAlpha > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f * activeAlpha),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                }
                if (isActive && normalizedProgress > 0f) {
                    val overlayHeight = size.height * normalizedProgress
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(0f, size.height - overlayHeight),
                        size = Size(size.width, overlayHeight),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                }

                drawContent()

                if (isActive) {
                    val inset = 2.dp.toPx()
                    val strokeWidth = if (normalizedProgress > 0f) 2.5.dp.toPx() else 1.5.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.30f + 0.22f * activeAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = strokeWidth)
                    )
                    if (normalizedProgress > 0f) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.82f),
                            startAngle = -90f,
                            sweepAngle = 360f * normalizedProgress,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx()),
                            topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                            size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx())
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 360.dp)
        ) {
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    color = GlassColors.TextPrimary,
                    fontSize = if (isLetterGroup) 30.sp else 48.sp,
                    fontWeight = if (isLetterGroup) FontWeight.Light else FontWeight.Medium,
                    letterSpacing = if (isLetterGroup) 3.sp else 0.sp,
                    lineHeight = if (isLetterGroup) 43.sp else 54.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (subLabel.isNotEmpty()) {
                Text(
                    text = subLabel,
                    color = GlassColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}
