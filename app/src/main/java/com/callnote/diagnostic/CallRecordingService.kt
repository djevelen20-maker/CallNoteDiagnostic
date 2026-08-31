package com.callnote.diagnostic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Подготовка записи звонка"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallNoteAI").apply { mkdirs() }
        val file = File(directory, "callnote_call_${timestamp()}.m4a")
        val mediaRecorderResult = runCatching { createRecorder(file) }
        val mediaRecorder = mediaRecorderResult.getOrElse {
            appendEvent("Запись звонка недоступна: ${it.localizedMessage ?: "система заблокировала аудиоканал"}")
            stopSelf()
            return
        }
        runCatching { mediaRecorder.start() }.onSuccess {
            recorder = mediaRecorder
            outputFile = file
            updateNotification("Идет запись звонка")
            appendEvent("Запись звонка началась: ${file.name}")
        }.onFailure {
            mediaRecorder.release()
            appendEvent("Запись звонка не началась: ${it.localizedMessage ?: "доступ к микрофону закрыт"}")
            stopSelf()
        }
    }

    private fun createRecorder(file: File): MediaRecorder {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        var lastError: Throwable? = null
        for (source in sources) {
            val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            val prepared = runCatching {
                candidate.setAudioSource(source)
                candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                candidate.setAudioEncodingBitRate(128_000)
                candidate.setAudioSamplingRate(44_100)
                candidate.setOutputFile(file.absolutePath)
                candidate.prepare()
                candidate
            }.getOrElse {
                lastError = it
                candidate.release()
                null
            }
            if (prepared != null) {
                appendEvent("Выбран источник звонка: $source")
                return prepared
            }
        }
        throw IllegalStateException(lastError?.localizedMessage ?: "ни один источник аудио не доступен")
    }

    private fun stopRecording() {
        val current = recorder ?: run { stopSelf(); return }
        runCatching { current.stop() }
        current.release()
        recorder = null
        outputFile?.let { appendEvent("Запись звонка сохранена: ${it.name}") }
        outputFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("CallNote AI")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Запись звонков", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun appendEvent(text: String) {
        runCatching {
            File(filesDir, "callnote_call_events.txt").appendText("${timestamp()} - $text\n")
        }
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    companion object {
        const val ACTION_START = "com.callnote.diagnostic.action.START_CALL_RECORDING"
        const val ACTION_STOP = "com.callnote.diagnostic.action.STOP_CALL_RECORDING"
        private const val CHANNEL_ID = "call_recording"
        private const val NOTIFICATION_ID = 2408
    }
}

