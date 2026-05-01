package com.gazeboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.gazeboard.state.AppState
import com.gazeboard.state.GazeBoardViewModel
import com.gazeboard.ui.CalibrationScreen
import com.gazeboard.ui.ModelErrorScreen
import com.gazeboard.ui.QuickPhrasesScreen
import com.gazeboard.ui.SpellScreen
import com.gazeboard.ui.theme.GazeBoardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GazeBoardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GazeBoardTheme {
                val appState     by viewModel.appState.collectAsState()
                val inferenceMs  by viewModel.inferenceMs.collectAsState()
                val accelerator  by viewModel.accelerator.collectAsState()
                val faceDetected by viewModel.faceDetected.collectAsState()
                val debugMode    by viewModel.debugMode.collectAsState()
                val faceDetectMs by viewModel.faceDetectMs.collectAsState()
                val rawPitch     by viewModel.rawPitch.collectAsState()
                val rawYaw       by viewModel.rawYaw.collectAsState()
                val fps          by viewModel.fps.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) viewModel.onCameraPermissionGranted(this@MainActivity, this@MainActivity)
                }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                        viewModel.onCameraPermissionGranted(this@MainActivity, this@MainActivity)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                when (val state = appState) {
                    is AppState.Calibrating -> CalibrationScreen(
                        state = state,
                        faceDetected = faceDetected,
                        debugMode = debugMode,
                        onToggleDebug = { viewModel.toggleDebugMode() },
                        fps = fps,
                        inferenceMs = inferenceMs,
                        faceDetectMs = faceDetectMs,
                        rawPitch = rawPitch,
                        rawYaw = rawYaw,
                        accelerator = accelerator
                    )
                    is AppState.QuickPhrases -> QuickPhrasesScreen(
                        state = state,
                        accelerator = accelerator,
                        inferenceMs = inferenceMs,
                        faceDetected = faceDetected,
                        onRecalibrate = { viewModel.startRecalibration() },
                        debugMode = debugMode,
                        onToggleDebug = { viewModel.toggleDebugMode() },
                        fps = fps,
                        faceDetectMs = faceDetectMs,
                        rawPitch = rawPitch,
                        rawYaw = rawYaw
                    )
                    is AppState.Spelling, is AppState.WordSelection -> SpellScreen(
                        state = state,
                        accelerator = accelerator,
                        inferenceMs = inferenceMs,
                        faceDetected = faceDetected,
                        onRecalibrate = { viewModel.startRecalibration() },
                        debugMode = debugMode,
                        onToggleDebug = { viewModel.toggleDebugMode() },
                        fps = fps,
                        faceDetectMs = faceDetectMs,
                        rawPitch = rawPitch,
                        rawYaw = rawYaw
                    )
                    is AppState.ModelLoadError -> ModelErrorScreen(
                        state = state,
                        onRetry = { viewModel.onCameraPermissionGranted(this@MainActivity, this@MainActivity) }
                    )
                }
            }
        }
    }
}
