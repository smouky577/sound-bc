package com.drbaysinger.audiobc.ballistics

/**
 * Mach → Cd table.
 *
 * G1 and G7 rows are the standard drag functions shipped with py_ballisticcalc
 * (TableG1 / TableG7):
 * https://github.com/o-murphy/py-ballisticcalc/blob/master/py_ballisticcalc/drag_tables.py
 * They match the McCoy G1/G7 functions published by JBM Ballistics:
 * https://jbmballistics.com/downloads/mcg1.txt
 * https://jbmballistics.com/downloads/mcg7.txt
 *
 * Cd is read from assets/g1_cd.csv and assets/g7_cd.csv and interpolated linearly in Mach.
 */
class DragTable(
    private val mach: DoubleArray,
    private val cd: DoubleArray,
) {
    val size: Int get() = mach.size

    fun cd(machNumber: Double): Double {
        if (machNumber <= mach[0]) return cd[0]
        val last = mach.lastIndex
        if (machNumber >= mach[last]) return cd[last]
        var lo = 0
        var hi = last
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (mach[mid] <= machNumber) lo = mid else hi = mid
        }
        val span = mach[hi] - mach[lo]
        val fraction = if (span == 0.0) 0.0 else (machNumber - mach[lo]) / span
        return cd[lo] + fraction * (cd[hi] - cd[lo])
    }

    companion object {
        fun parse(text: String): DragTable {
            val mach = ArrayList<Double>()
            val cd = ArrayList<Double>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(',', ' ', '\t').filter { it.isNotEmpty() }
                if (parts.size < 2) continue
                if (parts[0].equals("mach", ignoreCase = true)) continue
                mach += parts[0].toDouble()
                cd += parts[1].toDouble()
            }
            require(mach.size >= 2) { "Drag table is empty" }
            for (i in 1 until mach.size) {
                require(mach[i] > mach[i - 1]) { "Mach values must increase" }
            }
            return DragTable(mach.toDoubleArray(), cd.toDoubleArray())
        }
    }
}
