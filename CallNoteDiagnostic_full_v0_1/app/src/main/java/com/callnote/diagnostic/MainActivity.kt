package com.callnote.diagnostic

import android.Manifest
import android.media.MediaPlayer
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
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)

        setContent {
            var result by remember { mutableStateOf("Готово") }
            var selectedFile by remember { mutableStateOf<File?>(null) }

            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("CallNote AI v1.0")
                    Text("\nАудио → Текст → AI анализ")

                    Button(onClick = {
                        val files = getExternalFilesDir(null)?.listFiles()?.filter { it.name.endsWith(".m4a") }
                        selectedFile = files?.firstOrNull()
                        result = if (selectedFile == null) "Записей нет" else "Выбрано: ${selectedFile?.name}"
                    }) {
                        Text("Выбрать запись")
                    }

                    Button(onClick = {
                        result = selectedFile?.let {
                            "Отправка в Whisper для расшифровки: ${it.name}"
                        } ?: "Сначала выберите запись"
                    }) {
                        Text("Расшифровать")
                    }

                    Button(onClick = {
                        try {
                            player?.release()
                            player = MediaPlayer().apply {
                                setDataSource(selectedFile?.absolutePath)
                                prepare()
                                start()
                            }
                            result = "Прослушивание"
                        } catch (e: Exception) {
                            result = "Нет записи"
                        }
                    }) {
                        Text("Прослушать")
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
