package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecordingCard(call: CallCard) {
    Card(Modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("📞 ${call.date}")
            Text("🎙 ${call.audio.name}")
            Text("▶ Прослушать")
            Text("📝 Расшифровать")
            Text("🤖 AI анализ")
        }
    }
}
