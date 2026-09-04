package com.callnote.diagnostic

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class RussianVoskTranscriber(private val context: Context) {
    private val lock = Any()
    private var model: Model? = null

    fun transcribe(wavFile: File, onUpdate: (String) -> Unit): String {
        val loadedModel = synchronized(lock) {
            model ?: loadModel(onUpdate).also { model = it }
        }
        onUpdate("Офлайн-модель обрабатывает аудио...")
        val recognizer = Recognizer(loadedModel, 16_000.0f)
        try {
            FileInputStream(wavFile).use { input ->
                val header = ByteArray(44)
                var headerRead = 0
                while (headerRead < header.size) {
                    val count = input.read(header, headerRead, header.size - headerRead)
                    if (count <= 0) break
                    headerRead += count
                }
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    recognizer.acceptWaveForm(buffer, count)
                }
            }
            return JSONObject(recognizer.finalResult).optString("text").trim()
        } finally {
            recognizer.close()
        }
    }

    fun close() {
        synchronized(lock) {
            model?.close()
            model = null
        }
    }

    private fun loadModel(onUpdate: (String) -> Unit): Model {
        val modelDir = File(context.filesDir, MODEL_DIR)
        val marker = File(modelDir, ".ready")
        if (!marker.exists()) {
            onUpdate("Подготавливаю русскую офлайн-модель...")
            if (modelDir.exists()) modelDir.deleteRecursively()
            modelDir.mkdirs()
            context.assets.open(MODEL_ZIP).use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val cleanName = entry.name.replace('\\', '/').removePrefix("/")
                        if (!cleanName.contains("..")) {
                            val target = File(modelDir, cleanName.substringAfter('/', cleanName))
                            if (entry.isDirectory) target.mkdirs()
                            else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output -> zip.copyTo(output) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            marker.writeText("ready")
        }
        return Model(modelDir.absolutePath)
    }

    private companion object {
        const val MODEL_ZIP = "vosk-model-small-ru-0.22.zip"
        const val MODEL_DIR = "vosk-model-small-ru-0.22"
    }
}

