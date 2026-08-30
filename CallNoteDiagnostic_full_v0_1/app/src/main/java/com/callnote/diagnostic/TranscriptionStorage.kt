package com.callnote.diagnostic

import java.io.File

class TranscriptionStorage {

    fun save(audioFile: File, text: String): File {
        val txt = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".txt")
        txt.writeText(text)
        return txt
    }

    fun load(file: File): String {
        return if (file.exists()) file.readText() else "Нет текста"
    }
}
