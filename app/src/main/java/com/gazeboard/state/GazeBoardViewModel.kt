package com.gazeboard.state

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazeboard.GazeBoardApplication
import com.gazeboard.audio.TtsManager
import com.gazeboard.calibration.CalibrationEngine
import com.gazeboard.camera.CameraManager
import com.gazeboard.ml.EyeGazeModel
import com.gazeboard.ml.GazeEstimator
import com.gazeboard.prediction.TriePredictor
import com.gazeboard.prediction.WordPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central ViewModel for GazeBoard.
 *
 * State machine:
 *   Calibrating(step 0-3) → QuickPhrases → Spelling ↔ WordSelection
 *
 * Dwell logic: 1 second on same quadrant triggers selection.
 * 500ms cooldown after selection prevents double-triggers.
 */
class GazeBoardViewModel : ViewModel() {

    private val _appState = MutableStateFlow<AppState>(AppState.Calibrating())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Telemetry for NPU badge
    private val _inferenceMs = MutableStateFlow(0L)
    val inferenceMs: StateFlow<Long> = _inferenceMs.asStateFlow()

    private val _accelerator = MutableStateFlow("—")
    val accelerator: StateFlow<String> = _accelerator.asStateFlow()

    private val _faceDetected = MutableStateFlow(false)
    val faceDetected: StateFlow<Boolean> = _faceDetected.asStateFlow()

    // Debug mode overlay
    private val _debugMode = MutableStateFlow(false)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    private val _faceDetectMs = MutableStateFlow(0L)
    val faceDetectMs: StateFlow<Long> = _faceDetectMs.asStateFlow()

    private val _rawPitch = MutableStateFlow(0f)
    val rawPitch: StateFlow<Float> = _rawPitch.asStateFlow()

    private val _rawYaw = MutableStateFlow(0f)
    val rawYaw: StateFlow<Float> = _rawYaw.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val frameTimestamps = ArrayDeque<Long>()

    fun toggleDebugMode() { _debugMode.update { !it } }

    // Dwell tracking
    private var currentDwellQuadrant: Int? = null
    private var dwellStartMs: Long = 0L
    private var inCooldown = false

    private lateinit var eyeGazeModel: EyeGazeModel
    private lateinit var gazeEstimator: GazeEstimator
    private lateinit var calibrationEngine: CalibrationEngine
    private lateinit var cameraManager: CameraManager
    private lateinit var ttsManager: TtsManager
    private lateinit var wordPredictor: WordPredictor

    // Quick phrases for home screen (quadrant 1-3)
    val quickPhrases = listOf("Yes", "No", "Help")

    // Calibration corner labels (shown in CalibrationScreen)
    val calibrationCorners = listOf("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right")

    // Surface provider race: composable may set it before CameraManager is ready
    private var pendingSurfaceProvider: Preview.SurfaceProvider? = null

    companion object {
        private const val TAG = "GazeBoard"
        private const val DWELL_MS = 1000L
        private const val COOLDOWN_MS = 500L
        private const val CALIB_DWELL_MS = 1500L
    }

    fun onCameraPermissionGranted(context: Context, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch(Dispatchers.IO) {
            ttsManager = (context.applicationContext as GazeBoardApplication).ttsManager
            wordPredictor = TriePredictor(context)
            calibrationEngine = CalibrationEngine(context)
            gazeEstimator = GazeEstimator()

            eyeGazeModel = EyeGazeModel(context)
            try {
                eyeGazeModel.load()
                Log.i(TAG, "EyeGaze model ready on ${eyeGazeModel.acceleratorName}")
            } catch (e: Exception) {
                Log.e(TAG, "NPU model load failed: ${e.message}")
                _appState.value = AppState.ModelLoadError(e.message ?: "NPU unavailable")
                return@launch
            }

            cameraManager = CameraManager(context, eyeGazeModel, gazeEstimator, calibrationEngine, this@GazeBoardViewModel)

            withContext(Dispatchers.Main) {
                pendingSurfaceProvider?.let { cameraManager.preview.setSurfaceProvider(it) }
                cameraManager.start(lifecycleOwner)
            }

            // If already calibrated from prefs, skip to QuickPhrases
            if (calibrationEngine.isCalibrated) {
                _appState.value = AppState.QuickPhrases()
                ttsManager.speak("GazeBoard ready. Look at Yes, No, or Help.")
            } else {
                ttsManager.speak("Look at the top-left corner.")
            }

            Log.i(TAG, "Camera pipeline started")
        }
    }

