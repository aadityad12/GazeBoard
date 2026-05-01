package com.gazeboard.state

sealed class AppState {
    /** Four-corner calibration. step = 0..3 (TL, TR, BL, BR). */
    data class Calibrating(
        val step: Int = 0,
        val dwellProgress: Float = 0f
    ) : AppState()

    /** Home screen — Yes / No / Help / More► */
    data class QuickPhrases(
        val activeQuadrant: Int? = null,
        val dwellProgress: Float = 0f,
        val sentence: String = ""
    ) : AppState()

    /** Spell mode — user selects letter groups to narrow word candidates. */
    data class Spelling(
        val gestureSequence: List<Int> = emptyList(),
        val activeQuadrant: Int? = null,
        val dwellProgress: Float = 0f,
        val sentence: String = ""
    ) : AppState()

    /** Spell mode — 3 or fewer candidates, displayed in quadrants for selection. */
    data class WordSelection(
        val candidates: List<String>,
        val gestureSequence: List<Int>,
        val activeQuadrant: Int? = null,
        val dwellProgress: Float = 0f,
        val sentence: String = ""
    ) : AppState()
}
