package com.gazeboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.gazeboard.state.AppState
import com.gazeboard.state.GazeBoardViewModel
import com.gazeboard.ui.BoardScreen
import com.gazeboard.ui.CalibrationScreen
import com.gazeboard.ui.theme.GazeBoardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GazeBoardViewModel by viewModels()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onCameraPermissionGranted(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GazeBoardTheme {
                val appState by viewModel.appState.collectAsState()

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val w = with(density) { maxWidth.toPx() }
                    val h = with(density) { maxHeight.toPx() }

                    LaunchedEffect(w, h) {
                        viewModel.setScreenSize(w, h)
                    }

                    when (val state = appState) {
                        AppState.Initializing -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF10B981))
                                    Spacer(Modifier.height(16.dp))
                                    Text("Loading model...", color = Color.White)
                                }
                            }
                        }

                        AppState.NeedsPermission -> {
                            LaunchedEffect(Unit) {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.onCameraPermissionGranted(this@MainActivity)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Camera permission required", color = Color.White)
                            }
                        }

                        AppState.Calibrating -> CalibrationScreen(viewModel)
                        AppState.Board -> BoardScreen(viewModel)

                        is AppState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(state.message, color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
