package com.callnote.diagnostic

import android.Manifest
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Column(
                    Modifier.fillMaxSize()
                        .background(Color(0xFF111218))
                        .padding(20.dp)
                ) {
                    Text("CallNote AI", color = Color.White, fontSize = 30.sp)
                    Text("Аудио → Текст → AI анализ", color = Color(0xFF9B7BFF))

                    Spacer(Modifier.height(25.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF20222B)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("📞 Записи разговоров", color = Color.White, fontSize = 20.sp)
                            Text("Все звонки и заметки в одном месте", color = Color.Gray)
                        }
                    }

                    Spacer(Modifier.height(15.dp))

                    Button(onClick = {
                        selectedFile = getExternalFilesDir(null)?.listFiles()
                            ?.firstOrNull { it.name.endsWith(".m4a") }
                        result = selectedFile?.let { "Выбрано: ${it.name}" } ?: "Записей нет"
                    }) {
                        Text("Выбрать запись")
                    }

                    Button(onClick = {
                        result = selectedFile?.let { "🧠 Расшифровка Whisper: ${it.name}" }
                            ?: "Сначала выберите запись"
                    }) {
                        Text("📝 Расшифровать")
                    }

                    Button(onClick = {
                        try {
                            player?.release()
                            player = MediaPlayer().apply {
                                setDataSource(selectedFile?.absolutePath)
                                prepare()
                                start()
                            }
                            result = "▶ Прослушивание"
                        } catch (e: Exception) {
                            result = "Нет записи"
                        }
                    }) {
                        Text("▶ Прослушать")
                    }

                    Spacer(Modifier.height(25.dp))
                    Text(result, color = Color.White)
                }
            }
        }
    }
}
