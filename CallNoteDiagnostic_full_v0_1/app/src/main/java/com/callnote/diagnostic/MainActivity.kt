package com.callnote.diagnostic

import android.media.AudioManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var result by remember { mutableStateOf("Нажмите кнопку для проверки") }

            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text("CallNote Diagnostic v0.2")
                    Text("\nДиагностика Huawei / Android")

                    Button(onClick = {
                        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                        result = """
                            Модель: ${Build.MANUFACTURER} ${Build.MODEL}
                            Android: ${Build.VERSION.RELEASE}
                            SDK: ${Build.VERSION.SDK_INT}
                            Режим связи: ${audio.mode}
                            Проверка записи звонков: требуется тест
                        """.trimIndent()
                    }) {
                        Text("Проверить устройство")
                    }

                    Text("\n$result")
                }
            }
        }
    }
}
