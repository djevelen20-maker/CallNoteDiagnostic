package com.callnote.diagnostic

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.telephony.TelephonyManager
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallNoteApp()
        }
    }
}

private object ActiveCallController {
    var call: Call? = null
}

internal object CallRecordingState {
    @Volatile var isActive: Boolean = false
}

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val rawState = intent.getStringExtra(TelephonyManager.EXTRA_STATE).orEmpty()
        val state = when (rawState) {
            TelephonyManager.EXTRA_STATE_RINGING -> "Входящий звонок"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "Разговор начался"
            TelephonyManager.EXTRA_STATE_IDLE -> "Звонок завершен"
            else -> "Состояние звонка изменилось"
        }

        runCatching {
            callEventsFile(context).appendText("${timestampForNote()} - $state\n")
        }
    }
}

class CallNoteInCallService : InCallService() {
    private val callbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveCallController.call = call
        runCatching {
            callEventsFile(this).appendText("${timestampForNote()} - Звонок открыт в CallNote AI\n")
        }
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) startCallRecording()
                if (state == Call.STATE_DISCONNECTED) stopCallRecording()
            }
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        if (call.state == Call.STATE_ACTIVE) startCallRecording()
    }

    override fun onCallRemoved(call: Call) {
        if (ActiveCallController.call == call) ActiveCallController.call = null
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        stopCallRecording()
        runCatching {
            callEventsFile(this).appendText("${timestampForNote()} - Звонок закрыт в CallNote AI\n")
        }
        super.onCallRemoved(call)
    }

    private fun startCallRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callEventsFile(this).appendText("${timestampForNote()} - Нет разрешения на микрофон для записи звонка\n")
            return
        }
        val intent = Intent(this, CallRecordingService::class.java).setAction(CallRecordingService.ACTION_START)
        runCatching { ContextCompat.startForegroundService(this, intent) }
            .onFailure { callEventsFile(this).appendText("${timestampForNote()} - Сервис записи не запущен: ${it.localizedMessage}\n") }
    }

    private fun stopCallRecording() {
        runCatching {
            startService(Intent(this, CallRecordingService::class.java).setAction(CallRecordingService.ACTION_STOP))
        }
    }
}

@Composable
private fun CallNoteApp() {
    val context = LocalContext.current
    var hasAudioPermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var hasPhonePermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.READ_PHONE_STATE)) }
    var hasCallPermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.CALL_PHONE)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAudioPermission = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        hasPhonePermission = hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        hasCallPermission = hasPermission(context, Manifest.permission.CALL_PHONE)
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        Toast.makeText(context, "Разрешения обновлены", Toast.LENGTH_SHORT).show()
    }

    fun requestPermissions() {
        permissionsLauncher.launch(requiredPermissions())
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission || !hasPhonePermission || !hasCallPermission || !hasNotificationPermission) {
            requestPermissions()
        }
    }

    MaterialTheme(colorScheme = AppColors) {
        Surface(color = Ink) {
            CallNoteHome(
                context = context,
                hasAudioPermission = hasAudioPermission,
                hasPhonePermission = hasPhonePermission,
                hasCallPermission = hasCallPermission,
                hasNotificationPermission = hasNotificationPermission,
                requestPermissions = ::requestPermissions
            )
        }
    }
}

