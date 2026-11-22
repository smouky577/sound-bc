package com.drbaysinger.audiobc.ballistics

import kotlin.math.PI

/** lb/in² → kg/m². Specified factor: 1 lb / 1 in². */
const val BC_LB_IN2_TO_KG_M2 = 703.0696

const val BC_MIN = 0.02
const val BC_MAX = 5.0

data class Flight(val time: Double, val impactSpeed: Double)

data class ModelResult(
    val bc: Double?,
    val impactSpeed: Double?,
    val outOfRange: Boolean,
)

/**
 * Delay from the muzzle blast to the gong sound (t2 − t1), in milliseconds,
 * for which the solved BC stays inside 0.02…5.0 lb/in².
 * The lower bound is the flight at BC 5.0, the upper bound at BC 0.02.
 * [maxMs] is null when even BC 0.02 never reaches the gong.
 */
data class BcHitWindow(val minMs: Double, val maxMs: Double?)

fun bcHitWindowMs(
    distanceM: Double,
    muzzleVelocity: Double,
    density: Double,
    soundSpeed: Double,
    microphoneM: Double,
    table: DragTable,
): BcHitWindow? {
    if (distanceM <= 0.0 || muzzleVelocity <= 1.0 || density <= 0.0 || soundSpeed <= 0.0) return null
    val fast = simulateFlight(distanceM, muzzleVelocity, density, soundSpeed, BC_MAX, table).time
    if (!fast.isFinite() || fast <= 0.0) return null
    val slow = simulateFlight(distanceM, muzzleVelocity, density, soundSpeed, BC_MIN, table).time
    val acoustic = distanceM / soundSpeed - microphoneM / soundSpeed
    val minMs = ((fast + acoustic) * 1000.0).coerceAtLeast(0.0)
    val maxMs = if (slow.isFinite()) ((slow + acoustic) * 1000.0).coerceAtLeast(minMs) else null
    return BcHitWindow(minMs, maxMs)
}

/**
 * Time of flight from the two acoustic marks.
 * TOF = (t2 − t1) − D/c + microphone/c.
 * The microphone is beside the shooter: the blast is late by microphone/c,
 * and the gong sound travels D back to the microphone.
 */
fun timeOfFlightSeconds(
    t1: Double,
    t2: Double,
    distanceM: Double,
    soundSpeed: Double,
    microphoneM: Double,
): Double {
    return (t2 - t1) - distanceM / soundSpeed + microphoneM / soundSpeed
}

/**
 * Point-mass flight integrated along distance with a fixed step of 0.1 m.
 * Deceleration a = rho * v² * Cd(M) * (π/4) / (2 * BC_SI), M = v/c,
 * BC_SI = BC(lb/in²) * 703.0696.
 * dv = −a * dx / v, dt = dx / v.
 */
fun simulateFlight(
    distanceM: Double,
    muzzleVelocity: Double,
    density: Double,
    soundSpeed: Double,
    bcLbPerIn2: Double,
    table: DragTable,
): Flight {
    val bcSi = bcLbPerIn2 * BC_LB_IN2_TO_KG_M2
    if (distanceM <= 0.0 || muzzleVelocity <= 1.0 || bcSi <= 0.0 || soundSpeed <= 0.0 || density <= 0.0) {
        return Flight(Double.NaN, Double.NaN)
    }
    var x = 0.0
    var velocity = muzzleVelocity
    var time = 0.0
    val stepMax = 0.1
    while (x < distanceM - 1e-9) {
        val step = minOf(stepMax, distanceM - x)
        if (velocity < 1.0) return Flight(Double.POSITIVE_INFINITY, 0.0)
        val mach = velocity / soundSpeed
        val cd = table.cd(mach)
        val drag = density * velocity * velocity * cd * (PI / 4.0) / (2.0 * bcSi)
        val dt = step / velocity
        val next = velocity - drag * dt
        if (!next.isFinite() || next <= 0.5) return Flight(Double.POSITIVE_INFINITY, 0.0)
        velocity = next
        time += dt
        x += step
    }
    return Flight(time, velocity)
}

/**
 * BC in lb/in² whose simulated time of flight matches [measuredSeconds].
 * Higher BC means less drag and a shorter time. Search is a bisection on 0.02…5.0.
 */
fun solveBc(
    distanceM: Double,
    muzzleVelocity: Double,
    density: Double,
    soundSpeed: Double,
    measuredSeconds: Double,
    table: DragTable,
): ModelResult {
    if (!measuredSeconds.isFinite() || measuredSeconds <= 0.0) {
        return ModelResult(null, null, true)
    }
    fun timeAt(bc: Double): Double {
        return simulateFlight(distanceM, muzzleVelocity, density, soundSpeed, bc, table).time
    }
    val fastest = timeAt(BC_MAX)
    val slowest = timeAt(BC_MIN)
    if (!fastest.isFinite() || measuredSeconds < fastest - 1e-7) {
        return ModelResult(null, null, true)
    }
    if (slowest.isFinite() && measuredSeconds > slowest + 1e-7) {
        return ModelResult(null, null, true)
    }
    var lo = BC_MIN
    var hi = BC_MAX
    repeat(56) {
        val mid = (lo + hi) / 2.0
        val time = timeAt(mid)
        if (!time.isFinite() || time > measuredSeconds) lo = mid else hi = mid
    }
    val bc = (lo + hi) / 2.0
    val flight = simulateFlight(distanceM, muzzleVelocity, density, soundSpeed, bc, table)
    return ModelResult(bc, flight.impactSpeed, false)
}

data class ShotMath(
    val timeOfFlight: Double?,
    val averageSpeed: Double?,
    val g1: ModelResult?,
    val g7: ModelResult?,
    val badOrder: Boolean,
    val nonPositive: Boolean,
)

fun computeShot(
    t1: Double,
    t2: Double,
    muzzleVelocity: Double,
    distanceM: Double,
    temperatureC: Double,
    pressureHpa: Double,
    humidityPercent: Double,
    microphoneM: Double,
    g1: DragTable,
    g7: DragTable,
): ShotMath {
    if (t2 <= t1) {
        return ShotMath(null, null, null, null, badOrder = true, nonPositive = false)
    }
    val sound = Atmosphere.speedOfSound(temperatureC)
    val tof = timeOfFlightSeconds(t1, t2, distanceM, sound, microphoneM)
    if (!tof.isFinite() || tof <= 0.0) {
        return ShotMath(null, null, null, null, badOrder = false, nonPositive = true)
    }
    val density = Atmosphere.density(temperatureC, pressureHpa, humidityPercent)
    val g1Result = solveBc(distanceM, muzzleVelocity, density, sound, tof, g1)
    val g7Result = solveBc(distanceM, muzzleVelocity, density, sound, tof, g7)
    return ShotMath(
        timeOfFlight = tof,
        averageSpeed = distanceM / tof,
        g1 = g1Result,
        g7 = g7Result,
        badOrder = false,
        nonPositive = false,
    )
}

data class Stats(val mean: Double, val sampleSd: Double?, val count: Int)

fun statsOf(values: List<Double>): Stats? {
    if (values.isEmpty()) return null
    val mean = values.sum() / values.size
    if (values.size < 2) return Stats(mean, null, values.size)
    val variance = values.sumOf { val d = it - mean; d * d } / (values.size - 1)
    return Stats(mean, kotlin.math.sqrt(variance), values.size)
}
