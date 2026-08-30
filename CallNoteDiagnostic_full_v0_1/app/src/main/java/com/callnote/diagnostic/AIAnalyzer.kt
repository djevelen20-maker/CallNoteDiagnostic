package com.callnote.diagnostic

class AIAnalyzer {

    fun summarize(text: String): String {
        return """
        Анализ разговора:

        $text

        Итоги и задачи будут сформированы AI.
        """.trimIndent()
    }
}
