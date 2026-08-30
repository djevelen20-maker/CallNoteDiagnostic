package com.callnote.diagnostic

import java.io.File

/**
 * Клиент для подключения Whisper.
 *
 * Поток:
 * File(.m4a) -> Whisper -> текст
 *
 * Здесь будет добавлен реальный HTTP запрос
 * после выбора способа подключения:
 * 1. OpenAI API
 * 2. Собственный сервер Whisper
 * 3. Локальный Whisper.cpp
 */
class WhisperClient {

    fun transcribe(file: File): String {
        return "Ожидание подключения Whisper: ${file.name}"
    }
}
