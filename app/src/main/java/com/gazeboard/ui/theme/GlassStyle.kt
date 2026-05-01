package com.gazeboard.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassColors {
    val Background = Color(0xFF141313)
    val Surface = Color(0xFF201F1F)
    val Glass = Color(0x0DFFFFFF)
    val GlassStrong = Color(0x14FFFFFF)
    val GlassPressed = Color(0x1FFFFFFF)
    val GlassRed = Color(0x10FF5050)
    val Border = Color(0x1AFFFFFF)
    val BorderStrong = Color(0x4DFFFFFF)
    val TextPrimary = Color(0xF2FFFFFF)
    val TextSecondary = Color(0x99FFFFFF)
    val TextMuted = Color(0x66FFFFFF)
    val Good = Color(0xFFB7F7C8)
    val Warn = Color(0xFFFFE27A)
    val Bad = Color(0xFFFF8F8F)
}

val GlassCardShape = RoundedCornerShape(36.dp)
val GlassPillShape = RoundedCornerShape(999.dp)

fun Modifier.glassPanel(
    shape: Shape = GlassCardShape,
    fill: Color = GlassColors.Glass,
    border: Color = GlassColors.Border,
    shadowElevation: Dp = 22.dp
): Modifier = this
    .shadow(shadowElevation, shape, clip = false)
    .clip(shape)
    .background(fill)
    .border(1.dp, border, shape)

fun Modifier.glassPill(
    fill: Color = GlassColors.GlassStrong,
    border: Color = GlassColors.Border,
    shadowElevation: Dp = 18.dp
): Modifier = glassPanel(
    shape = GlassPillShape,
    fill = fill,
    border = border,
    shadowElevation = shadowElevation
)

fun Modifier.glassClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
