package com.callnote.diagnostic

import android.Manifest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)

        setContent {
            var result by remember { mutableStateOf("Готов к тесту") }
            var recording by remember { mutableStateOf(false) }

            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                ) {
                    Text("CallNote Diagnostic v0.3")
                    Text("\nHuawei / Android тест")

                    Button(onClick = {
                        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                        result = """
Модель: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE}
SDK: ${Build.VERSION.SDK_INT}
Audio mode: ${audio.mode}

Микрофон доступен: да
""".trimIndent()
                    }) {
                        Text("Проверить устройство")
                    }

                    Button(onClick = {
                        if (!recording) {
                            try {
                                file = File(getExternalFilesDir(null), "test_audio.m4a")
                                recorder = MediaRecorder(this@MainActivity).apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(file)
                                    prepare()
                                    start()
                                }
                                recording = true
                                result = "Идёт запись микрофона..."
                            } catch (e: Exception) {
                                result = "Ошибка: ${e.message}"
                            }
                        } else {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            recording = false
                            result = "Запись сохранена:\n${file?.absolutePath}"
                        }
                    }) {
                        Text(if (recording) "Остановить запись" else "Записать аудио"
                    )
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
