package com.callnote.diagnostic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
private fun CallNoteApp() {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    MaterialTheme {
        Surface(color = Ink) {
            CallNoteHome(
                context = context,
                hasAudioPermission = hasAudioPermission,
                requestPermission = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            )
        }
    }
}

@Composable
private fun CallNoteHome(
    context: Context,
    hasAudioPermission: Boolean,
    requestPermission: () -> Unit
) {
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var nowPlaying by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Готов к записи и заметкам") }
    var noteText by remember { mutableStateOf("") }
    val recordings = remember { mutableStateListOf<File>() }
    val notes = remember { mutableStateListOf<String>() }

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

    LaunchedEffect(Unit) {
        refreshRecordings()
        refreshNotes()
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSeconds = 0
            while (isRecording) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
    }

    fun stopPlayer() {
        player?.release()
        player = null
        nowPlaying = null
    }

    fun stopRecording() {
        runCatching {
            recorder?.stop()
        }
        recorder?.release()
        recorder = null
        isRecording = false
        status = "Запись сохранена: ${formatTimer(elapsedSeconds)}"
        refreshRecordings()
    }

    fun startRecording() {
        if (!hasAudioPermission) {
            requestPermission()
            status = "Разрешите доступ к микрофону"
            return
        }

        stopPlayer()
        val target = File(recordingsDir(context), "callnote_${timestampForFile()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        runCatching {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
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
            status = "Идет запись"
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
                    status = "Прослушивание завершено"
                }
                start()
            }
        }.onSuccess {
            player = it
            nowPlaying = file.name
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

            StatusCard(
                isRecording = isRecording,
                elapsedSeconds = elapsedSeconds,
                status = status
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (isRecording) stopRecording() else startRecording() },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Wine else Gold)
                ) {
                    Text(if (isRecording) "Остановить" else "Записать", color = Ink, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { stopPlayer(); status = "Воспроизведение остановлено" },
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
                        onPlay = { playRecording(file) },
                        onDelete = {
                            if (nowPlaying == file.name) stopPlayer()
                            file.delete()
                            status = "Запись удалена"
                            refreshRecordings()
                        }
                    )
                }
            }

            SectionTitle("Заметки")
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Заметка к разговору") },
                minLines = 3
            )
            Button(
                onClick = { saveNote() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Steel)
            ) {
                Text("Сохранить заметку", color = Color.White, fontWeight = FontWeight.Bold)
            }

            notes.take(4).forEach {
                Text(it, color = SoftText, fontSize = 14.sp, lineHeight = 19.sp)
                Divider(color = Line)
            }

            SectionTitle("AI")
            FeaturePanel(
                title = "Расшифровка и краткое содержание",
                body = "Каркас AI-модуля готов: записи сохраняются как .m4a, следующий шаг - подключить распознавание речи и резюме разговоров."
            )

            SectionTitle("Звонки")
            FeaturePanel(
                title = "Подготовка под Huawei Android 14",
                body = "Добавлена база для ручных записей и заметок. Автоматическая запись звонков зависит от ограничений прошивки и системных разрешений."
            )

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
        Image(
            painter = painterResource(id = R.drawable.avamishin_logo),
            contentDescription = "AVAMishin",
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text("CallNote AI", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("by AVAMishin", color = Gold, fontSize = 16.sp)
            Text("Аудио, заметки и AI-анализ", color = SoftText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatusCard(isRecording: Boolean, elapsedSeconds: Int, status: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (isRecording) "Запись идет" else "Диктофон готов", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(formatTimer(elapsedSeconds), color = if (isRecording) Gold else SoftText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(status, color = SoftText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RecordingRow(
    file: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
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

private fun recordingsDir(context: Context): File {
    return File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallNoteAI").apply {
        mkdirs()
    }
}

private fun notesFile(context: Context): File {
    return File(context.filesDir, "callnote_notes.txt")
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

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024
    return if (kb < 1024) "$kb КБ" else "${kb / 1024} МБ"
}

private val Ink = Color(0xFF080A0B)
private val DeepGreen = Color(0xFF10231F)
private val Panel = Color(0xE61A1E22)
private val Gold = Color(0xFFE8C46D)
private val Steel = Color(0xFF2D6F88)
private val Wine = Color(0xFFC85C5C)
private val SoftText = Color(0xFFC9D1CE)
private val Line = Color(0xFF34413F)
