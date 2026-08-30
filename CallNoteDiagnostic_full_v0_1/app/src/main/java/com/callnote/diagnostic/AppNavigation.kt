package com.callnote.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun AppNavigation() {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Text("📁 Записи")
        Text("📊 Аналитика")
        Text("⚙ Настройки")
    }
}
