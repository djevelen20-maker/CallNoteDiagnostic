package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConversationScreen(call: CallCard) {
    Column(Modifier.padding(16.dp)) {
        Text("📞 Разговор")
        Text("${call.date}")
        Text("")
        AudioPlayerCard(call.duration)
        Text("")
        Text("📝 Расшифровка")
        Text(if (call.transcript.isEmpty()) "Нет текста" else call.transcript)
        Text("")
        AISummaryScreen(call.summary, call.tasks)
    }
}