@Composable
private fun CallNoteHome(
    context: Context,
    hasAudioPermission: Boolean,
    hasPhonePermission: Boolean,
    hasCallPermission: Boolean,
    hasNotificationPermission: Boolean,
    requestPermissions: () -> Unit
) {
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var lastAmplitude by remember { mutableIntStateOf(0) }
    var nowPlaying by remember { mutableStateOf<String?>(null) }
    var playbackPosition by remember { mutableIntStateOf(0) }
    var playbackDuration by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Готов к записи, заметкам и проверке звонка") }
    var noteText by remember { mutableStateOf("") }
    // A manual dictation must use the microphone. Call-only sources are often silent
    // outside an active call, especially on Huawei firmware.
    var selectedSource by remember { mutableStateOf(recordingSourceOptions().first { it.audioSource == MediaRecorder.AudioSource.MIC }) }
    var selectedTab by remember { mutableStateOf(AppTab.Phone) }
    var dialNumber by remember { mutableStateOf((context as? Activity)?.intent?.data?.schemeSpecificPart.orEmpty()) }
    var callState by remember { mutableIntStateOf(Call.STATE_DISCONNECTED) }
    var callRecordingActive by remember { mutableStateOf(CallRecordingState.isActive) }
    var aiDraft by remember { mutableStateOf("") }
    var speechText by remember { mutableStateOf("") }
    var transcriptionFile by remember { mutableStateOf<String?>(null) }
    val audioSourceDescriptor = remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    val offlineWhisper = remember(context) { OfflineWhisperTranscriber(context) }
    val recordings = remember { mutableStateListOf<File>() }
    val notes = remember { mutableStateListOf<String>() }
    val callEvents = remember { mutableStateListOf<String>() }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        audioSourceDescriptor.value?.close()
        audioSourceDescriptor.value = null
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()
            speechText = matches.firstOrNull().orEmpty()
            status = if (speechText.isBlank()) "Речь не распознана" else {
                if (transcriptionFile == null) "Речь распознана" else "Аудио расшифровано"
            }
        } else {
            status = "Распознавание отменено"
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status = "Говорите, идет распознавание" }
            override fun onBeginningOfSpeech() { status = "Слушаю речь" }
            override fun onRmsChanged(rmsdB: Float) { lastAmplitude = (rmsdB * 100).toInt().coerceAtLeast(0) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "Обрабатываю речь" }
            override fun onError(error: Int) {
                status = when (error) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Нет соединения с сервисом распознавания"
                    SpeechRecognizer.ERROR_AUDIO -> "Сервис не получил звук с микрофона"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Разрешите микрофон для распознавания"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Слова не разобраны: говорите громче и ближе к телефону"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание уже занято другим приложением"
                    else -> "Ошибка распознавания речи: $error"
                }
            }
            override fun onResults(results: Bundle?) {
                speechText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                transcriptionFile = null
                status = if (speechText.isBlank()) "Речь не распознана" else "Текст получен"
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) speechText = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer?.destroy() }
    }

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        status = if (result.resultCode == Activity.RESULT_OK) {
            "CallNote AI выбран как приложение телефона"
        } else {
            "Роль приложения телефона не выдана"
        }
        Toast.makeText(context, status, Toast.LENGTH_LONG).show()
    }

    fun refreshRecordings() {
        recordings.clear()
        recordings.addAll(recordingsDir(context).listFiles()
            ?.filter { it.extension.equals("m4a", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty())
    }

    fun refreshNotes() {
        notes.clear()
        val file = notesFile(context)
        if (file.exists()) {
            notes.addAll(file.readLines().filter { it.isNotBlank() }.asReversed())
        }
    }

    fun refreshCalls() {
        callEvents.clear()
        val file = callEventsFile(context)
        if (file.exists()) {
            callEvents.addAll(file.readLines().filter { it.isNotBlank() }.asReversed())
        }
    }

    LaunchedEffect(Unit) {
        refreshRecordings()
        refreshNotes()
        refreshCalls()
        aiDraft = aiDraftsFile(context).takeIf { it.exists() }?.readText().orEmpty()
    }

    LaunchedEffect(Unit) {
        while (true) {
            callState = ActiveCallController.call?.state ?: Call.STATE_DISCONNECTED
            callRecordingActive = CallRecordingState.isActive
            delay(500)
        }
    }

    fun placeCall() {
        val number = dialNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (number.isBlank()) return
        if (!hasCallPermission) {
            requestPermissions()
            return
        }
        runCatching {
            context.getSystemService(TelecomManager::class.java)
                ?.placeCall(Uri.parse("tel:${Uri.encode(number)}"), Bundle())
            callState = Call.STATE_DIALING
        }.onFailure { status = "Не удалось начать звонок: ${it.localizedMessage ?: "проверьте телефонные разрешения"}" }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSeconds = 0
            while (isRecording) {
                delay(1000)
                lastAmplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                elapsedSeconds += 1
            }
        }
    }

    LaunchedEffect(nowPlaying) {
        while (nowPlaying != null) {
            playbackPosition = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release()
            player?.release()
            offlineWhisper.close()
        }
    }

    fun stopPlayer() {
        player?.release()
        player = null
        nowPlaying = null
        playbackPosition = 0
        playbackDuration = 0
    }

    fun stopRecording() {
        runCatching {
            recorder?.stop()
        }
        recorder?.release()
        recorder = null
        isRecording = false
        lastAmplitude = 0
        status = "Запись сохранена: ${formatTimer(elapsedSeconds)}"
        refreshRecordings()
    }

    fun startRecording(callMode: Boolean = false) {
        if (!hasAudioPermission) {
            requestPermissions()
            status = "Разрешите доступ к микрофону"
            return
        }

        stopPlayer()
        val prefix = if (callMode) "calltest" else selectedSource.filePrefix
        val target = File(recordingsDir(context), "callnote_${prefix}_${timestampForFile()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        runCatching {
            mediaRecorder.setAudioSource(selectedSource.audioSource)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        }.onSuccess {
            recorder = mediaRecorder
            isRecording = true
            status = if (callMode) {
                "Проверка звонка: ${selectedSource.title}"
            } else {
                "Идет запись: ${selectedSource.title}"
            }
        }.onFailure {
            mediaRecorder.release()
            status = "Не удалось начать запись: ${it.localizedMessage ?: "ошибка микрофона"}"
        }
    }

    fun playRecording(file: File) {
        stopPlayer()
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    it.release()
                    player = null
                    nowPlaying = null
                    playbackPosition = 0
                    playbackDuration = 0
                    status = "Прослушивание завершено"
                }
                start()
            }
        }.onSuccess {
            player = it
            nowPlaying = file.name
            playbackPosition = 0
            playbackDuration = it.duration.coerceAtLeast(0)
            status = "Воспроизведение: ${file.name}"
        }.onFailure {
            status = "Не удалось воспроизвести запись"
        }
    }

    fun saveNote() {
        val cleanNote = noteText.trim()
        if (cleanNote.isEmpty()) {
            status = "Введите текст заметки"
            return
        }
        val line = "${timestampForNote()} - $cleanNote"
        notesFile(context).appendText(line + "\n")
        noteText = ""
        status = "Заметка сохранена"
        refreshNotes()
    }

    fun buildAiDraft() {
        val latestRecording = recordings.firstOrNull()?.name ?: "запись пока не выбрана"
        val latestNote = speechText.ifBlank { notes.firstOrNull() ?: "заметок пока нет" }
        aiDraft = """
            AI-черновик CallNote AI
            Создано: ${timestampForNote()}
            Аудио: $latestRecording
            Текст: $latestNote

            Резюме:
            Подготовлен локальный черновик анализа по распознанной речи и заметкам. Для автоматической расшифровки сохраненных аудиофайлов нужен серверный модуль или AI API.

            Действия:
            1. Проверить качество аудио.
            2. Добавить важные пункты в заметку.
            3. Отправить запись в AI-модуль после подключения backend.
        """.trimIndent()
        aiDraftsFile(context).writeText(aiDraft)
        status = "AI-черновик подготовлен"
    }

    fun startSpeechRecognition() {
        if (!hasAudioPermission) {
            requestPermissions()
            status = "Разрешите микрофон для распознавания речи"
            return
        }

        if (isRecording) {
            stopRecording()
            status = "Запись сохранена. Теперь говорите для расшифровки"
        }
        speechRecognizer?.cancel()
        speechText = ""
        transcriptionFile = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите, CallNote AI слушает")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        runCatching {
            if (speechRecognizer == null) {
                status = "На телефоне нет голосового сервиса. Установите или включите Google Voice Input"
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            } else {
                speechRecognizer.startListening(intent)
                Toast.makeText(context, "Говорите, идет распознавание", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            status = "Не удалось запустить распознавание: ${it.localizedMessage ?: "проверьте микрофон"}"
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
        }
    }

    fun transcribeRecording(file: File) {
        if (!hasAudioPermission) {
            requestPermissions()
            status = "Разрешите доступ к микрофону для расшифровки"
            return
        }
        transcriptionFile = file.name
        speechText = ""
        status = "Подготовка аудио для Whisper..."
        Thread {
            val wav = File(context.cacheDir, "${file.nameWithoutExtension}_whisper.wav")
            runCatching {
                AudioFileConverter.toWhisperWav(file, wav)
                Handler(Looper.getMainLooper()).post { status = "Whisper расшифровывает запись..." }
                offlineWhisper.transcribe(
                    wav,
                    onUpdate = { message -> Handler(Looper.getMainLooper()).post { status = message } },
                    onResult = { result -> Handler(Looper.getMainLooper()).post {
                        speechText = result.trim()
                        status = if (speechText.isBlank()) "Whisper не нашел речи в записи" else "Расшифровка готова"
                    } }
                )
            }.onFailure {
                Handler(Looper.getMainLooper()).post {
                    status = "Не удалось расшифровать запись: ${it.localizedMessage ?: "проверьте, что в записи есть голос"}"
                }
            }
        }.start()
    }

    fun saveSpeechAsNote() {
        val text = speechText.trim()
        if (text.isBlank()) {
            status = "Сначала распознайте речь"
            return
        }
        notesFile(context).appendText("${timestampForNote()} - $text\n")
        status = "Распознанный текст сохранен"
        refreshNotes()
    }

    fun requestDialerIntegration() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }

        runCatching { dialerRoleLauncher.launch(intent) }
            .onFailure {
                status = "Откройте настройки приложений телефона"
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, DeepGreen, Ink)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header()
            TabBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
            StatusCard(
                isRecording = isRecording,
                isCallRecording = callRecordingActive,
                elapsedSeconds = elapsedSeconds,
                lastAmplitude = lastAmplitude,
                status = status
            )

            when (selectedTab) {
                AppTab.Phone -> PhoneTab(
                    number = dialNumber,
                    callState = callState,
                    onNumberChanged = { dialNumber = it },
                    onCall = ::placeCall,
                    onAnswer = { ActiveCallController.call?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) },
                    onHangUp = { ActiveCallController.call?.disconnect() }
                )
                AppTab.Recorder -> RecorderTab(
                    recordings = recordings,
                    isRecording = isRecording,
                    nowPlaying = nowPlaying,
                    onRecord = { if (isRecording) stopRecording() else startRecording() },
                    onStopPlayer = {
                        stopPlayer()
                        status = "Воспроизведение остановлено"
                    },
                    onPlay = ::playRecording,
                    playbackPosition = playbackPosition,
                    playbackDuration = playbackDuration,
                    onSeek = { position -> player?.seekTo(position) },
                    onDelete = {
                        if (nowPlaying == it.name) stopPlayer()
                        it.delete()
                        status = "Запись удалена"
                        refreshRecordings()
                    }
                )

                AppTab.Calls -> CallsTab(
                    isRecording = callRecordingActive,
                    lastAmplitude = lastAmplitude,
                    callEvents = callEvents,
                    hasPhonePermission = hasPhonePermission,
                    onRefreshCalls = {
                        refreshCalls()
                        status = "Журнал звонков обновлен"
                    },
                    onRequestPermissions = requestPermissions
                )

                AppTab.Notes -> NotesTab(
                    noteText = noteText,
                    notes = notes,
                    onNoteChanged = { noteText = it },
                    onSaveNote = ::saveNote
                )

                AppTab.Ai -> AiTab(
                    recordings = recordings,
                    notes = notes,
                    aiDraft = aiDraft,
                    speechText = speechText,
                    onStartSpeech = ::startSpeechRecognition,
                    onTranscribeRecording = ::transcribeRecording,
                    onSaveSpeech = ::saveSpeechAsNote,
                    onBuildDraft = ::buildAiDraft
                )

                AppTab.Settings -> SettingsTab(
                    context = context,
                    hasAudioPermission = hasAudioPermission,
                    hasPhonePermission = hasPhonePermission,
                    hasNotificationPermission = hasNotificationPermission,
                    isDefaultDialer = isDefaultDialer(context),
                    onRequestPermissions = requestPermissions,
                    onRequestDialerIntegration = ::requestDialerIntegration
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 94.dp, height = 74.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.avamishin_logo),
                contentDescription = "AVAMishin",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("CallNote AI", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("by AVAMishin", color = Gold, fontSize = 16.sp)
            Text("Аудио, заметки и AI-анализ", color = SoftText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TabBar(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppTab.entries.chunked(3).forEach { rowTabs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTabs.forEach { tab ->
                    val selected = selectedTab == tab
                    OutlinedButton(
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Gold.copy(alpha = 0.18f) else Color.Transparent,
                            contentColor = if (selected) Gold else SoftText
                        )
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(isRecording: Boolean, isCallRecording: Boolean, elapsedSeconds: Int, lastAmplitude: Int, status: String) {
    val recordingNow = isRecording || isCallRecording
    val level = if (isRecording) (lastAmplitude / 32767f).coerceIn(0.02f, 1f) else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    isCallRecording -> "●  ИДЕТ ЗАПИСЬ ЗВОНКА"
                    isRecording -> "●  ИДЕТ ЗАПИСЬ"
                    else -> "Диктофон готов"
                },
                color = if (recordingNow) Wine else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(formatTimer(elapsedSeconds), color = if (recordingNow) Gold else SoftText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Line, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(level)
                        .height(8.dp)
                        .background(if (lastAmplitude > 0) Gold else Wine, RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Уровень звука: $lastAmplitude", color = SoftText, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(status, color = SoftText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RecorderTab(
    recordings: List<File>,
    isRecording: Boolean,
    nowPlaying: String?,
    onRecord: () -> Unit,
    onStopPlayer: () -> Unit,
    onPlay: (File) -> Unit,
    playbackPosition: Int,
    playbackDuration: Int,
    onSeek: (Int) -> Unit,
    onDelete: (File) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onRecord,
            modifier = Modifier.weight(1f).height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Wine else Gold)
        ) {
            Text(if (isRecording) "Остановить" else "Записать", color = Ink, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onStopPlayer,
            modifier = Modifier.weight(1f).height(54.dp),
            enabled = nowPlaying != null
        ) {
            Text("Стоп")
        }
    }

    SectionTitle("Мои записи")
    if (recordings.isEmpty()) {
        EmptyPanel("Записей пока нет. Нажмите «Записать», чтобы создать первую аудиозаметку.")
    } else {
        recordings.forEach { file ->
            RecordingRow(
                file = file,
                isPlaying = nowPlaying == file.name,
                onPlay = { onPlay(file) },
                playbackPosition = if (nowPlaying == file.name) playbackPosition else 0,
                playbackDuration = if (nowPlaying == file.name) playbackDuration else 0,
                onSeek = onSeek,
                onDelete = { onDelete(file) }
            )
        }
    }
}

@Composable
private fun PhoneTab(
    number: String,
    callState: Int,
    onNumberChanged: (String) -> Unit,
    onCall: () -> Unit,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit
) {
    SectionTitle("Телефон")
    OutlinedTextField(
        value = number,
        onValueChange = { onNumberChanged(it.filter { ch -> ch.isDigit() || ch in "+*#" }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Номер телефона") },
        singleLine = true
    )
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    keys.chunked(3).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { key ->
                OutlinedButton(
                    onClick = { onNumberChanged(number + key) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text(key, fontSize = 21.sp) }
            }
        }
    }
    val active = callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING
    val ringing = callState == Call.STATE_RINGING
    if (ringing) {
        FeaturePanel(title = "Входящий звонок", body = "Ответьте, чтобы начать разговор")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAnswer, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Steel)) { Text("Ответить") }
            OutlinedButton(onClick = onHangUp, modifier = Modifier.weight(1f)) { Text("Отклонить") }
        }
    } else if (active) {
        FeaturePanel(title = "Разговор идет", body = "CallNote AI подключен к звонку")
        Button(onClick = onHangUp, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Wine)) { Text("Завершить звонок") }
    } else {
        Button(onClick = onCall, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold)) { Text("Позвонить", color = Ink, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun CallsTab(
    isRecording: Boolean,
    lastAmplitude: Int,
    callEvents: List<String>,
    hasPhonePermission: Boolean,
    onRefreshCalls: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    SectionTitle("Запись звонка")
    Text(
        if (isRecording) "● Запись звонка идет. Файл появится в «Диктофоне»." else "Запись запускается сама после ответа на звонок.",
        color = if (isRecording) Wine else SoftText,
        fontSize = 15.sp
    )

    DiagnosticsPanel(lastAmplitude = lastAmplitude)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onRefreshCalls, modifier = Modifier.weight(1f)) {
            Text("Обновить")
        }
        OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.weight(1f)) {
            Text("Разрешения")
        }
    }

    SectionTitle("События звонков")
    if (callEvents.isEmpty()) {
        EmptyPanel("Событий пока нет. Разрешите доступ к состоянию телефона и сделайте тестовый звонок.")
    } else {
        callEvents.take(8).forEach {
            Text(it, color = SoftText, fontSize = 14.sp, lineHeight = 19.sp)
            HorizontalDivider(color = Line)
        }
    }
}

@Composable
private fun NotesTab(
    noteText: String,
    notes: List<String>,
    onNoteChanged: (String) -> Unit,
    onSaveNote: () -> Unit
) {
    SectionTitle("Заметки")
    OutlinedTextField(
        value = noteText,
        onValueChange = onNoteChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Заметка к разговору") },
        minLines = 5
    )
    Button(
        onClick = onSaveNote,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Steel)
    ) {
        Text("Сохранить заметку", color = Color.White, fontWeight = FontWeight.Bold)
    }

    if (notes.isEmpty()) {
        EmptyPanel("Заметок пока нет. Сюда попадут ручные итоги разговоров.")
    } else {
        notes.take(12).forEach {
            Text(it, color = SoftText, fontSize = 14.sp, lineHeight = 19.sp)
            HorizontalDivider(color = Line)
        }
    }
}

