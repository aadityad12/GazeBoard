package com.gazeboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.GazeBoardViewModel
import com.gazeboard.ui.components.CameraPreviewPip

@Composable
fun CalibrationScreen(viewModel: GazeBoardViewModel) {
    val dwellProgress    by viewModel.dwellProgress.collectAsState()
    val calibTargetIndex by viewModel.calibTargetIndex.collectAsState()   // reactive — fixes Bug 2
    val faceDetected     by viewModel.faceDetected.collectAsState()
    val eyeCenterNorm    by viewModel.eyeCenterNorm.collectAsState()

    val cornerLabels = listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Instructions — top center
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Eye Calibration",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Look at the dot and hold your gaze",
                color = Color(0xFFAAAAAA),
                fontSize = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Target ${calibTargetIndex + 1} of 4  ·  ${cornerLabels.getOrElse(calibTargetIndex) { "" }}",
                color = Color(0xFF10B981),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (faceDetected) "● Face detected" else "◌  No face — center your face in view",
                color = if (faceDetected) Color(0xFF10B981) else Color(0xFFEF4444),
                fontSize = 12.sp
            )
        }

        // Calibration target dot with progress arc
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val target = viewModel.calibEngine.getTarget(calibTargetIndex)
            if (target != null) {
                val density = LocalDensity.current
                val dotSize = 52.dp
                val x = with(density) { target.screenX.toDp() }
                val y = with(density) { target.screenY.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = x - dotSize / 2, y = y - dotSize / 2)
                        .size(dotSize)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Dark background ring
                        drawCircle(
                            color = Color(0xFF2A2A2A),
                            radius = size.minDimension / 2f
                        )
                        // Green progress fill
                        drawArc(
                            color = Color(0xFF10B981),
                            startAngle = -90f,
                            sweepAngle = 360f * dwellProgress,
                            useCenter = true,
                            size = size
                        )
                        // White center dot — always visible so user knows where to look
                        drawCircle(
                            color = Color.White,
                            radius = size.minDimension / 4f
                        )
                    }
                }
            }
        }

        // Camera PiP — bottom right, so user can verify face is in frame
        CameraPreviewPip(
            preview      = viewModel.cameraPreview,
            eyeCenterNorm = eyeCenterNorm,
            faceDetected  = faceDetected,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
