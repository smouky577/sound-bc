package com.drbaysinger.audiobc.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.exp
import kotlin.math.sin

/** Magnitude grid, column-major: value(col, row) = data[col * rows + row], 0…1, row 0 = 20 Hz. */
class Spectrogram(val cols: Int, val rows: Int, val data: FloatArray, val start: Double, val end: Double)

const val SPEC_F_MIN = 20.0
const val SPEC_F_MAX = 16_000.0

/** Hann-windowed 2048-point power of a −40 dBFS sine: floor for the live normalization. */
const val LIVE_MIN_PEAK = 262_144.0 * 1e-4

/**
 * STFT (Hann, 2048 points) of samples[start…end], resampled to a log frequency axis
 * 20 Hz – 16 kHz, in dB normalized to the loudest bin with an 80 dB range.
 */
fun computeSpectrogram(
    samples: FloatArray,
    sampleRate: Int,
    start: Double,
    end: Double,
    cols: Int = 360,
    rows: Int = 128,
    /** Lower bound for the normalization peak (power); keeps quiet live audio from being blown up. */
    minPeak: Double = 1e-12,
    /** Displayed dynamic range below the peak, dB. */
    rangeDb: Double = 80.0,
): Spectrogram {
    val n = 2048
    val window = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / (n - 1)) }
    val a = (start * sampleRate).toInt().coerceIn(0, samples.size)
    val b = (end * sampleRate).toInt().coerceIn(a, samples.size)
    val span = (b - a).coerceAtLeast(1)
    val nyquist = sampleRate / 2.0
    val fMax = minOf(SPEC_F_MAX, nyquist)
    val binHz = sampleRate.toDouble() / n
    val out = FloatArray(cols * rows)
    val re = DoubleArray(n)
    val im = DoubleArray(n)
    val mags = DoubleArray(n / 2)
    var peak = minPeak
    for (c in 0 until cols) {
        val center = a + (span.toDouble() * (c + 0.5) / cols).toInt()
        val from = center - n / 2
        for (i in 0 until n) {
            val idx = from + i
            re[i] = if (idx in samples.indices) samples[idx] * window[i] else 0.0
            im[i] = 0.0
        }
        fft(re, im)
        for (k in 0 until n / 2) mags[k] = re[k] * re[k] + im[k] * im[k]
        for (r in 0 until rows) {
            val f0 = SPEC_F_MIN * exp(ln(fMax / SPEC_F_MIN) * r / rows)
            val f1 = SPEC_F_MIN * exp(ln(fMax / SPEC_F_MIN) * (r + 1) / rows)
            val k0 = (f0 / binHz).toInt().coerceIn(0, n / 2 - 1)
            val k1 = (f1 / binHz).toInt().coerceIn(k0, n / 2 - 1)
            var m = 0.0
            for (k in k0..k1) if (mags[k] > m) m = mags[k]
            out[c * rows + r] = m.toFloat()
            if (m > peak) peak = m
        }
    }
    val floorDb = -rangeDb
    for (i in out.indices) {
        val db = 10.0 * log10((out[i] / peak).coerceAtLeast(1e-12))
        out[i] = ((db - floorDb) / -floorDb).coerceIn(0.0, 1.0).toFloat()
    }
    return Spectrogram(cols, rows, out, start, end)
}

/** In-place radix-2 FFT. */
private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            var t = re[i]; re[i] = re[j]; re[j] = t
            t = im[i]; im[i] = im[j]; im[j] = t
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wr = cos(ang)
        val wi = sin(ang)
        var i = 0
        while (i < n) {
            var cr = 1.0
            var ci = 0.0
            for (k in 0 until len / 2) {
                val ur = re[i + k]
                val ui = im[i + k]
                val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                re[i + k] = ur + vr
                im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr
                im[i + k + len / 2] = ui - vi
                val nr = cr * wr - ci * wi
                ci = cr * wi + ci * wr
                cr = nr
            }
            i += len
        }
        len = len shl 1
    }
}
