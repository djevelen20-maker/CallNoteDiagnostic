package com.callnote.diagnostic

import android.content.Context
import com.callnote.diagnostic.whisper.asr.Whisper
import java.io.File

class OfflineWhisperTranscriber(private val context: Context) {
    private var whisper: Whisper? = null

    fun transcribe(wavFile: File, onUpdate: (String) -> Unit, onResult: (String) -> Unit) {
        val engine = whisper ?: Whisper(context).also { whisper = it }
        val model = copyAsset("whisper-tiny.tflite")
        val vocab = copyAsset("filters_vocab_multilingual.bin")
        engine.setListener(object : Whisper.WhisperListener {
            override fun onUpdateReceived(message: String) = onUpdate(message)
            override fun onResultReceived(result: String) = onResult(result.orEmpty())
        })
        engine.loadModel(model, vocab, true)
        engine.setFilePath(wavFile.absolutePath)
        engine.setAction(Whisper.ACTION_TRANSCRIBE)
        engine.start()
    }

    fun close() {
        whisper?.stop()
        whisper?.unloadModel()
        whisper = null
    }

    private fun copyAsset(name: String): File {
        val file = File(context.filesDir, name)
        if (!file.exists() || file.length() == 0L) {
            context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        return file
    }
}

