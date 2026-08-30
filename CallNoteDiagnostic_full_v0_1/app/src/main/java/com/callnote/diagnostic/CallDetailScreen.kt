package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CallDetailScreen(call: CallCard) {
    var status by remember { mutableStateOf("Готово") }
    var transcript by remember { mutableStateOf(call.transcript) }

    Column(Modifier.padding(20.dp)) {
        Text("📞 Разговор")
        Text("Дата: ${call.date}")
        Text("Файл: ${call.audio.name}")

        Button(onClick = {
            status = "Идёт расшифровка..."
            TranscriptionAction().run(call.audio) { result ->
                transcript = result.text
                status = "Готово"
            }
        }) {
            Text("📝 Расшифровать")
        }

        Text("\nСтатус: $status")

        Text("\n📝 Расшифровка:")
        Text(if (transcript.isEmpty()) "Ожидание Whisper" else transcript)

        Text("\n🤖 Итог AI:")
        Text(if (call.summary.isEmpty()) "Анализ ещё не выполнен" else call.summary)

        Text("\n✅ Задачи:")
        if (call.tasks.isEmpty()) {
            Text("Нет задач")
        } else {
            call.tasks.forEach { Text("• $it") }
        }
    }
}
