package com.gazeboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.gazeboard.state.AppState
import com.gazeboard.state.GazeBoardViewModel
import com.gazeboard.ui.BoardScreen
import com.gazeboard.ui.CalibrationScreen
import com.gazeboard.ui.theme.GazeBoardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GazeBoardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GazeBoardTheme {
                val appState by viewModel.appState.collectAsState()
                val gazeState by viewModel.gazeState.collectAsState()

                // Camera permission gate
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) viewModel.onCameraPermissionGranted(this@MainActivity, this@MainActivity)
                }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.onCameraPermissionGranted(this@MainActivity, this@MainActivity)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                // Navigation driven entirely by AppState
                when (appState) {
                    is AppState.Calibrating -> CalibrationScreen(
                        gazeState = gazeState,
                        onCalibrationPointCaptured = viewModel::onCalibrationPointCaptured,
                        onCalibrationComplete = viewModel::onCalibrationComplete
                    )
                    else -> BoardScreen(
                        gazeState = gazeState,
                        appState = appState,
                        phrases = viewModel.phrases,
                        onRecalibrate = viewModel::startCalibration,
                        onPreviewSurfaceReady = viewModel::setPreviewSurface
                    )
                }
            }
        }
    }
}
