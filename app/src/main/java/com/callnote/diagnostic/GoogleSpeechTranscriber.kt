package com.callnote.diagnostic

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Sends a prepared 16 kHz mono WAV to Google Cloud Speech-to-Text. */
class GoogleSpeechTranscriber {
    fun transcribe(wavFile: File, apiKey: String, onProgress: (String) -> Unit): String {
        require(wavFile.exists() && wavFile.length() > 44) { "Аудиофайл пустой или не найден" }
        require(apiKey.isNotBlank()) { "Добавьте ключ Google Cloud Speech-to-Text" }
        require(wavFile.length() <= MAX_INLINE_BYTES) {
            "Файл слишком большой для быстрой расшифровки Google: сократите запись"
        }

        onProgress("Отправляю запись в Google Speech-to-Text...")
        val audio = Base64.encodeToString(wavFile.readBytes(), Base64.NO_WRAP)
        val request = JSONObject()
            .put("config", JSONObject()
                .put("encoding", "LINEAR16")
                .put("sampleRateHertz", 16_000)
                .put("languageCode", "ru-RU")
                .put("model", "latest_long")
                .put("enableAutomaticPunctuation", true))
            .put("audio", JSONObject().put("content", audio))

        val encodedKey = URLEncoder.encode(apiKey.trim(), Charsets.UTF_8.name())
        val connection = (URL("https://speech.googleapis.com/v1/speech:recognize?key=$encodedKey")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                val message = runCatching { JSONObject(responseText).optJSONObject("error")?.optString("message") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                error(message ?: "Google вернул ошибку HTTP $responseCode")
            }

            val results = JSONObject(responseText).optJSONArray("results") ?: return ""
            return buildString {
                for (index in 0 until results.length()) {
                    val transcript = results.optJSONObject(index)
                        ?.optJSONArray("alternatives")
                        ?.optJSONObject(0)
                        ?.optString("transcript")
                        ?.trim()
                        .orEmpty()
                    if (transcript.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append(transcript)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_INLINE_BYTES = 10L * 1024L * 1024L
    }
}

