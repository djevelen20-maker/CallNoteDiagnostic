package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CallListScreen(calls: List<CallCard>) {
    Column(Modifier.padding(16.dp)) {
        Text("Все разговоры")

        calls.forEach { call ->
            Card(Modifier.padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📞 ${call.date}")
                    Text("🎙 ${call.audio.name}")

                    if (call.transcript.isNotEmpty()) {
                        Text("📝 ${call.transcript.take(80)}")
                    } else {
                        Text("Ожидается расшифровка")
                    }

                    if (call.summary.isNotEmpty()) {
                        Text("🤖 ${call.summary}")
                    }
                }
            }
        }
    }
}
