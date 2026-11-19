package com.drbaysinger.audiobc.export

import com.drbaysinger.audiobc.ballistics.Atmosphere
import com.drbaysinger.audiobc.ballistics.DragTable
import com.drbaysinger.audiobc.ballistics.Stats
import com.drbaysinger.audiobc.ballistics.energyJoules
import com.drbaysinger.audiobc.ballistics.formFactor
import com.drbaysinger.audiobc.ballistics.projectileCd
import com.drbaysinger.audiobc.ballistics.sectionalDensity
import com.drbaysinger.audiobc.data.SessionConditions
import com.drbaysinger.audiobc.ui.L10n
import com.drbaysinger.audiobc.ui.ShotUi
import com.drbaysinger.audiobc.ui.num
import java.io.OutputStream
import kotlin.math.pow
import kotlin.math.roundToLong

/** One spreadsheet: rows of cells (Double = number, String = text, null = empty) and the bold header rows. */
class Sheet(val rows: List<List<Any?>>, val bold: Set<Int>)

/**
 * All shots (Δt, V0, BC G1, BC G7, time of flight, gong speed, energy when from the chronograph),
 * then the mean / SD, the conditions and the slug data.
 */
fun buildSheet(
    shots: List<ShotUi>,
    cond: SessionConditions,
    g1: Stats?,
    g7: Stats?,
    tables: Pair<DragTable, DragTable>,
    text: L10n,
): Sheet {
    val rows = ArrayList<List<Any?>>()
    val bold = HashSet<Int>()
    fun header(vararg v: Any?) { bold += rows.size; rows += v.toList() }
    fun row(vararg v: Any?) { rows += v.toList() }
    fun r(value: Double?, digits: Int): Double? {
        if (value == null || !value.isFinite()) return null
        val f = 10.0.pow(digits)
        return (value * f).roundToLong() / f
    }

    val weight = num(cond.weight)?.takeIf { it > 0 }
    val caliber = num(cond.caliber)?.takeIf { it > 0 }
    val energy = cond.chrono && weight != null

    val head = mutableListOf<Any?>(
        text.shot, text.file, "Δt (s)", "t shot (s)", "t gong (s)", "TOF (s)", "V0 (m/s)",
        "BC G1", "BC G7", "V gong G1 (m/s)", "V gong G7 (m/s)",
    )
    if (energy) head.addAll(listOf("E0 (J)", "E gong G1 (J)"))
    head.add(text.status)
    header(*head.toTypedArray())
    shots.forEach { s ->
        val state = when {
            s.decodeError != null -> s.decodeError
            !s.gongFound -> text.noGong
            s.v0 == null -> text.needV0
            else -> ""
        }
        val cells = mutableListOf<Any?>(
            s.number.toDouble(), s.name, r(s.deltaT, 6),
            r(s.t1, 6), r(if (s.gongFound) s.t2 else null, 6), r(s.math?.timeOfFlight, 6), r(s.v0, 2),
            r(s.math?.g1?.bc, 4), r(s.math?.g7?.bc, 4), r(s.math?.g1?.impactSpeed, 1), r(s.math?.g7?.impactSpeed, 1),
        )
        if (energy) {
            cells += r(s.v0?.let { energyJoules(weight!!, it) }, 1)
            cells += r(s.math?.g1?.impactSpeed?.let { energyJoules(weight!!, it) }, 1)
        }
        cells += state
        row(*cells.toTypedArray())
    }
    row()
    header(text.summary, text.mean, text.sdShort, "n")
    row("BC G1", r(g1?.mean, 4), r(g1?.sampleSd, 4), (g1?.count ?: 0).toDouble())
    row("BC G7", r(g7?.mean, 4), r(g7?.sampleSd, 4), (g7?.count ?: 0).toDouble())
    row()
    header(text.conditions)
    val t = num(cond.temperature)
    val p = num(cond.pressure)
    val rh = num(cond.humidity)
    row("D (m)", r(num(cond.distance), 2))
    row("T (°C)", r(t, 1))
    row("P (hPa)", r(p, 1))
    row("RH (%)", r(rh, 0))
    row("Mic (m)", r(num(cond.microphone), 2))
    row("k", if (cond.k.isBlank()) "auto" else (num(cond.k) ?: cond.k))
    if (t != null && p != null && rh != null) {
        row("ρ (kg/m³)", r(Atmosphere.density(t, p, rh), 4))
        row("c (m/s)", r(Atmosphere.speedOfSound(t), 1))
    }
    if (weight != null || caliber != null) {
        row()
        header(text.slugTitle)
        row("caliber (mm)", r(caliber, 2))
        row("weight (g)", r(weight, 2))
        if (weight != null && caliber != null) {
            val sd = sectionalDensity(weight, caliber)
            row("SD (lb/in²)", r(sd, 4))
            val v0 = shots.filter { it.valid(false) }.mapNotNull { it.v0 }.average().takeIf { it.isFinite() }
            val c = t?.let { Atmosphere.speedOfSound(it) }
            listOf(Triple("G1", g1, tables.first), Triple("G7", g7, tables.second)).forEach { (name, st, table) ->
                val bc = st?.mean ?: return@forEach
                val i = formFactor(sd, bc)
                row("i $name", r(i, 3))
                if (v0 != null && c != null) row("Cd $name muzzle", r(projectileCd(i, table, v0 / c), 4))
            }
        }
    }
    return Sheet(rows, bold)
}

