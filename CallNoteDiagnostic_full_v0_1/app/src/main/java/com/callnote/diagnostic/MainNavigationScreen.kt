package com.callnote.diagnostic

import androidx.compose.runtime.Composable

@Composable
fun MainNavigationScreen(router: ScreenRouter) {
    when (val screen = router.current) {
        is Screen.Records -> {
            // экран записей
        }
        is Screen.Analytics -> {
            // аналитика AI
        }
        is Screen.Settings -> {
            // настройки
        }
        is Screen.Conversation -> {
            ConversationScreen(screen.call)
        }
    }
}
