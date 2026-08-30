package com.callnote.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioPlayerCard(duration: String) {
    Card(Modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("🎧 Аудиозапись")
            Text("00:00 ━━━━━ $duration")
            Button(onClick = {}) {
                Text("▶ Воспроизвести")
            }
        }
    }
}
