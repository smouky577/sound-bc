package com.drbaysinger.audiobc.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

private data class Biquad(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
)

/**
 * Butterworth high-pass, even order, bilinear transform with pre-warping.
 * Sections are monic in the analog s-plane so the passband gain is 1.
 */
private fun butterworthHighpass(order: Int, cutoffHz: Double, sampleRate: Double): List<Biquad> {
    require(order >= 2 && order % 2 == 0)
    require(cutoffHz > 0.0 && cutoffHz < sampleRate / 2.0)
    val omega = 2.0 * tan(PI * cutoffHz / sampleRate)
    val sections = ArrayList<Biquad>(order / 2)
    for (k in 0 until order / 2) {
        val theta = PI * (2.0 * k + 1.0) / (2.0 * order)
        val damping = 2.0 * omega * cos(theta)
        val constant = omega * omega
        val a0 = 4.0 + 2.0 * damping + constant
        sections += Biquad(
            b0 = 4.0 / a0,
            b1 = -8.0 / a0,
            b2 = 4.0 / a0,
            a1 = (-8.0 + 2.0 * constant) / a0,
            a2 = (4.0 - 2.0 * damping + constant) / a0,
        )
    }
    return sections
}

private fun applyBiquad(x: FloatArray, q: Biquad) {
    var z1 = 0.0
    var z2 = 0.0
    for (i in x.indices) {
        val input = x[i].toDouble()
        val output = q.b0 * input + z1
        z1 = q.b1 * input - q.a1 * output + z2
        z2 = q.b2 * input - q.a2 * output
        x[i] = output.toFloat()
    }
}

private fun reverse(x: FloatArray) {
    var i = 0
    var j = x.lastIndex
    while (i < j) {
        val tmp = x[i]
        x[i] = x[j]
        x[j] = tmp
        i++
        j--
    }
}

/** Odd extension, the same edge rule scipy uses for filtfilt. */
private fun oddPad(x: FloatArray, pad: Int): FloatArray {
    val n = pad.coerceAtMost(x.size - 1)
    if (n < 1) return x.copyOf()
    val out = FloatArray(x.size + 2 * n)
    for (i in 0 until n) {
        out[i] = (2.0 * x[0] - x[n - i]).toFloat()
    }
    x.copyInto(out, n)
    val last = x.lastIndex
    for (i in 0 until n) {
        out[n + x.size + i] = (2.0 * x[last] - x[last - 1 - i]).toFloat()
    }
    return out
}

/** 4th-order Butterworth high-pass at 300 Hz, applied forward and back so the phase is zero. */
fun zeroPhaseHighpass(samples: FloatArray, sampleRate: Int): FloatArray {
    if (samples.size < 8 || sampleRate <= 0) return samples.copyOf()
    val sections = butterworthHighpass(4, 300.0, sampleRate.toDouble())
    val pad = (sampleRate * 0.05).toInt().coerceAtLeast(64).coerceAtMost(samples.size - 1)
    val padded = oddPad(samples, pad)
    fun cascade() {
        for (section in sections) applyBiquad(padded, section)
    }
    cascade()
    reverse(padded)
    cascade()
    reverse(padded)
    return padded.copyOfRange(pad, pad + samples.size)
}

/** Centered RMS envelope. The window is 0.5 ms. */
fun rmsEnvelope(samples: FloatArray, sampleRate: Int): FloatArray {
    val window = maxOf(1, (0.0005 * sampleRate).roundToInt())
    val out = FloatArray(samples.size)
    val prefix = DoubleArray(samples.size + 1)
    for (i in samples.indices) {
        val value = samples[i].toDouble()
        prefix[i + 1] = prefix[i] + value * value
    }
    for (i in samples.indices) {
        var start = i - window / 2
        var end = start + window
        if (start < 0) {
            start = 0
            end = minOf(samples.size, window)
        }
        if (end > samples.size) {
            end = samples.size
            start = maxOf(0, end - window)
        }
        val count = (end - start).coerceAtLeast(1)
        out[i] = sqrt((prefix[end] - prefix[start]) / count).toFloat()
    }
    return out
}

fun median(values: FloatArray): Double {
    if (values.isEmpty()) return 0.0
    val copy = values.copyOf()
    copy.sort()
    val mid = copy.size / 2
    return if (copy.size % 2 == 1) {
        copy[mid].toDouble()
    } else {
        (copy[mid - 1].toDouble() + copy[mid].toDouble()) / 2.0
    }
}

