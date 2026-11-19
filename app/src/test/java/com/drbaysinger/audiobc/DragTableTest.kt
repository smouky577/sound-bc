package com.drbaysinger.audiobc

import com.drbaysinger.audiobc.ballistics.DragTable
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Reference Cd values copied from py_ballisticcalc TableG1 / TableG7
 * (https://github.com/o-murphy/py-ballisticcalc/blob/master/py_ballisticcalc/drag_tables.py)
 * which reproduce the McCoy drag functions published by JBM Ballistics
 * (https://jbmballistics.com/downloads/mcg1.txt and mcg7.txt).
 */
class DragTableTest {
    @Test
    fun cdMatchesPublishedReference() {
        val g1 = DragTable.parse(asset("g1_cd.csv"))
        val g7 = DragTable.parse(asset("g7_cd.csv"))
        assertEquals(79, g1.size)
        assertEquals(84, g7.size)

        assertCd(g1, 0.5, 0.2032)
        assertCd(g1, 0.9, 0.3415)
        assertCd(g1, 1.0, 0.4805)
        assertCd(g1, 1.5, 0.6573)
        assertCd(g1, 2.0, 0.5934)

        assertCd(g7, 0.5, 0.1194)
        assertCd(g7, 0.9, 0.1464)
        assertCd(g7, 1.0, 0.3803)
        assertCd(g7, 1.5, 0.3440)
        assertCd(g7, 2.0, 0.2980)

        // Halfway between the published knots Mach 0.60 (0.2034) and 0.70 (0.2165).
        assertEquals(0.20995, g1.cd(0.65), 1e-12)
        assertEquals(0.2629, g1.cd(-1.0), 1e-12)
        assertEquals(0.4988, g1.cd(6.0), 1e-12)
        assertEquals(0.1618, g7.cd(9.0), 1e-12)
    }

    private fun assertCd(table: DragTable, mach: Double, expected: Double) {
        assertEquals(expected, table.cd(mach), 1e-12)
    }

    private fun asset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing $name from ${File(".").absolutePath}")
        return file.readText()
    }
}
