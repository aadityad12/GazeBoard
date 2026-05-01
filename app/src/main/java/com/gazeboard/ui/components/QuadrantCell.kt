package com.gazeboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val IdleBg     = Color(0xFF0D1B2A)
private val HoveredBg  = Color(0xFF1A3A5C)
private val ActiveBorder = Color(0xFF00BFFF)
private val IdleBorder   = Color(0xFF2A4A6A)
private val LabelColor   = Color(0xFFE8F4FD)
private val SubLabelColor = Color(0xFF8AAECA)

/**
 * A single full-quadrant cell for the 2×2 gaze layout.
 *
 * Shows a dwell progress ring around the border when active.
 * isActive drives the ring; dwellProgress (0..1) fills the ring.
 */
@Composable
fun QuadrantCell(
    label: String,
    subLabel: String = "",
    isActive: Boolean,
    dwellProgress: Float,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) HoveredBg else IdleBg,
        animationSpec = tween(150),
        label = "quadrantBg"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.4f,
        animationSpec = tween(150),
        label = "borderAlpha"
    )

    Box(
        modifier = modifier
            .background(bgColor)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) ActiveBorder.copy(alpha = borderAlpha) else IdleBorder
            )
            .drawWithContent {
                drawContent()
                if (isActive && dwellProgress > 0f) {
                    val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 3.dp.toPx()
                    drawArc(
                        color = ActiveBorder,
                        startAngle = -90f,
                        sweepAngle = 360f * dwellProgress,
                        useCenter = false,
                        style = stroke,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - inset * 2, size.height - inset * 2
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = LabelColor,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (subLabel.isNotEmpty()) {
                Text(
                    text = subLabel,
                    color = SubLabelColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
