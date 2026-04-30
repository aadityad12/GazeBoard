package com.gazeboard.state

sealed class AppState {
    object Initializing : AppState()
    object NeedsPermission : AppState()
    object Calibrating : AppState()
    object Board : AppState()
    data class Error(val message: String) : AppState()
}
