package com.drbaysinger.audiobc

import com.drbaysinger.audiobc.audio.processRecording
import com.drbaysinger.audiobc.audio.rmsEnvelope
import com.drbaysinger.audiobc.audio.zeroPhaseHighpass
import com.drbaysinger.audiobc.ballistics.Atmosphere
import com.drbaysinger.audiobc.ballistics.DragTable
import com.drbaysinger.audiobc.ballistics.simulateFlight
import com.drbaysinger.audiobc.ballistics.solveBc
import com.drbaysinger.audiobc.ballistics.timeOfFlightSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class SignalAndFlightTest {
    @Test
    fun highpassIsZeroPhaseAndCutsLowFrequencies() {
        val sampleRate = 48_000
        assertTrue(gain(4_000.0, sampleRate) in 0.94..1.05)
        assertTrue(gain(300.0, sampleRate) in 0.40..0.60)
        assertTrue(gain(30.0, sampleRate) < 0.05)

        val n = sampleRate
        val input = FloatArray(n) { i -> sin(2.0 * PI * 4000.0 * i / sampleRate).toFloat() }
        val output = zeroPhaseHighpass(input, sampleRate)
        val peak = 3 + 12 * 2000
        assertTrue(output[peak] > 0.8f)
    }

    @Test
    fun rmsOfAConstantIsTheConstant() {
        val samples = FloatArray(2000) { 3f }
        val envelope = rmsEnvelope(samples, 48_000)
        assertEquals(3.0, envelope[1000].toDouble(), 1e-4)
    }

    @Test
    fun syntheticClicksRecoverTheTimeOfFlight() {
        val sampleRate = 48_000
        val distance = 100.0
        val muzzle = 800.0
        val temperature = 15.0
        val microphone = 0.5
        val k = 12.0
        val sound = Atmosphere.speedOfSound(temperature)
        val flight = distance / muzzle + 0.040
        val separation = flight + distance / sound - microphone / sound
        val gate = distance / sound + distance / muzzle - 0.01
        assertTrue(separation > gate + 0.030)

        val first = (0.20 * sampleRate).toInt()
        val second = first + (separation * sampleRate).toInt()
        val length = second + sampleRate / 5
        var seed = 17
        val samples = FloatArray(length) {
            seed = seed * 1103515245 + 12345
            val unit = ((seed ushr 16) and 0x7FFF) / 32768.0
            ((unit - 0.5) * 2.0 * 1.5e-4).toFloat()
        }
        click(samples, first)
        click(samples, second)

        val processed = processRecording(samples, sampleRate, k, distance, muzzle, sound)
        assertTrue(processed.muzzleFound)
        assertTrue(processed.gongFound)
        val recovered = timeOfFlightSeconds(processed.t1, processed.t2, distance, sound, microphone)
        assertEquals(
            "t1=${processed.t1} t2=${processed.t2} recovered=$recovered",
            flight,
            recovered,
            5e-4,
        )
    }

    @Test
    fun timeOfFlightFormula() {
        val tof = timeOfFlightSeconds(1.0, 1.5, 100.0, 340.0, 0.5)
        assertEquals(0.5 - 99.5 / 340.0, tof, 1e-12)
    }

    @Test
    fun speedOfSoundAndDryDensity() {
        val sound = Atmosphere.speedOfSound(15.0)
        assertEquals(20.05 * sqrt(288.15), sound, 1e-9)
        val density = Atmosphere.density(15.0, 1013.25, 0.0)
        assertEquals(101325.0 / (287.058 * 288.15), density, 1e-6)
    }

    @Test
    fun bisectionRecoversTheBcUsedToMakeTheFlight() {
        val g1 = DragTable.parse(asset("g1_cd.csv"))
        val g7 = DragTable.parse(asset("g7_cd.csv"))
        val distance = 300.0
        val muzzle = 850.0
        val density = Atmosphere.density(15.0, 1013.25, 40.0)
        val sound = Atmosphere.speedOfSound(15.0)
        for ((table, bc) in listOf(g1 to 0.450, g7 to 0.220)) {
            val flight = simulateFlight(distance, muzzle, density, sound, bc, table)
            val solved = solveBc(distance, muzzle, density, sound, flight.time, table)
            assertEquals(bc, solved.bc ?: Double.NaN, 1e-3)
            assertTrue((solved.impactSpeed ?: 0.0) < muzzle)
            assertTrue((solved.impactSpeed ?: 0.0) > 100.0)
        }
        val tooFast = solveBc(distance, muzzle, density, sound, distance / muzzle * 0.5, g1)
        assertTrue(tooFast.outOfRange)
        val tooSlow = solveBc(distance, muzzle, density, sound, 30.0, g1)
        assertTrue(tooSlow.outOfRange)
    }

    private fun gain(frequency: Double, sampleRate: Int): Double {
        val input = FloatArray(sampleRate) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat()
        }
        val output = zeroPhaseHighpass(input, sampleRate)
        return rms(output, sampleRate / 4, 3 * sampleRate / 4) /
            rms(input, sampleRate / 4, 3 * sampleRate / 4)
    }

    private fun rms(samples: FloatArray, from: Int, until: Int): Double {
        var sum = 0.0
        for (i in from until until) sum += samples[i] * samples[i]
        return sqrt(sum / (until - from))
    }

    private fun click(samples: FloatArray, at: Int) {
        for (i in 0 until 8) {
            samples[at + i] = if (i % 2 == 0) 0.95f else -0.95f
        }
    }

    private fun asset(name: String): String {
        val file = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.isFile }
            ?: error("Missing $name")
        return file.readText()
    }
}
