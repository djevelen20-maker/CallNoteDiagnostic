package com.callnote.diagnostic

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class GigaChatTranscriber {
    private val oauthUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    private val apiBaseUrl = "https://api.giga.chat/v1"

    fun transcribe(file: File, authKey: String, onProgress: (String) -> Unit = {}): String {
        require(file.exists()) { "Файл записи не найден" }
        require(file.length() <= 35L * 1024L * 1024L) {
            "Giga принимает аудиофайлы размером до 35 МБ"
        }
        require(authKey.isNotBlank()) { "Добавьте ключ GigaChat в настройках" }

        onProgress("Получаю доступ к Giga...")
        val accessToken = requestAccessToken(authKey)
        onProgress("Загружаю запись в Giga...")
        val fileId = uploadFile(file, accessToken)
        return try {
            onProgress("Giga расшифровывает запись...")
            requestTranscription(fileId, accessToken)
        } finally {
            deleteFile(fileId, accessToken)
        }
    }

    private fun requestAccessToken(authKey: String): String {
        val connection = openConnection(oauthUrl, "POST")
        connection.setRequestProperty("RqUID", UUID.randomUUID().toString())
        connection.setRequestProperty("Authorization", "Basic ${authKey.removePrefix("Basic ").trim()}")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write("scope=${URLEncoder.encode("GIGACHAT_API_PERS", "UTF-8")}".toByteArray())
        }
        val body = readResponse(connection)
        return JSONObject(body).optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Giga не вернул токен доступа")
    }

    private fun uploadFile(file: File, accessToken: String): String {
        val boundary = "CallNote-${UUID.randomUUID()}"
        val connection = openConnection("$apiBaseUrl/files", "POST")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n".toByteArray())
            output.write("general\r\n".toByteArray())
            output.write("--$boundary\r\n".toByteArray())
            output.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n".toByteArray()
            )
            output.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
            file.inputStream().use { input -> input.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val body = readResponse(connection)
        return JSONObject(body).optString("id").takeIf { it.isNotBlank() }
            ?: error("Giga не вернул идентификатор записи")
    }

    private fun requestTranscription(fileId: String, accessToken: String): String {
        val message = JSONObject()
            .put("role", "user")
            .put(
                "content",
                "Дословно расшифруй эту аудиозапись на русском языке. " +
                    "Сохрани смысл и порядок слов, расставь знаки препинания. " +
                    "Верни только готовую расшифровку без пояснений и без краткого пересказа."
            )
            .put("attachments", JSONArray().put(fileId))
        val payload = JSONObject()
            .put("model", "GigaChat")
            .put("function_call", "auto")
            .put("messages", JSONArray().put(message))
            .put("temperature", 0.1)

        val connection = openConnection("$apiBaseUrl/chat/completions", "POST")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.outputStream.use { output -> output.write(payload.toString().toByteArray()) }

        val body = readResponse(connection)
        val content = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
        return content.ifBlank { error("Giga вернул пустую расшифровку") }
    }

    private fun deleteFile(fileId: String, accessToken: String) {
        runCatching {
            val connection = openConnection("$apiBaseUrl/files/$fileId/delete", "POST")
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            readResponse(connection)
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 180_000
            useCaches = false
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("Giga: HTTP $code ${body.take(500)}")
        }
        return body
    }
}

