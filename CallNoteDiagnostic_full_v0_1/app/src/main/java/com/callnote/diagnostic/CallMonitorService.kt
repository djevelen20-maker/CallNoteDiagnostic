package com.callnote.diagnostic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallMonitorService : Service() {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override fun onCreate() {
        super.onCreate()
        createNotification()
        startForeground(100, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTestRecording()
        return START_STICKY
    }

    private fun startTestRecording() {
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
        } catch (_: Exception) {
            recorder = null
        }
    }

    override fun onDestroy() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        super.onDestroy()
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "callnote",
                "CallNote recording",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        return NotificationCompat.Builder(this, "callnote")
            .setContentTitle("CallNote")
            .setContentText("Запись звонка тестируется")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
