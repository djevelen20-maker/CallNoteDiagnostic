package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AISummaryScreen(summary: String, tasks: List<String>) {
    Column(Modifier.padding(20.dp)) {
        Text("🤖 AI анализ", )

        Card(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Краткий итог:")
                Text(if (summary.isEmpty()) "Ожидание анализа" else summary)
            }
        }

        Text("\n✅ Задачи:")
        if (tasks.isEmpty()) {
            Text("Задачи не найдены")
        } else {
            tasks.forEach { Text("• $it") }
        }
    }
}