    /** Called by CameraManager every frame. */
    fun onGazeUpdate(result: GazeEstimator.GazeResult) {
        _inferenceMs.value = result.inferenceMs
        _accelerator.value = result.accelerator
        _faceDetected.value = result.quadrant != 0
        _faceDetectMs.value = result.faceDetectMs
        _rawPitch.value = result.rawPitch
        _rawYaw.value = result.rawYaw

        // Rolling FPS over last 10 frames
        val now = SystemClock.elapsedRealtime()
        frameTimestamps.addLast(now)
        if (frameTimestamps.size > 10) frameTimestamps.removeFirst()
        if (frameTimestamps.size >= 2) {
            val windowMs = frameTimestamps.last() - frameTimestamps.first()
            if (windowMs > 0) _fps.value = (frameTimestamps.size - 1) * 1000f / windowMs
        }

        val state = _appState.value

        when (state) {
            is AppState.Calibrating -> handleCalibrationGaze(result, state)
            is AppState.QuickPhrases, is AppState.Spelling, is AppState.WordSelection -> {
                handleDwellGaze(result.quadrant)
            }
            is AppState.ModelLoadError -> Unit
        }
    }

    private fun handleCalibrationGaze(result: GazeEstimator.GazeResult, state: AppState.Calibrating) {
        if (result.quadrant == 0) return  // no face

        calibrationEngine.accumulateSample(result.rawPitch, result.rawYaw)

        // Track dwell for calibration corner
        if (currentDwellQuadrant != state.step) {
            currentDwellQuadrant = state.step
            dwellStartMs = SystemClock.elapsedRealtime()
            _appState.update { state.copy(dwellProgress = 0f) }
            return
        }

        val elapsed = SystemClock.elapsedRealtime() - dwellStartMs
        val progress = (elapsed.toFloat() / CALIB_DWELL_MS).coerceIn(0f, 1f)
        _appState.update { state.copy(dwellProgress = progress) }

        if (elapsed >= CALIB_DWELL_MS) {
            val done = calibrationEngine.commitCorner()
            currentDwellQuadrant = null
            dwellStartMs = 0L
            if (done) {
                Log.i(TAG, "Calibration complete")
                ttsManager.speak("Calibration complete. Look at Yes, No, or Help.")
                _appState.value = AppState.QuickPhrases()
            } else {
                val nextStep = calibrationEngine.currentStep
                val cornerName = calibrationCorners.getOrElse(nextStep) { "corner" }
                ttsManager.speak("Look at the $cornerName corner.")
                _appState.update { AppState.Calibrating(step = nextStep, dwellProgress = 0f) }
            }
        }
    }

    private fun handleDwellGaze(quadrant: Int) {
        if (inCooldown) return

        if (quadrant == 0) {
            // Face lost — pause dwell but don't reset (brief blink tolerance)
            updateActiveQuadrant(null, 0f)
            return
        }

        if (quadrant != currentDwellQuadrant) {
            // Moved to new quadrant — restart dwell timer
            currentDwellQuadrant = quadrant
            dwellStartMs = SystemClock.elapsedRealtime()
            updateActiveQuadrant(quadrant, 0f)
            return
        }

        val elapsed = SystemClock.elapsedRealtime() - dwellStartMs
        val progress = (elapsed.toFloat() / DWELL_MS).coerceIn(0f, 1f)
        updateActiveQuadrant(quadrant, progress)

        if (elapsed >= DWELL_MS) {
            selectQuadrant(quadrant)
        }
    }

    private fun updateActiveQuadrant(quadrant: Int?, progress: Float) {
        _appState.update { state ->
            when (state) {
                is AppState.QuickPhrases   -> state.copy(activeQuadrant = quadrant, dwellProgress = progress)
                is AppState.Spelling       -> state.copy(activeQuadrant = quadrant, dwellProgress = progress)
                is AppState.WordSelection  -> state.copy(activeQuadrant = quadrant, dwellProgress = progress)
                else -> state
            }
        }
    }

    private fun selectQuadrant(quadrant: Int) {
        currentDwellQuadrant = null
        dwellStartMs = 0L
        inCooldown = true

        val state = _appState.value
        when (state) {
            is AppState.QuickPhrases -> handleQuickPhrasesSelection(quadrant, state)
            is AppState.Spelling     -> handleSpellingSelection(quadrant, state)
            is AppState.WordSelection -> handleWordSelection(quadrant, state)
            else -> Unit
        }

        viewModelScope.launch {
            delay(COOLDOWN_MS)
            inCooldown = false
        }
    }

