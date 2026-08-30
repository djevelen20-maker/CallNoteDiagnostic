package com.callnote.diagnostic

import java.io.File

class TranscriptionController {

    private val whisper = WhisperClient()
    private val storage = TranscriptionStorage()
    private val analyzer = AIAnalyzer()

    fun process(audio: File): TranscriptionResult {
        val text = whisper.transcribe(audio)
        storage.save(audio, text)
        val summary = analyzer.summarize(text)

        return TranscriptionResult(
            audioFile = audio,
            text = text,
            status = summary
        )
    }
}
