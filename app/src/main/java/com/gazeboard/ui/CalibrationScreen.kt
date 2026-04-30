package com.gazeboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.GazeBoardViewModel

@Composable
fun CalibrationScreen(viewModel: GazeBoardViewModel) {
    val dwellProgress by viewModel.dwellProgress.collectAsState()
    val targetIndex = viewModel.calibEngine.currentTargetIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = "Look at the dot and hold your gaze",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        Text(
            text = "Target ${targetIndex + 1} of 4",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val target = viewModel.calibEngine.getTarget(targetIndex)
            if (target != null) {
                val density = LocalDensity.current
                val dotSize = 32.dp
                val x = with(density) { target.screenX.toDp() }
                val y = with(density) { target.screenY.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = x - dotSize / 2, y = y - dotSize / 2)
                        .size(dotSize)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.White, radius = size.minDimension / 2f)
                        drawArc(
                            color = Color(0xFF10B981),
                            startAngle = -90f,
                            sweepAngle = 360f * dwellProgress,
                            useCenter = true,
                            size = size
                        )
                        drawCircle(color = Color.White, radius = size.minDimension / 4f)
                    }
                }
            }
        }
    }
}