    private fun handleQuickPhrasesSelection(quadrant: Int, state: AppState.QuickPhrases) {
        when (quadrant) {
            1, 2, 3 -> {
                val phrase = quickPhrases.getOrNull(quadrant - 1) ?: return
                Log.i(TAG, "Quick phrase: $phrase")
                ttsManager.speak(phrase)
                val newSentence = if (state.sentence.isEmpty()) phrase else "${state.sentence}. $phrase"
                _appState.value = state.copy(
                    activeQuadrant = quadrant, dwellProgress = 1f,
                    sentence = newSentence
                )
            }
            4 -> {
                Log.i(TAG, "Entering Spell Mode")
                ttsManager.speakFeedback("Spell mode")
                _appState.value = AppState.Spelling(sentence = state.sentence)
            }
        }
    }

    private fun handleSpellingSelection(quadrant: Int, state: AppState.Spelling) {
        val newSequence = state.gestureSequence + quadrant
        val groupLabel = groupLabel(quadrant)
        ttsManager.speakFeedback(groupLabel)
        Log.i(TAG, "Gesture: quadrant $quadrant, sequence=$newSequence")

        val candidates = wordPredictor.predict(newSequence)
        Log.i(TAG, "Candidates: $candidates")

        when {
            candidates.size == 1 -> {
                // Auto-select single candidate
                val word = candidates[0]
                confirmWord(word, state.sentence)
            }
            candidates.size in 2..3 -> {
                _appState.value = AppState.WordSelection(
                    candidates = candidates,
                    gestureSequence = newSequence,
                    sentence = state.sentence
                )
                ttsManager.speakFeedback(candidates.joinToString(", "))
            }
            candidates.isEmpty() -> {
                // No matches — backspace last gesture
                ttsManager.speakFeedback("No match, try again")
                _appState.value = state.copy(gestureSequence = state.gestureSequence)
            }
            else -> {
                _appState.value = state.copy(
                    gestureSequence = newSequence,
                    activeQuadrant = null,
                    dwellProgress = 0f
                )
            }
        }
    }

    private fun handleWordSelection(quadrant: Int, state: AppState.WordSelection) {
        when {
            quadrant == 4 -> {
                // Back to spelling
                Log.i(TAG, "Back to spelling from word selection")
                ttsManager.speakFeedback("Back")
                _appState.value = AppState.Spelling(
                    gestureSequence = state.gestureSequence,
                    sentence = state.sentence
                )
            }
            quadrant - 1 < state.candidates.size -> {
                val word = state.candidates[quadrant - 1]
                confirmWord(word, state.sentence)
            }
        }
    }

    private fun confirmWord(word: String, currentSentence: String) {
        Log.i(TAG, "Word confirmed: $word")
        ttsManager.speak(word)
        val newSentence = if (currentSentence.isEmpty()) word else "$currentSentence $word"
        _appState.value = AppState.Spelling(sentence = newSentence)
    }

    private fun groupLabel(quadrant: Int) = when (quadrant) {
        1 -> "A through G"
        2 -> "H through M"
        3 -> "N through S"
        4 -> "T through Z"
        else -> ""
    }

    fun setPreviewSurface(provider: Preview.SurfaceProvider) {
        pendingSurfaceProvider = provider
        if (::cameraManager.isInitialized) cameraManager.preview.setSurfaceProvider(provider)
    }

    fun startRecalibration() {
        calibrationEngine.reset()
        _appState.value = AppState.Calibrating()
        ttsManager.speak("Recalibrating. Look at the top-left corner.")
    }

    fun speakSentence() {
        val sentence = when (val s = _appState.value) {
            is AppState.QuickPhrases  -> s.sentence
            is AppState.Spelling      -> s.sentence
            is AppState.WordSelection -> s.sentence
            else -> ""
        }
        if (sentence.isNotBlank()) ttsManager.speak(sentence)
    }

    fun clearSentence() {
        _appState.update { state ->
            when (state) {
                is AppState.QuickPhrases  -> state.copy(sentence = "")
                is AppState.Spelling      -> state.copy(sentence = "")
                is AppState.WordSelection -> state.copy(sentence = "")
                else -> state
            }
        }
    }

    override fun onCleared() {
        if (::cameraManager.isInitialized) cameraManager.stop()
        if (::eyeGazeModel.isInitialized) eyeGazeModel.close()
        if (::gazeEstimator.isInitialized) gazeEstimator.close()
        super.onCleared()
    }
}
