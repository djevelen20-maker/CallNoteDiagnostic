package com.callnote.diagnostic

import android.Manifest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
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
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE), 10)

        setContent {
            var result by remember { mutableStateOf("Готово") }
            var selectedFile by remember { mutableStateOf<File?>(null) }

            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("CallNote Diagnostic v0.9")
                    Text("\nАудио записи звонков")

                    Button(onClick = {
                        val dir = getExternalFilesDir(null)
                        val files = dir?.listFiles()?.filter { it.name.endsWith(".m4a") }
                        selectedFile = files?.firstOrNull()
                        result = if (files.isNullOrEmpty()) "Записей нет" else "Найдено записей: ${files.size}"
                    }) {
                        Text("Обновить список")
                    }

                    Button(onClick = {
                        try {
                            player?.release()
                            player = MediaPlayer().apply {
                                setDataSource(selectedFile?.absolutePath)
                                prepare()
                                start()
                            }
                            result = "Воспроизведение: ${selectedFile?.name}"
                        } catch (e: Exception) {
                            result = "Выберите запись"
                        }
                    }) {
                        Text("Прослушать")
                    }

                    Button(onClick = {
                        selectedFile?.delete()
                        result = "Запись удалена"
                    }) {
                        Text("Удалить запись")
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