@Composable
private fun AiTab(
    recordings: List<File>,
    notes: List<String>,
    aiDraft: String,
    speechText: String,
    onStartSpeech: () -> Unit,
    onTranscribeRecording: (File) -> Unit,
    onSaveSpeech: () -> Unit,
    onBuildDraft: () -> Unit
) {
    SectionTitle("AI-анализ")
    Button(
        onClick = onStartSpeech,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold)
    ) {
        Text("Распознать речь", color = Ink, fontWeight = FontWeight.Bold)
    }
    if (speechText.isNotBlank()) {
        FeaturePanel(title = "Распознанный текст", body = speechText)
        OutlinedButton(
            onClick = onSaveSpeech,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Сохранить в заметки")
        }
    }
    InfoLine("Последняя запись", recordings.firstOrNull()?.name ?: "нет записей")
    recordings.firstOrNull()?.let { file ->
        OutlinedButton(
            onClick = { onTranscribeRecording(file) },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Расшифровать последнюю запись")
        }
    }
    InfoLine("Последняя заметка", notes.firstOrNull() ?: "нет заметок")
    Button(
        onClick = onBuildDraft,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Steel)
    ) {
        Text("Подготовить AI-черновик", color = Color.White, fontWeight = FontWeight.Bold)
    }
    if (aiDraft.isNotBlank()) {
        FeaturePanel(title = "Черновик", body = aiDraft)
    }
}

