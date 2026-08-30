package com.callnote.diagnostic

import java.io.File

class TranscriptionAction {

    private val controller = TranscriptionController()

    fun run(audio: File, callback: (TranscriptionResult) -> Unit) {
        Thread {
            val result = controller.process(audio)
            callback(result)
        }.start()
    }
}
