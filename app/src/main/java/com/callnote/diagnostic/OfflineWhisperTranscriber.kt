package com.callnote.diagnostic

import android.content.Context
import com.whispertflite.asr.Whisper
import java.io.File

class OfflineWhisperTranscriber(private val context: Context) {
    private var whisper: Whisper? = null

    fun transcribe(wavFile: File, onUpdate: (String) -> Unit, onResult: (String) -> Unit) {
        val engine = whisper ?: Whisper(context).also {
            val model = copyAsset("whisper-tiny.tflite")
            val vocab = copyAsset("filters_vocab_multilingual.bin")
            it.loadModel(model, vocab, true)
            whisper = it
        }

        engine.setListener(object : Whisper.WhisperListener {
            override fun onUpdateReceived(message: String) {
                // Whisper sends "Processing done" after the result. Ignoring it keeps the
                // more useful final state set by MainActivity (ready or no speech found).
                if (!message.contains("Processing done", ignoreCase = true)) {
                    onUpdate(message.toRussianStatus())
                }
            }

            override fun onResultReceived(result: String) {
                onResult(cleanWhisperTranscript(result))
            }
        })
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
            context.assets.open(name).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return file
    }

    private fun String.toRussianStatus(): String = when {
        contains("Processing", ignoreCase = true) -> "Whisper анализирует аудио..."
        contains("not found", ignoreCase = true) -> "Аудиофайл не найден"
        contains("not initialized", ignoreCase = true) -> "Модель Whisper не загрузилась"
        contains("failed", ignoreCase = true) -> "Ошибка обработки аудио"
        else -> this
    }
}

internal fun cleanWhisperTranscript(raw: String): String {
    return raw
        .replace(Regex("""<\|[^|>]+\|>"""), " ")
        .replace(
            Regex(
                """\[(?:_extra_token_\d+|_[A-Z]+_|_TT_\d+)\]""",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        .replace(Regex("""\s+"""), " ")
        .trim()
}