@Composable
private fun SettingsTab(
    context: Context,
    hasAudioPermission: Boolean,
    hasPhonePermission: Boolean,
    hasNotificationPermission: Boolean,
    isDefaultDialer: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDialerIntegration: () -> Unit
) {
    SectionTitle("Настройки")
    InfoLine("Микрофон", if (hasAudioPermission) "разрешен" else "нужно разрешить")
    InfoLine("Состояние телефона", if (hasPhonePermission) "разрешено" else "нужно разрешить")
    InfoLine("Уведомления", if (hasNotificationPermission) "разрешены" else "нужно разрешить")
    InfoLine("Интеграция со звонилкой", if (isDefaultDialer) "CallNote AI выбран как приложение телефона" else "нужно выдать роль приложения телефона")
    InfoLine("Папка записей", recordingsDir(context).absolutePath)

    Button(
        onClick = onRequestPermissions,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Steel)
    ) {
        Text("Проверить разрешения", color = Color.White, fontWeight = FontWeight.Bold)
    }

    Button(
        onClick = onRequestDialerIntegration,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold)
    ) {
        Text("Встроить в звонилку", color = Ink, fontWeight = FontWeight.Bold)
    }

}

@Composable
private fun RecordingSourcePicker(
    selectedSource: RecordingSourceOption,
    enabled: Boolean,
    onSelect: (RecordingSourceOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        recordingSourceOptions().forEach { option ->
            val isSelected = selectedSource == option
            OutlinedButton(
                onClick = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) Gold.copy(alpha = 0.16f) else Color.Transparent,
                    contentColor = if (isSelected) Gold else SoftText
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(option.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(option.description, fontSize = 12.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    file: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    playbackPosition: Int,
    playbackDuration: Int,
    onSeek: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${formatFileSize(file.length())} - ${timestampFromFile(file)}", color = SoftText, fontSize = 13.sp)
            if (isPlaying && playbackDuration > 0) {
                Slider(
                    value = playbackPosition.toFloat().coerceIn(0f, playbackDuration.toFloat()),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..playbackDuration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${formatPlaybackTime(playbackPosition)} / ${formatPlaybackTime(playbackDuration)}", color = SoftText, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) Gold else Steel)
                ) {
                    Text(if (isPlaying) "Играет" else "Слушать", color = if (isPlaying) Ink else Color.White)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("Удалить")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(lastAmplitude: Int) {
    val result = when {
        lastAmplitude == 0 -> "Сигнала нет: попробуйте громкую связь и другой источник."
        lastAmplitude < 600 -> "Сигнал слабый: поднесите телефон ближе или включите громкую связь."
        else -> "Сигнал есть: запись должна содержать слышимый голос."
    }

    FeaturePanel(
        title = "Диагностика звука",
        body = "Текущий уровень: $lastAmplitude\n$result"
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyPanel(text: String) {
    FeaturePanel(title = "Пусто", body = text)
}

@Composable
private fun FeaturePanel(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text(body, color = SoftText, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
        ,Manifest.permission.CALL_PHONE
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

private fun isDefaultDialer(context: Context): Boolean {
    val telecomManager = context.getSystemService(TelecomManager::class.java)
    return telecomManager?.defaultDialerPackage == context.packageName
}

private fun recordingsDir(context: Context): File {
    return File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallNoteAI").apply {
        mkdirs()
    }
}

private fun notesFile(context: Context): File {
    return File(context.filesDir, "callnote_notes.txt")
}

private fun callEventsFile(context: Context): File {
    return File(context.filesDir, "callnote_call_events.txt")
}

private fun aiDraftsFile(context: Context): File {
    return File(context.filesDir, "callnote_ai_draft.txt")
}

private fun timestampForFile(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}

private fun timestampForNote(): String {
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
}

private fun timestampFromFile(file: File): String {
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatPlaybackTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024
    return if (kb < 1024) "$kb КБ" else "${kb / 1024} МБ"
}

private enum class AppTab(val title: String) {
    Phone("Телефон"),
    Recorder("Диктофон"),
    Calls("Звонки"),
    Notes("Заметки"),
    Ai("AI"),
    Settings("Настройки")
}

private data class RecordingSourceOption(
    val title: String,
    val description: String,
    val filePrefix: String,
    val audioSource: Int
)

private fun recordingSourceOptions(): List<RecordingSourceOption> {
    val options = mutableListOf(
        RecordingSourceOption(
            title = "Голосовая связь",
            description = "Первым пробовать во время звонка",
            filePrefix = "voice_comm",
            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        ),
        RecordingSourceOption(
            title = "Микрофон",
            description = "Обычный диктофон и громкая связь",
            filePrefix = "mic",
            audioSource = MediaRecorder.AudioSource.MIC
        ),
        RecordingSourceOption(
            title = "Распознавание",
            description = "Чистый голос, иногда лучше на Huawei",
            filePrefix = "voice_rec",
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
        ),
        RecordingSourceOption(
            title = "Камера",
            description = "Альтернативный микрофонный профиль",
            filePrefix = "camcorder",
            audioSource = MediaRecorder.AudioSource.CAMCORDER
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        options += RecordingSourceOption(
            title = "Без обработки",
            description = "Сырой микрофон, если устройство поддерживает",
            filePrefix = "raw",
            audioSource = MediaRecorder.AudioSource.UNPROCESSED
        )
    }

    return options
}

private val Ink = Color(0xFF080A0B)
private val DeepGreen = Color(0xFF10231F)
private val Panel = Color(0xE61A1E22)
private val Gold = Color(0xFFE8C46D)
private val Steel = Color(0xFF2D6F88)
private val Wine = Color(0xFFC85C5C)
private val SoftText = Color(0xFFC9D1CE)
private val Line = Color(0xFF34413F)

private val AppColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = Steel,
    onSecondary = Color.White,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF20272A),
    onSurfaceVariant = SoftText,
    outline = Color(0xFF83918E)
)

