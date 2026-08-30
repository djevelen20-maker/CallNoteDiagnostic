package com.callnote.diagnostic

import java.io.File

data class TranscriptionResult(
    val audioFile: File,
    val text: String,
    val status: String
)
