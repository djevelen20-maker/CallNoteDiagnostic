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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        ), 10)

        setContent {
            var result by remember { mutableStateOf("Готов к тесту") }
            var log by remember { mutableStateOf("") }

            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(20.dp)
                ) {
                    Text("CallNote Diagnostic v0.7")
                    Text("\nМониторинг состояния звонка")

                    Button(onClick = {
                        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                        val phone = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                        val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                        val state = when(phone.callState){
                            TelephonyManager.CALL_STATE_IDLE -> "Нет звонка"
                            TelephonyManager.CALL_STATE_RINGING -> "Входящий звонок"
                            TelephonyManager.CALL_STATE_OFFHOOK -> "Разговор активен"
                            else -> "Неизвестно"
                        }

                        val event = "$time — $state"
                        log = event + "\n" + log

                        result = """
Модель: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE}
SDK: ${Build.VERSION.SDK_INT}
Audio mode: ${audio.mode}

Состояние:
$state

Событие добавлено в журнал
""".trimIndent()
                    }) {
                        Text("Проверить звонок")
                    }

                    Button(onClick = {
                        result = "Журнал событий:\n\n$log"
                    }) {
                        Text("Показать журнал")
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
