package com.drbaysinger.audiobc.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** Live state shown on the Record button and in the panel while recording. */
data class LiveState(
    val seconds: Double,
    val shots: Int,
    /** Last ~1.5 s of the 1 ms RMS envelope, for the live panel. */
    val tail: FloatArray,
    val threshold: Float,
    /** Rolling spectrogram of the last ~1.5 s, only while the Spectrogram view is selected. */
    val spectrogram: Spectrogram? = null,
)

/** One shot found while recording: onset of the blast and, if heard, of the gong. */
data class LiveShot(val shotTime: Double, var gongTime: Double?)

/**
 * Records a whole session at 48 kHz mono and detects shots while it runs.
 *
 * Detection runs on a 1 ms RMS envelope of a first-order 300 Hz high-pass.
 * The threshold follows the noise floor (median + N·MAD of the last 2 s).
 * Each loud onset is either the shot, or the gong of the pending shot when it lands
 * inside the expected window D/c + D/V0 (± slack) after it.
 */
class Recorder(private val context: Context) {
    val sampleRate = 48_000
    private val chunks = ArrayList<ShortArray>()
    private var totalSamples = 0
    private val blocks = FloatGrow()
    private val shots = ArrayList<LiveShot>()
    private var record: AudioRecord? = null
    private var job: Job? = null

    /** Expected shot→gong delay window in seconds, updated from the current inputs. */
    @Volatile var gongWindow: ClosedFloatingPointRange<Double> = 0.25..0.75

    /** When true the live state also carries a rolling spectrogram. */
    @Volatile var liveSpectrogram: Boolean = false

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onLive: (LiveState) -> Unit): Boolean {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (min <= 0) return false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val recorder = try {
            AudioRecord(source, sampleRate, channel, encoding, maxOf(min, sampleRate / 5 * 2))
        } catch (_: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        record = recorder
        chunks.clear()
        totalSamples = 0
        blocks.clear()
        synchronized(shots) { shots.clear() }
        recorder.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(sampleRate / 50)
            // 1st-order high-pass at 300 Hz
            val rc = 1.0 / (2.0 * PI * 300.0)
            val dt = 1.0 / sampleRate
            val alpha = rc / (rc + dt)
            var prevIn = 0.0
            var prevOut = 0.0
            val blockLen = sampleRate / 1000
            var acc = 0.0
            var accN = 0
            var lastLive = 0L
            val detector = Detector()
            while (isActive) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                chunks += buffer.copyOf(n)
                for (i in 0 until n) {
                    val x = buffer[i] / 32768.0
                    val y = alpha * (prevOut + x - prevIn)
                    prevIn = x
                    prevOut = y
                    acc += y * y
                    accN++
                    if (accN == blockLen) {
                        blocks.add(sqrt(acc / accN).toFloat())
                        acc = 0.0
                        accN = 0
                        detector.step(blocks.size - 1)
                    }
                }
                totalSamples += n
                val now = System.currentTimeMillis()
                if (now - lastLive > 100) {
                    lastLive = now
                    val tailLen = 1500
                    val from = (blocks.size - tailLen).coerceAtLeast(0)
                    onLive(
                        LiveState(
                            seconds = totalSamples.toDouble() / sampleRate,
                            shots = synchronized(shots) { shots.size },
                            tail = blocks.slice(from, blocks.size),
                            threshold = detector.threshold.toFloat(),
                            spectrogram = if (liveSpectrogram) liveSpec() else null,
                        ),
                    )
                }
            }
        }
        return true
    }

    /** Spectrogram of the last 1.5 s of raw audio, on an absolute level scale so silence stays dark. */
    private fun liveSpec(): Spectrogram {
        val want = (sampleRate * 1.5).toInt()
        val raw = FloatArray(want)
        var at = want
        var c = chunks.size - 1
        while (c >= 0 && at > 0) {
            val chunk = chunks[c]
            var i = chunk.size - 1
            while (i >= 0 && at > 0) raw[--at] = chunk[i--] / 32768f
            c--
        }
        return computeSpectrogram(raw, sampleRate, 0.0, 1.5, cols = 150, rows = 96, minPeak = LIVE_MIN_PEAK, rangeDb = 60.0)
    }

    /** Stops and returns the full recording plus the shots found while it ran. */
    suspend fun stop(): Pair<FloatArray, List<LiveShot>> {
        val running = job
        job = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        running?.cancel()
        running?.join()
        record?.release()
        record = null
        val out = FloatArray(totalSamples)
        var at = 0
        for (c in chunks) {
            for (s in c) out[at++] = s / 32768f
        }
        chunks.clear()
        return out to synchronized(shots) { shots.map { it.copy() } }
    }

    private inner class Detector {
        var threshold = Double.MAX_VALUE
        private var lastStats = -1
        private var inEvent = false
        private var quiet = 0
        private var pending: LiveShot? = null

        fun step(i: Int) {
            // Noise floor every 250 ms from the last 2 s of envelope.
            if (i - lastStats >= 250 && i >= 300) {
                lastStats = i
                val from = (i - 2000).coerceAtLeast(0)
                val window = blocks.slice(from, i + 1)
                val med = median(window)
                val dev = FloatArray(window.size) { abs(window[it] - med.toFloat()) }
                val mad = median(dev)
                threshold = maxOf(med + AUTO_MAD_FACTOR * mad, 4.0 * med, 1e-4)
            }
            val v = blocks[i]
            if (!inEvent) {
                if (v >= threshold) {
                    inEvent = true
                    quiet = 0
                    onset(i / 1000.0)
                }
            } else {
                // Event ends after 30 ms below threshold.
                if (v < threshold) quiet++ else quiet = 0
                if (quiet >= 30) inEvent = false
            }
        }

        private fun onset(t: Double) {
            val p = pending
            if (p != null) {
                val dt = t - p.shotTime
                val win = gongWindow
                if (dt < win.start) return // echo / ringing of the blast
                if (dt <= win.endInclusive && p.gongTime == null) {
                    p.gongTime = t
                    pending = null
                    return
                }
            }
            val shot = LiveShot(t, null)
            synchronized(shots) { shots += shot }
            pending = shot
        }
    }
}

/**
 * Splits a session recording into one segment per detected shot:
 * from 150 ms before the blast to the gong + 400 ms (or the expected gong time + 600 ms),
 * never into the next shot.
 */
fun splitSession(
    samples: FloatArray,
    sampleRate: Int,
    shots: List<LiveShot>,
    expectedGong: Double,
): List<Pair<Double, FloatArray>> {
    if (shots.isEmpty()) return listOf(0.0 to samples)
    val duration = samples.size.toDouble() / sampleRate
    return shots.mapIndexed { i, shot ->
        val start = (shot.shotTime - 0.15).coerceAtLeast(0.0)
        var end = (shot.gongTime?.plus(0.4) ?: (shot.shotTime + expectedGong + 0.6))
        val next = shots.getOrNull(i + 1)?.shotTime
        if (next != null) end = minOf(end, next - 0.05)
        end = end.coerceAtMost(duration).coerceAtLeast(start + 0.05)
        val a = (start * sampleRate).toInt().coerceIn(0, samples.size)
        val b = (end * sampleRate).toInt().coerceIn(a, samples.size)
        start to samples.copyOfRange(a, b)
    }
}

private class FloatGrow {
    private var data = FloatArray(1 shl 16)
    @Volatile var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]
    fun slice(from: Int, to: Int): FloatArray = data.copyOfRange(from, to)
    fun clear() {
        size = 0
    }
}