class Processed(
    val envelope: FloatArray,
    val t1: Double,
    val t2: Double,
    val noise: Double,
    val threshold: Double,
    val muzzleFound: Boolean,
    val gongFound: Boolean,
)

/**
 * High-pass, RMS envelope, then the two onsets.
 * t1 is the first threshold crossing, walked back to where the envelope falls under 2×noise
 * (at most 20 ms). t2 is the same, on the first crossing after t1 + D/c + D/V0 − 0.01 s.
 */
fun processRecording(
    samples: FloatArray,
    sampleRate: Int,
    k: Double?,
    distanceM: Double,
    muzzleVelocity: Double,
    soundSpeed: Double,
): Processed {
    val filtered = zeroPhaseHighpass(samples, sampleRate)
    val envelope = rmsEnvelope(filtered, sampleRate)
    val noise = median(envelope)
    val threshold = if (k != null) k * noise else autoThreshold(envelope, noise)
    val duration = if (sampleRate > 0) envelope.size.toDouble() / sampleRate else 0.0
    val fallbackT2 = (distanceM / soundSpeed + distanceM / muzzleVelocity).coerceIn(0.0, duration)
    if (noise <= 0.0 || !threshold.isFinite() || threshold <= 0.0) {
        return Processed(envelope, 0.0, fallbackT2, noise, threshold, false, false)
    }
    val muzzle = firstCrossing(envelope, 0, threshold)
    if (muzzle < 0) {
        return Processed(envelope, 0.0, fallbackT2, noise, threshold, false, false)
    }
    val t1Index = refineOnset(envelope, muzzle, sampleRate, noise)
    val t1 = t1Index.toDouble() / sampleRate
    val gate = t1 + distanceM / soundSpeed + distanceM / muzzleVelocity - 0.01
    var search = floor(gate * sampleRate).toInt() + 1
    if (search < 0) search = 0
    val gong = firstCrossing(envelope, search, threshold)
    if (gong < 0) {
        val parked = (t1 + distanceM / soundSpeed + distanceM / muzzleVelocity).coerceIn(0.0, duration)
        return Processed(envelope, t1, parked, noise, threshold, true, false)
    }
    val t2Index = refineOnset(envelope, gong, sampleRate, noise)
    return Processed(
        envelope = envelope,
        t1 = t1,
        t2 = t2Index.toDouble() / sampleRate,
        noise = noise,
        threshold = threshold,
        muzzleFound = true,
        gongFound = true,
    )
}

/**
 * Automatic threshold from the noise floor: median + 15·MAD of the envelope,
 * never below 4× the median. Robust to the shot and gong themselves, which are
 * a tiny fraction of the samples.
 */
fun autoThreshold(envelope: FloatArray, noise: Double = median(envelope)): Double {
    if (envelope.isEmpty()) return 0.0
    val deviations = FloatArray(envelope.size) { kotlin.math.abs(envelope[it] - noise.toFloat()) }
    val mad = median(deviations)
    return maxOf(noise + AUTO_MAD_FACTOR * mad, 4.0 * noise)
}

const val AUTO_MAD_FACTOR = 15.0

private fun firstCrossing(envelope: FloatArray, start: Int, threshold: Double): Int {
    val from = start.coerceAtLeast(0)
    for (i in from until envelope.size) {
        val above = envelope[i].toDouble() >= threshold
        val wasBelow = i == 0 || envelope[i - 1].toDouble() < threshold
        if (above && wasBelow) return i
    }
    return -1
}

private fun refineOnset(envelope: FloatArray, crossing: Int, sampleRate: Int, noise: Double): Int {
    val floorLevel = 2.0 * noise
    val maxBack = (0.020 * sampleRate).roundToInt().coerceAtLeast(1)
    val limit = (crossing - maxBack).coerceAtLeast(0)
    var index = crossing
    while (index > limit && envelope[index].toDouble() >= floorLevel) {
        index--
    }
    return index
}

/** Max-pooled envelope so a long recording still shows the impulses. */
fun downsamplePeaks(samples: FloatArray, buckets: Int): FloatArray {
    if (samples.size <= buckets || buckets < 2) return samples
    val out = FloatArray(buckets)
    val step = samples.size.toDouble() / buckets
    for (i in 0 until buckets) {
        val start = (i * step).toInt()
        var end = ((i + 1) * step).toInt()
        if (end <= start) end = start + 1
        if (end > samples.size) end = samples.size
        var peak = 0f
        for (j in start until end) {
            if (samples[j] > peak) peak = samples[j]
        }
        out[i] = peak
    }
    return out
}
