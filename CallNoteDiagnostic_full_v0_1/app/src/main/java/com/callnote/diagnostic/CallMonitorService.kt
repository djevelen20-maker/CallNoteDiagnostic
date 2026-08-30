package com.callnote.diagnostic

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallMonitorService : Service() {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun startTestRecording() {
        try {
            val name = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()
            ).format(Date())

            file = File(
                getExternalFilesDir(null),
                "CallNote_$name.m4a"
            )

            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file)
                prepare()
                start()
            }
        } catch (e: Exception) {
            recorder = null
        }
    }

    fun stopTestRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
