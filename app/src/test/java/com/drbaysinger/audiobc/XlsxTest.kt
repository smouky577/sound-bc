package com.drbaysinger.audiobc

import com.drbaysinger.audiobc.export.Sheet
import com.drbaysinger.audiobc.export.writeXlsx
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class XlsxTest {
    @Test
    fun writesValidWorkbook() {
        val sheet = Sheet(
            listOf(listOf("Изстрел", "V0 (m/s)", "BC G1"), listOf(1.0, 812.5, 0.2341), emptyList(), listOf("a & <b>", null, 3.0)),
            setOf(0),
        )
        val out = ByteArrayOutputStream()
        writeXlsx(sheet, out)
        val bytes = out.toByteArray()
        File("build/sample.xlsx").writeBytes(bytes)
        val names = ArrayList<String>()
        ZipInputStream(bytes.inputStream()).use { z -> while (true) { names += (z.nextEntry ?: break).name } }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml")))
    }
}
