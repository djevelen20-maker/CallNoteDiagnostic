package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CallDetailScreen(call: CallCard) {
    Column(Modifier.padding(20.dp)) {
        Text("📞 Разговор")
        Text("Дата: ${call.date}")
        Text("Файл: ${call.audio.name}")

        Text("\n📝 Расшифровка:")
        Text(if (call.transcript.isEmpty()) "Ожидание Whisper" else call.transcript)

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
