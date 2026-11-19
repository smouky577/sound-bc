package com.drbaysinger.audiobc.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmAudio(val samples: FloatArray, val sampleRate: Int)

/**
 * Decode one audio file to mono Float PCM with MediaExtractor and MediaCodec.
 * Uncompressed WAV (audio/raw) is read straight from the extractor.
 */
fun decodeToMonoFloat(context: Context, uri: Uri): PcmAudio {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    var started = false
    try {
        extractor.setDataSource(context, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                track = i
                format = candidate
                break
            }
        }
        if (track < 0 || format == null) error("No audio track.")
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime == "audio/raw" || mime == "audio/wav" || mime == "audio/x-wav") {
            return readRaw(extractor, format)
        }
        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        started = true
        return decodeCodec(extractor, codec, format)
    } finally {
        if (started) {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        extractor.release()
    }
}

private fun readRaw(extractor: MediaExtractor, format: MediaFormat): PcmAudio {
    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
    val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        AudioFormat.ENCODING_PCM_16BIT
    }
    val out = FloatBuf()
    val buffer = ByteBuffer.allocate(256 * 1024)
    while (true) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        appendPcm(buffer, channels, encoding, out)
        extractor.advance()
    }
    if (out.size == 0) error("Empty recording.")
    return PcmAudio(out.toArray(), rate)
}

private fun decodeCodec(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): PcmAudio {
    var rate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
    var encoding = AudioFormat.ENCODING_PCM_16BIT
    val info = MediaCodec.BufferInfo()
    val out = FloatBuf()
    var inputDone = false
    var outputDone = false
    var spins = 0
    while (!outputDone) {
        if (!inputDone) {
            val inIndex = codec.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val buffer = codec.getInputBuffer(inIndex) ?: error("Empty decoder input.")
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    val time = extractor.sampleTime.coerceAtLeast(0L)
                    codec.queueInputBuffer(inIndex, 0, size, time, 0)
                    extractor.advance()
                }
                spins = 0
            }
        }
        when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> {
                spins++
                if (spins > 800 && inputDone) error("Decoder stalled.")
            }
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val changed = codec.outputFormat
                rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    encoding = changed.getInteger(MediaFormat.KEY_PCM_ENCODING)
                }
                spins = 0
            }
            else -> if (outIndex >= 0) {
                val buffer = codec.getOutputBuffer(outIndex)
                val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (buffer != null && info.size > 0 && !config) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    appendPcm(buffer, channels, encoding, out)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                spins = 0
            }
        }
    }
    if (out.size == 0) error("Empty recording.")
    return PcmAudio(out.toArray(), rate)
}

private fun appendPcm(buffer: ByteBuffer, channels: Int, encoding: Int, out: FloatBuf) {
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val channelCount = channels.coerceAtLeast(1)
    when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> {
            val frames = buffer.remaining() / (4 * channelCount)
            repeat(frames) {
                var sum = 0.0
                repeat(channelCount) { sum += buffer.float.toDouble() }
                out.add((sum / channelCount).toFloat())
            }
        }
        AudioFormat.ENCODING_PCM_8BIT -> {
            val frames = buffer.remaining() / channelCount
            repeat(frames) {
                var sum = 0.0
                repeat(channelCount) {
                    sum += (buffer.get().toInt() and 0xFF) / 128.0 - 1.0
                }
                out.add((sum / channelCount).toFloat())
            }
        }
        else -> {
            val frames = buffer.remaining() / (2 * channelCount)
            repeat(frames) {
                var sum = 0.0
                repeat(channelCount) { sum += buffer.short.toDouble() / 32768.0 }
                out.add((sum / channelCount).toFloat())
            }
        }
    }
}

private class FloatBuf {
    private var data = FloatArray(65_536)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
