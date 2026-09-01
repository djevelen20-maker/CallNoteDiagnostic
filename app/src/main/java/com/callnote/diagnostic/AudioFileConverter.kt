package com.callnote.diagnostic

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioFileConverter {
    fun toWhisperWav(input: File, output: File) {
        val extractor = MediaExtractor()
        val pcm = ByteArrayOutputStream()
        try {
            extractor.setDataSource(input.absolutePath)
            var track = -1
            for (index in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = index
                    break
                }
            }
            require(track >= 0) { "В файле нет аудиодорожки" }
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Неизвестный аудиоформат")
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val decoder = MediaCodec.createDecoderByType(mime)
            extractor.selectTrack(track)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (index >= 0) {
                        decoder.getOutputBuffer(index)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            pcm.write(bytes)
                        }
                        decoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            decoder.stop()
            decoder.release()
            val source = pcm.toByteArray().toShortArray()
            val mono = FloatArray(source.size / channels)
            for (i in mono.indices) {
                var sum = 0f
                for (channel in 0 until channels) sum += source[i * channels + channel] / 32768f
                mono[i] = sum / channels
            }
            val outputSamples = if (sourceRate == 16_000) mono else {
                val count = (mono.size.toLong() * 16_000 / sourceRate).toInt()
                FloatArray(count) { index ->
                    val position = index.toDouble() * sourceRate / 16_000.0
                    val left = position.toInt().coerceIn(0, mono.lastIndex)
                    val right = (left + 1).coerceAtMost(mono.lastIndex)
                    (mono[left] + (mono[right] - mono[left]) * (position - left)).toFloat()
                }
            }
            val peak = outputSamples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            require(outputSamples.isNotEmpty() && peak >= 0.003f) {
                "В записи не найден голос: аудиосигнал пустой"
            }
            val outputBytes = ByteArray(outputSamples.size * 2)
            val target = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN)
            outputSamples.forEach { target.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
            WaveFile.write(output, outputBytes, 16_000, 1, 2)
        } finally {
            extractor.release()
        }
    }

    private fun ByteArray.toShortArray(): ShortArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(size / 2) { buffer.short }
    }
}

private object WaveFile {
    fun write(file: File, data: ByteArray, sampleRate: Int, channels: Int, bytesPerSample: Int) {
        file.outputStream().use { out ->
            fun putInt(value: Int) = out.write(byteArrayOf((value and 255).toByte(), (value shr 8 and 255).toByte(), (value shr 16 and 255).toByte(), (value shr 24 and 255).toByte()))
            fun putShort(value: Int) = out.write(byteArrayOf((value and 255).toByte(), (value shr 8 and 255).toByte()))
            out.write("RIFF".toByteArray()); putInt(36 + data.size); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels); putInt(sampleRate)
            putInt(sampleRate * channels * bytesPerSample); putShort(channels * bytesPerSample); putShort(bytesPerSample * 8)
            out.write("data".toByteArray()); putInt(data.size); out.write(data)
        }
    }
}

