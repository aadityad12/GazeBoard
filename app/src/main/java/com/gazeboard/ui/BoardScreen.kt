package com.gazeboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.GazeBoardViewModel

@Composable
fun BoardScreen(viewModel: GazeBoardViewModel) {
    val gazePoint by viewModel.gazePoint.collectAsState()
    val dwellingCell by viewModel.dwellingCellIndex.collectAsState()
    val dwellProgress by viewModel.dwellProgress.collectAsState()
    val lastSpoken by viewModel.lastSpokenPhrase.collectAsState()
    val inferenceMs by viewModel.inferenceMs.collectAsState()
    val accelerator by viewModel.acceleratorName.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        val density = LocalDensity.current

        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (col in 0..2) {
                        val cellIndex = row * 3 + col
                        val phrase = viewModel.phrases[cellIndex]
                        val isDwelling = dwellingCell == cellIndex

                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDwelling) Color(0xFF1A3A2A) else Color(0xFF1A1A1A)
                                )
                                .border(
                                    width = if (isDwelling) 2.dp else 1.dp,
                                    color = if (isDwelling) Color(0xFF10B981) else Color(0xFF2E2E2E),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = phrase,
                                    color = if (isDwelling) Color(0xFF10B981) else Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                if (isDwelling && dwellProgress > 0f) {
                                    Spacer(Modifier.height(8.dp))
                                    Canvas(modifier = Modifier.size(36.dp)) {
                                        drawArc(
                                            color = Color(0xFF10B981),
                                            startAngle = -90f,
                                            sweepAngle = 360f * dwellProgress,
                                            useCenter = false,
                                            style = Stroke(width = 4.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        gazePoint?.let { (sx, sy) ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0x8010B981),
                    radius = with(density) { 18.dp.toPx() },
                    center = Offset(sx, sy)
                )
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = with(density) { 6.dp.toPx() },
                    center = Offset(sx, sy)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${inferenceMs}ms  $accelerator",
                color = Color(0xFF10B981),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        lastSpoken?.let {
            Text(
                text = "\"$it\"",
                color = Color(0xFFE8E6E0),
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
