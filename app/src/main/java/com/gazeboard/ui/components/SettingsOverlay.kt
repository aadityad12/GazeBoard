package com.gazeboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.ui.theme.GlassCardShape
import com.gazeboard.ui.theme.GlassColors
import com.gazeboard.ui.theme.glassClickable
import com.gazeboard.ui.theme.glassPanel
import com.gazeboard.ui.theme.glassPill

/**
 * Modal settings overlay.
 *
 * The actions are the same as before: close, recalibrate, and toggle debug mode.
 */
@Composable
fun SettingsOverlay(
    accelerator: String,
    inferenceMs: Long,
    debugMode: Boolean,
    onToggleDebug: () -> Unit,
    onRecalibrate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable(indication = null, interactionSource = scrimInteraction) { onClose() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .glassPanel(shape = GlassCardShape, fill = GlassColors.Glass, border = GlassColors.BorderStrong)
                .clickable(indication = null, interactionSource = panelInteraction) { /* absorb */ }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .glassPill(shadowElevation = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙", color = GlassColors.TextPrimary, fontSize = 21.sp)
                    }
                    Text(
                        text = "Settings",
                        color = GlassColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp
                    )
                }
                NpuBadge(accelerator = accelerator, inferenceMs = inferenceMs)
            }

            HorizontalDivider(color = GlassColors.Border)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SettingsToggle(
                    label = "Debug Mode",
                    checked = debugMode,
                    onToggle = onToggleDebug
                )
                SettingsToggle(
                    label = "Dark Mode",
                    checked = true,
                    onToggle = {}
                )
            }

            HorizontalDivider(color = GlassColors.Border)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .glassPill(shadowElevation = 8.dp)
                ) {
                    Text("Close", color = GlassColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                TextButton(
                    onClick = {
                        onRecalibrate()
                        onClose()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .glassPill(shadowElevation = 8.dp)
                ) {
                    Text("Recalibrate", color = GlassColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassClickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = GlassColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light
        )
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 38.dp)
                .glassPill(
                    fill = if (checked) GlassColors.GlassPressed else GlassColors.Glass,
                    border = if (checked) GlassColors.BorderStrong else GlassColors.Border,
                    shadowElevation = 4.dp
                )
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (checked) "ON" else "OFF",
                    color = if (checked) Color.Black else GlassColors.TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
