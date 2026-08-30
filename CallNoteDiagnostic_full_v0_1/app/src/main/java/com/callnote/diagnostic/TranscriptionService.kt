package com.callnote.diagnostic

import java.io.File

/**
 * Подготовка модуля транскрибации.
 * Следующий этап: подключение Whisper API
 * или локальной модели Whisper.cpp.
 */
class TranscriptionService {

    fun transcribe(audioFile: File): String {
        // Здесь будет вызов Whisper
        // Вход: аудиофайл m4a
        // Выход: текст разговора
        return "Транскрибация будет выполнена через Whisper: ${audioFile.name}"
    }
}
