package com.callnote.diagnostic

import android.Manifest
import android.media.AudioManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE), 10)

        setContent {
            var result by remember { mutableStateOf("Готов к тесту") }

            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("CallNote Diagnostic v0.8")
                    Text("\nЗаписи звонков")

                    Button(onClick = {
                        val phone = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                        result = "Модель: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nAudio: ${audio.mode}\nСостояние: ${phone.callState}"
                    }) {
                        Text("Проверить звонок")
                    }

                    Button(onClick = {
                        val dir = getExternalFilesDir(null)
                        val files = dir?.listFiles()?.filter { it.name.endsWith(".m4a") }
                        result = if (files.isNullOrEmpty()) {
                            "Записей пока нет"
                        } else {
                            files.joinToString("\n") { "${it.name} (${it.length()/1024} KB)" }
                        }
                    }) {
                        Text("Список записей")
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
