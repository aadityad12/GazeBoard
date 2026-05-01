package com.gazeboard.prediction

import android.content.Context
import android.util.Log

/**
 * Dictionary-based word predictor.
 *
 * Loads words.txt from assets (5000 most common English words, frequency-sorted).
 * Precomputes gesture codes for every word at init time. predict() is O(n) filter
 * over the precomputed list — fast enough for 5000 words on every frame.
 */
class TriePredictor(context: Context) : WordPredictor {

    private val letterToGroup = mapOf(
        'a' to 1, 'b' to 1, 'c' to 1, 'd' to 1, 'e' to 1, 'f' to 1, 'g' to 1,
        'h' to 2, 'i' to 2, 'j' to 2, 'k' to 2, 'l' to 2, 'm' to 2,
        'n' to 3, 'o' to 3, 'p' to 3, 'q' to 3, 'r' to 3, 's' to 3,
        't' to 4, 'u' to 4, 'v' to 4, 'w' to 4, 'x' to 4, 'y' to 4, 'z' to 4
    )

    // Precomputed: (word, gestureCode) pairs in frequency order
    private val dictionary: List<Pair<String, List<Int>>> = loadDictionary(context)

    companion object {
        private const val TAG = "GazeBoard"
        private const val MAX_RESULTS = 5
    }

    override fun predict(gestureSequence: List<Int>): List<String> {
        if (gestureSequence.isEmpty()) return emptyList()
        return dictionary
            .filter { (_, code) ->
                code.size >= gestureSequence.size &&
                code.subList(0, gestureSequence.size) == gestureSequence
            }
            .take(MAX_RESULTS)
            .map { it.first }
    }

    private fun loadDictionary(context: Context): List<Pair<String, List<Int>>> {
        return try {
            context.assets.open("words.txt").bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } }
                    .map { word -> word to wordToGestureCode(word) }
                    .filter { (_, code) -> code.isNotEmpty() }
                    .toList()
            }.also { Log.i(TAG, "TriePredictor loaded ${it.size} words") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load words.txt: ${e.message}")
            emptyList()
        }
    }

    private fun wordToGestureCode(word: String): List<Int> {
        return word.mapNotNull { letterToGroup[it] }
    }
}
