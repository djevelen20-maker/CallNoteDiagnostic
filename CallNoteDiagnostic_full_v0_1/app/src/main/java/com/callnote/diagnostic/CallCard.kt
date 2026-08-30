package com.callnote.diagnostic

import java.io.File

/**
 * Карточка разговора.
 * Хранит аудио, расшифровку и результат AI анализа.
 */
data class CallCard(
    val audio: File,
    val date: String,
    val duration: String = "",
    val transcript: String = "",
    val summary: String = "",
    val tasks: List<String> = emptyList()
)
