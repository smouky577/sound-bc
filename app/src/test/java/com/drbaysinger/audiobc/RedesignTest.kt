package com.drbaysinger.audiobc

import com.drbaysinger.audiobc.audio.LiveShot
import com.drbaysinger.audiobc.audio.autoThreshold
import com.drbaysinger.audiobc.audio.median
import com.drbaysinger.audiobc.audio.processRecording
import com.drbaysinger.audiobc.audio.splitSession
import com.drbaysinger.audiobc.ballistics.energyJoules
import com.drbaysinger.audiobc.ballistics.sectionalDensity
import com.drbaysinger.audiobc.net.Ht3006
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RedesignTest {
    @Test
    fun parsesChronoShotList() {
        val html = "<html><body><h2>Speed 815.2 m/s</h2><div>1: 812.4</div><div>2: 808.9</div>" +
            "<div>3: 815,2</div><p>FireRate 0.0</p></body></html>"
        val text = Ht3006.htmlToText(html)
        assertTrue(Ht3006.looksLikeChrono(text))
        assertEquals(listOf(812.4, 808.9, 815.2), Ht3006.parsePage(text).shots)
    }

    @Test
    fun chronoFpsPageIsConverted() {
        assertEquals(304.8, Ht3006.parsePage("Velocity fps 1: 1000").shots[0], 1e-9)
        assertEquals(3.2, Ht3006.parsePage("Weight 3.20 g Velocity m/s 1: 280.5 2: 281.0").weightGrams!!, 1e-9)
    }

    @Test
    fun autoThresholdSitsAboveNoise() {
        var seed = 3
        val env = FloatArray(10_000) {
            seed = seed * 1103515245 + 12345
            0.01f + ((seed ushr 16) and 0x7fff) / 32768f * 0.002f
        }
        env[5000] = 1f
        val thr = autoThreshold(env)
        val noise = median(env)
        assertTrue(thr >= 4 * noise)
        assertTrue(thr < 1.0)
    }

    @Test
    fun autoKFindsSyntheticShotAndGong() {
        val rate = 48_000
        val n = rate
        var seed = 11
        val x = FloatArray(n) {
            seed = seed * 1103515245 + 12345
            (((seed ushr 16) and 0x7fff) / 32768f - 0.5f) * 0.002f
        }
        fun burst(at: Int, amp: Float) {
            for (i in 0 until 2000) x[at + i] += amp * kotlin.math.sin(i * 0.9f) * kotlin.math.exp(-i / 400f)
        }
        burst((0.1 * rate).toInt(), 0.8f)
        burst((0.55 * rate).toInt(), 0.1f)
        val p = processRecording(x, rate, null, 100.0, 800.0, 340.0)
        assertTrue(p.muzzleFound && p.gongFound)
        assertEquals(0.1, p.t1, 0.002)
        assertEquals(0.55, p.t2, 0.002)
    }

    @Test
    fun sessionIsSplitPerShot() {
        val rate = 1000
        val samples = FloatArray(10 * rate)
        val shots = listOf(LiveShot(1.0, 1.42), LiveShot(4.0, null), LiveShot(4.5, 4.9))
        val seg = splitSession(samples, rate, shots, 0.42)
        assertEquals(3, seg.size)
        assertEquals(0.85, seg[0].first, 1e-9)
        assertEquals((1.82 - 0.85) * rate, seg[0].second.size.toDouble(), 2.0)
        // second shot is cut before the third one starts
        assertTrue(abs(seg[1].first + seg[1].second.size.toDouble() / rate - 4.45) < 0.01)
    }

    @Test
    fun slugMath() {
        // 150 gr (9.72 g) .308: SD ≈ 0.226 lb/in², 800 m/s ≈ 3110 J
        assertEquals(0.226, sectionalDensity(9.7198, 7.82), 0.002)
        assertEquals(3110.0, energyJoules(9.7198, 800.0), 5.0)
        // 3.2 g slug at 280 m/s ≈ 125 J
        assertEquals(125.4, energyJoules(3.2, 280.0), 0.1)
    }

    @Test
    fun realHt3006PageIsParsed() {
        val page = Ht3006.parsePage(
            "HT-X3006 X3006 shot chronograph velocity 1.4 m/s firerate 0.0 r/min range 0.5 m weight 3.25 g " +
                "caliber 7.62 mm energy 0.0 J velocity 01: 1.3 02: 1.3 03: 1.2 04: 1.4 05: 1.5 average velocity 1.4 max:___1.5",
        )
        assertEquals(listOf(1.3, 1.3, 1.2, 1.4, 1.5), page.shots)
        assertEquals(3.25, page.weightGrams!!, 1e-9)
    }

    @Test
    fun onlyShotsAfterConnectAreNew() {
        // list grows at the end
        assertEquals(listOf(290.0), Ht3006.newShots(listOf(280.0, 281.0), listOf(280.0, 281.0, 290.0)))
        // device keeps only the last 3 and scrolls
        assertEquals(listOf(295.0), Ht3006.newShots(listOf(280.0, 281.0, 290.0), listOf(281.0, 290.0, 295.0)))
        // same speed twice in a row
        assertEquals(listOf(280.0), Ht3006.newShots(listOf(280.0), listOf(280.0, 280.0)))
        // device list was cleared
        assertEquals(listOf(300.0), Ht3006.newShots(listOf(280.0, 281.0), listOf(300.0)))
    }
}
