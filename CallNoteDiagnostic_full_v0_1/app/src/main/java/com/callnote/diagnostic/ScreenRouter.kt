package com.callnote.diagnostic

sealed class Screen {
    object Records : Screen()
    object Analytics : Screen()
    object Settings : Screen()
    data class Conversation(val call: CallCard) : Screen()
}

class ScreenRouter {
    var current: Screen = Screen.Records
        private set

    fun open(screen: Screen) {
        current = screen
    }
}
