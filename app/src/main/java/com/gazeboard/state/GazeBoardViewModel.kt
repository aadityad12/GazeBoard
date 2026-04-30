package com.gazeboard.state

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazeboard.audio.TtsManager
import com.gazeboard.calibration.CalibrationEngine
import com.gazeboard.camera.CameraManager
import com.gazeboard.ml.FaceLandmarkModel
import com.gazeboard.ml.GazeEstimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Data published to UI every frame. All fields are safe to read on the main thread. */
data class GazeState(
    val gazePoint: Offset? = null,        // calibrated gaze in screen px; null = no face
    val hoveredCell: Int? = null,         // 0–5 (row-major), null = between cells
    val dwellProgress: Float = 0f,        // 0.0–1.0
    val inferenceMs: Long = 0L,
    val accelerator: String = "—",        // "NPU", "GPU", "CPU", or "—" before first frame
    val isBlinking: Boolean = false,
    val faceDetected: Boolean = false,
    val distanceWarning: String? = null   // "Move closer", "Move back", or null
)

class GazeBoardViewModel : ViewModel() {

    private val _appState = MutableStateFlow<AppState>(AppState.Calibrating)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _gazeState = MutableStateFlow(GazeState())
    val gazeState: StateFlow<GazeState> = _gazeState.asStateFlow()

    // Default phrase set — index matches cell layout (row-major, top-left = 0)
    val phrases = listOf("Yes", "No", "Help", "Thank you", "I need water", "Call nurse")

    private val DWELL_THRESHOLD_MS = 1500L
    private val COOLDOWN_MS = 500L

    private var currentDwellCell: Int? = null
    private var dwellStartMs: Long = 0L

    private lateinit var faceLandmarkModel: FaceLandmarkModel
    private lateinit var gazeEstimator: GazeEstimator
    private lateinit var calibrationEngine: CalibrationEngine
    private lateinit var cameraManager: CameraManager
    private lateinit var ttsManager: TtsManager

    fun onCameraPermissionGranted(context: Context) {
        // TODO(Person A): Initialize FaceLandmarkModel and start NPU inference
        // TODO(Person B): Initialize CameraManager and start frame pipeline
        // TODO(Person C): TtsManager is initialized in Application class

        // TODO: Replace stub initialization with real objects:
        // faceLandmarkModel = FaceLandmarkModel(context)
        // faceLandmarkModel.load()
        // gazeEstimator = GazeEstimator()
        // calibrationEngine = CalibrationEngine()
        // cameraManager = CameraManager(context, faceLandmarkModel, gazeEstimator, this)
        // cameraManager.start(lifecycleOwner)

        Log.i(TAG, "Camera permission granted — initializing pipeline")
    }

    /** Called by CameraManager each frame with normalized raw gaze (pre-calibration). */
    fun onGazeUpdate(
        rawGaze: PointF?,
        inferenceMs: Long,
        accelerator: String,
        isBlinking: Boolean,
        distanceWarning: String?
    ) {
        if (rawGaze == null) {
            _gazeState.update {
                it.copy(
                    faceDetected = false,
                    gazePoint = null,
                    hoveredCell = null,
                    dwellProgress = 0f,
                    inferenceMs = inferenceMs,
                    accelerator = accelerator
                )
            }
            resetDwell()
            return
        }

        val calibratedGaze = calibrationEngine.applyCalibration(rawGaze)

        // TODO(Person B): Convert calibratedGaze (normalized [0,1]) to screen pixels
        // using the screen dimensions available from the ViewModel scope
        val screenGaze = Offset(calibratedGaze.x, calibratedGaze.y)

        val cellIndex = mapToCell(calibratedGaze)

        _gazeState.update {
            it.copy(
                gazePoint = screenGaze,
                hoveredCell = cellIndex,
                inferenceMs = inferenceMs,
                accelerator = accelerator,
                isBlinking = isBlinking,
                faceDetected = true,
                distanceWarning = distanceWarning
            )
        }

        if (!isBlinking && _appState.value == AppState.Tracking) {
            onCellHovered(cellIndex)
        }
    }

    /** Called by CalibrationScreen when user successfully dwells on a calibration target. */
    fun onCalibrationPointCaptured(screenPoint: PointF, rawGaze: PointF) {
        // TODO(Person B): calibrationEngine.addCalibrationPoint(screenPoint, rawGaze)
    }

    fun onCalibrationComplete() {
        // TODO(Person B): calibrationEngine.computeAffineTransform()
        _appState.value = AppState.Tracking
        Log.i(TAG, "Calibration complete — entering tracking mode")
    }

    fun startCalibration() {
        // TODO(Person B): calibrationEngine.reset()
        _appState.value = AppState.Calibrating
    }

    private fun onCellHovered(cellIndex: Int?) {
        when {
            cellIndex == null -> resetDwell()
            cellIndex != currentDwellCell -> {
                currentDwellCell = cellIndex
                dwellStartMs = SystemClock.elapsedRealtime()
                _gazeState.update { it.copy(dwellProgress = 0f) }
            }
            else -> {
                val elapsed = SystemClock.elapsedRealtime() - dwellStartMs
                val progress = (elapsed.toFloat() / DWELL_THRESHOLD_MS).coerceIn(0f, 1f)
                _gazeState.update { it.copy(dwellProgress = progress) }

                if (elapsed >= DWELL_THRESHOLD_MS) {
                    selectCell(cellIndex)
                }
            }
        }
    }

    private fun selectCell(index: Int) {
        val phrase = phrases.getOrNull(index) ?: return
        Log.i(TAG, "Cell $index selected: \"$phrase\"")

        // TODO(Person C): ttsManager.speak(phrase)
        _appState.value = AppState.Selected(index)
        resetDwell()

        viewModelScope.launch {
            delay(COOLDOWN_MS)
            if (_appState.value is AppState.Selected) {
                _appState.value = AppState.Tracking
            }
        }
    }

    private fun resetDwell() {
        currentDwellCell = null
        dwellStartMs = 0L
        _gazeState.update { it.copy(dwellProgress = 0f) }
    }

    /**
     * Maps a calibrated gaze point (normalized [0,1]) to a cell index in the 2×3 grid.
     * Grid layout (row-major):
     *   0 | 1 | 2
     *   --|---|--
     *   3 | 4 | 5
     *
     * Returns null if gaze is outside the grid bounds.
     */
    private fun mapToCell(gaze: PointF): Int? {
        val col = (gaze.x * 3).toInt().coerceIn(0, 2)
        val row = (gaze.y * 2).toInt().coerceIn(0, 1)
        return row * 3 + col
    }

    override fun onCleared() {
        // TODO: cameraManager.stop()
        // TODO: faceLandmarkModel.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "GazeBoard"
    }
}
