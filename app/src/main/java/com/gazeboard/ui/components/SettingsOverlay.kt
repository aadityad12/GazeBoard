package com.gazeboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modal settings overlay (glass aesthetic).
 * Shown when user activates the gear icon on any main screen.
 * Contains NPU metric, Debug Mode toggle, Recalibrate action, and Close.
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
            .background(Color(0xCC000000))
            .clickable(indication = null, interactionSource = scrimInteraction) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .background(Color(0xFF0D1F35), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0x3300BFFF), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .clickable(indication = null, interactionSource = panelInteraction) { /* absorb */ }
        ) {
            Text(
                text = "Settings",
                color = Color(0xFFE8F4FD),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            // NPU metric badge
            val npuLabel = if (inferenceMs > 0L) "$accelerator · ${inferenceMs}ms" else accelerator
            val npuColor = if (accelerator.contains("NPU", ignoreCase = true)) Color(0xFF4CAF50) else Color(0xFFFFEB3B)
            Text(
                text = "LiteRT  $npuLabel",
                color = npuColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color(0xFF080F1A), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color(0x1A00BFFF))
            Spacer(Modifier.height(16.dp))

            // Debug Mode toggle
            SettingsToggle(
                label = "Debug Mode",
                checked = debugMode,
                onCheckedChange = { onToggleDebug() }
            )

            Spacer(Modifier.height(8.dp))

            // Dark Mode toggle (app is always dark — informational only)
            SettingsToggle(
                label = "Dark Mode",
                checked = true,
                onCheckedChange = { }
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color(0x1A00BFFF))
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0D1B2A), RoundedCornerShape(8.dp))
                ) {
                    Text("✕  Close", color = Color(0xFF8AAECA), fontSize = 14.sp)
                }
                TextButton(
                    onClick = {
                        onRecalibrate()
                        onClose()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0A2030), RoundedCornerShape(8.dp))
                ) {
                    Text("↺  Recalibrate", color = Color(0xFF00BFFF), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFB0C8E0), fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00BFFF),
                checkedTrackColor = Color(0xFF1A4A6A),
                uncheckedThumbColor = Color(0xFF4A6A8A),
                uncheckedTrackColor = Color(0xFF0D1B2A)
            )
        )
    }
}
