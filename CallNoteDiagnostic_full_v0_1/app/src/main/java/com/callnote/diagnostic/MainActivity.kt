package com.callnote.diagnostic

import android.Manifest
import android.media.AudioManager
import android.media.MediaPlayer
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
    private var player: MediaPlayer? = null
    private var file: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)

        setContent {
            var result by remember { mutableStateOf("Готов к тесту") }
            var recording by remember { mutableStateOf(false) }

            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("CallNote Diagnostic v0.4")
                    Text("\nТест аудио и записи")

                    Button(onClick = {
                        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                        result = """
Модель: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE}
SDK: ${Build.VERSION.SDK_INT}
Audio mode: ${audio.mode}

Готовность микрофона: да
""".trimIndent()
                    }) {
                        Text("Проверить устройство")
                    }

                    Button(onClick = {
                        if (!recording) {
                            file = File(getExternalFilesDir(null), "CallNote_test.m4a")
                            recorder = MediaRecorder(this@MainActivity).apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setOutputFile(file)
                                prepare()
                                start()
                            }
                            recording = true
                            result = "Идёт запись..."
                        } else {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            recording = false
                            result = "Файл сохранён:\n${file?.name}"
                        }
                    }) {
                        Text(if (recording) "Остановить" else "Записать заметку")
                    }

                    Button(onClick = {
                        try {
                            player?.release()
                            player = MediaPlayer().apply {
                                setDataSource(file?.absolutePath)
                                prepare()
                                start()
                            }
                            result = "Воспроизведение записи"
                        } catch (e: Exception) {
                            result = "Сначала сделайте запись"
                        }
                    }) {
                        Text("Прослушать запись")
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