/** Writes [sheet] as a minimal Excel workbook (.xlsx, Office Open XML) with real number cells. */
fun writeXlsx(sheet: Sheet, out: OutputStream, sheetName: String = "SoundBC") {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return sb.toString()
    }
    val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    val pkg = "http://schemas.openxmlformats.org/package/2006/relationships"
    val maxCols = sheet.rows.maxOfOrNull { it.size } ?: 1

    val data = StringBuilder()
    data.append(xml).append("<worksheet xmlns=\"$main\">")
    data.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
    data.append("<cols><col min=\"1\" max=\"1\" width=\"16\" customWidth=\"1\"/>")
    data.append("<col min=\"2\" max=\"$maxCols\" width=\"14\" customWidth=\"1\"/></cols><sheetData>")
    sheet.rows.forEachIndexed { ri, cells ->
        val rn = ri + 1
        data.append("<row r=\"$rn\">")
        val style = if (ri in sheet.bold) " s=\"1\"" else ""
        cells.forEachIndexed { ci, v ->
            val ref = "${col(ci)}$rn"
            when (v) {
                null -> {}
                is Number -> data.append("<c r=\"$ref\"$style><v>${v.toDouble()}</v></c>")
                else -> data.append("<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>")
            }
        }
        data.append("</row>")
    }
    data.append("</sheetData></worksheet>")

    val files = linkedMapOf(
        "[Content_Types].xml" to xml +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>",
        "_rels/.rels" to xml +
            "<Relationships xmlns=\"$pkg\"><Relationship Id=\"rId1\" Type=\"$rel/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
        "xl/workbook.xml" to xml +
            "<workbook xmlns=\"$main\" xmlns:r=\"$rel\"><sheets><sheet name=\"${esc(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
        "xl/_rels/workbook.xml.rels" to xml +
            "<Relationships xmlns=\"$pkg\">" +
            "<Relationship Id=\"rId1\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"$rel/styles\" Target=\"styles.xml\"/></Relationships>",
        "xl/styles.xml" to xml +
            "<styleSheet xmlns=\"$main\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>",
        "xl/worksheets/sheet1.xml" to data.toString(),
    )
    java.util.zip.ZipOutputStream(out).use { zip ->
        files.forEach { (name, body) ->
            zip.putNextEntry(java.util.zip.ZipEntry(name))
            zip.write(body.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
