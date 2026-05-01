package com.gazeboard.prediction

/**
 * Interface for word prediction from a sequence of quadrant gestures.
 *
 * Letter-group layout:
 *   Quadrant 1 = A B C D E F G
 *   Quadrant 2 = H I J K L M
 *   Quadrant 3 = N O P Q R S
 *   Quadrant 4 = T U V W X Y Z
 *
 * [predict] takes the current gesture sequence (list of quadrant numbers 1-4)
 * and returns candidate words whose gesture code starts with that prefix,
 * sorted by frequency (most common first), limited to 5 results.
 */
interface WordPredictor {
    fun predict(gestureSequence: List<Int>): List<String>
}
