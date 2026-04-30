package com.gazeboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazeboard.state.GazeBoardViewModel
import com.gazeboard.ui.components.CameraPreviewPip
import com.gazeboard.ui.components.GazeCursor
import com.gazeboard.ui.components.NpuBadge
import com.gazeboard.ui.components.PhraseCell

@Composable
fun BoardScreen(viewModel: GazeBoardViewModel) {
    val gazePoint    by viewModel.gazePoint.collectAsState()
    val dwellingCell by viewModel.dwellingCellIndex.collectAsState()
    val dwellProgress by viewModel.dwellProgress.collectAsState()
    val lastSpoken   by viewModel.lastSpokenPhrase.collectAsState()
    val inferenceMs  by viewModel.inferenceMs.collectAsState()
    val accelerator  by viewModel.acceleratorName.collectAsState()
    val faceDetected by viewModel.faceDetected.collectAsState()
    val eyeCenterNorm by viewModel.eyeCenterNorm.collectAsState()
    val faceDetectMs by viewModel.faceDetectMs.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        // 2×3 phrase grid
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (col in 0..2) {
                        val cellIndex = row * 3 + col
                        PhraseCell(
                            phrase      = viewModel.phrases[cellIndex],
                            isHovered   = dwellingCell == cellIndex,
                            isSelected  = false,
                            dwellProgress = if (dwellingCell == cellIndex) dwellProgress else 0f,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        // Gaze cursor — full-screen overlay, pixel coordinates
        GazeCursor(
            gazePoint = gazePoint,
            modifier  = Modifier.fillMaxSize()
        )

        // NPU badge + pipeline latency stats — top left
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            NpuBadge(
                accelerator = accelerator,
                inferenceMs = inferenceMs
            )
            if (faceDetectMs > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Face: ${faceDetectMs}ms",
                    color = Color(0xFF666666),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Camera PiP + face detection indicator — top right
        CameraPreviewPip(
            preview       = viewModel.cameraPreview,
            eyeCenterNorm = eyeCenterNorm,
            faceDetected  = faceDetected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // Last spoken phrase — bottom center
        lastSpoken?.let {
            Text(
                text = "\"$it\"",
                color = Color(0xFFE8E6E0),
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Recalibrate button — bottom right
        Button(
            onClick = { viewModel.startCalibration() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1C)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Recalibrate",
                color = Color(0xFF10B981),
                fontSize = 12.sp
            )
        }
    }
}
