package com.drbaysinger.audiobc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drbaysinger.audiobc.ballistics.Atmosphere
import com.drbaysinger.audiobc.ballistics.bcHitWindowMs
import com.drbaysinger.audiobc.ballistics.energyJoules
import com.drbaysinger.audiobc.ballistics.formFactor
import com.drbaysinger.audiobc.ballistics.projectileCd
import com.drbaysinger.audiobc.ballistics.sectionalDensity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Header(title: String, onBack: () -> Unit, right: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Glyph.BACK, Text, 22.dp) }
        T(title, Text, 24.sp, FontWeight.Bold, Modifier.weight(1f).padding(start = 4.dp))
        right()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ResultsScreen(vm: AudioBcViewModel) {
    val text = vm.text()
    val g7 = vm.settings.g7
    val bg = text.bulgarian
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) vm.exportExcel(uri)
    }
    var editV0 by remember { mutableStateOf<Int?>(null) }

    Header(text.results, { vm.screen = Screen.MAIN }) {
        Pill(filled = false, height = 48.dp, onClick = vm::openHistory) {
            Icon(Glyph.HISTORY, Accent, 18.dp)
            Gap(8.dp)
            T(text.history, Text, 15.sp, FontWeight.SemiBold)
        }
    }

    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
        // 2. G1 | G7
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false, true).forEach { model ->
                val st = vm.stats(model)
                val sel = model == g7
                TabCard(sel, Modifier.weight(1f), { vm.setG7(model) }) {
                    // Compact: "G1  0,069" on one line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(if (model) "G7" else "G1", Muted, 16.sp, FontWeight.SemiBold)
                        Gap(10.dp)
                        T(st?.mean?.let { formatNumber(it, 3, bg) } ?: text.dash, if (sel) Accent else Text, 22.sp, FontWeight.Bold)
                    }
                }
            }
        }


        // 3. Selected shot panel
        val shot = vm.shots.getOrNull(vm.selected)
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .card(shape = RoundedCornerShape(18.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val view = if (vm.resultsSpectrogram) text.spectrogram.lowercase() else text.waveform.lowercase()
                T("${text.shot} ${shot?.number ?: "—"} · $view", Text, 16.sp, FontWeight.SemiBold)
                Box(Modifier.weight(1f))
                T(
                    when {
                        shot == null -> ""
                        !shot.muzzleFound -> text.tapShot
                        !shot.gongFound -> text.tapGong
                        else -> text.dragHint
                    },
                    if (shot != null && !shot.gongFound) Accent else Muted,
                    12.sp,
                )
            }
            val chartMod = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(150.dp)
            if (shot != null && shot.envelope.isNotEmpty()) {
                val sg = if (vm.resultsSpectrogram) vm.spectrogramFor(shot) else null
                Box(chartMod) {
                    ShotChart(
                        shot = shot,
                        spectrogram = sg,
                        showSpectrogram = vm.resultsSpectrogram,
                        interactive = true,
                        modifier = Modifier.fillMaxSize(),
                        onMove = { which, t -> vm.moveMarker(vm.selected, which, t) },
                    )
                    if (vm.resultsSpectrogram && sg == null) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp).align(Alignment.Center))
                    }
                }
            } else {
                Box(chartMod.card(), contentAlignment = Alignment.Center) {
                    T(shot?.decodeError ?: text.dash, Danger, 13.sp, maxLines = 3)
                }
            }
            val cond = vm.sessionCond?.let(vm::parseCond)
            val speed = shot?.v0 ?: cond?.sharedSpeed
            val window = remember(cond, speed, g7) {
                if (cond == null || speed == null) {
                    null
                } else {
                    val sound = Atmosphere.speedOfSound(cond.temperature)
                    val density = Atmosphere.density(cond.temperature, cond.pressure, cond.humidity)
                    val table = if (g7) vm.tables.second else vm.tables.first
                    bcHitWindowMs(cond.distance, speed, density, sound, cond.microphone, table)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val label = window?.let { w ->
                    val from = formatNumber(w.minMs, 0, bg)
                    val to = w.maxMs?.let { formatNumber(it, 0, bg) }
                    if (to == null) text.hitWindowOpen(from) else text.hitWindow(from, to)
                }
                val dtMs = shot?.deltaT?.times(1000.0)
                val inside = window != null && dtMs != null &&
                    dtMs >= window.minMs - 0.5 && (window.maxMs == null || dtMs <= window.maxMs + 0.5)
                T(
                    label.orEmpty(),
                    when {
                        label == null || dtMs == null -> Muted
                        inside -> Text
                        else -> Danger
                    },
                    14.sp,
                    FontWeight.SemiBold,
                    Modifier.weight(1f).padding(end = 8.dp),
                )
                Pill(filled = false, height = 44.dp, onClick = { vm.resultsSpectrogram = !vm.resultsSpectrogram }) {
                    T(if (vm.resultsSpectrogram) text.showWaveform else text.showSpectrogram, Muted, 14.sp)
                }
            }
        }

        // 4. Shot list — the J column only when V0 and the slug weight came from the chronograph
        val slugGrams = vm.sessionCond?.weight?.let(::num)?.takeIf { it > 0 }
        val showJ = vm.sessionCond?.chrono == true
        Row(Modifier.padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 6.dp)) {
            T(text.shotCol, Muted, 13.sp, modifier = Modifier.weight(W_NO))
            T(text.dt, Muted, 13.sp, modifier = Modifier.weight(W_DT))
            T("V0", Muted, 13.sp, modifier = Modifier.weight(W_V0))
            if (showJ) T("J", Muted, 13.sp, modifier = Modifier.weight(W_J))
            T(if (g7) "BC G7" else "BC G1", Muted, 13.sp, modifier = Modifier.weight(W_BC))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.shots, key = { i, s -> "${s.number}_$i" }) { i, s ->
                ShotRow(s, i == vm.selected, g7, if (showJ) slugGrams else null, showJ, text, onClick = { vm.select(i) }, onV0 = { editV0 = i })
            }
        }
    }

    HorizontalDivider(color = Line)
    Pill(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        filled = true,
        height = 60.dp,
        enabled = vm.shots.isNotEmpty(),
        onClick = { saveCsv.launch(vm.exportName()) },
    ) {
        Icon(Glyph.DOWNLOAD, OnAccent, 20.dp)
        Gap(10.dp)
        T(text.exportExcel, OnAccent, 20.sp, FontWeight.Bold)
    }

    editV0?.let { index ->
        val shot = vm.shots.getOrNull(index)
        var value by remember(index) { mutableStateOf(shot?.v0?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
        AlertDialog(
            onDismissRequest = { editV0 = null },
            containerColor = Card,
            title = { T("${text.editV0} ${shot?.number ?: ""}", Text, 18.sp, FontWeight.Bold) },
            text = { LabeledField("V0 (m/s)", value, { value = it }, Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { vm.setShotV0(index, value); editV0 = null }) { T(text.ok, Accent) }
            },
            dismissButton = { TextButton(onClick = { editV0 = null }) { T(text.cancel, Muted) } },
        )
    }
}

@Composable
private fun ShotRow(s: ShotUi, selected: Boolean, g7: Boolean, grams: Double?, showJ: Boolean, text: L10n, onClick: () -> Unit, onV0: () -> Unit) {
    val bg = text.bulgarian
    val invalid = !s.gongFound || !s.muzzleFound || s.decodeError != null
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clip(shape)
    val styled = when {
        selected -> base.background(AccentTint).border(1.dp, Accent, shape)
        invalid -> base.dashedBorder(Grey, 14.dp)
        else -> base.background(Card).border(1.dp, Line, shape)
    }
    Row(
        styled
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val main = if (invalid && !selected) Muted else Text
        T(s.number.toString(), main, 16.sp, modifier = Modifier.weight(W_NO))
        if (invalid) {
            T(text.dash, Muted, 16.sp, modifier = Modifier.weight(W_DT))
            T(text.dash, Muted, 16.sp, modifier = Modifier.weight(0.5f))
            // The hint spans into the V0 / J columns so it is not cut; it stays right-aligned like the mockup.
            Box(Modifier.weight(W_V0 + (if (showJ) W_J else 0f) + W_BC - 0.5f), contentAlignment = Alignment.CenterEnd) {
                T(if (s.decodeError != null) "error" else text.noGongFix, Muted, 13.sp, FontWeight.Bold)
            }
        } else {
            T(s.deltaT?.let { formatNumber(it, 3, bg) } ?: text.dash, main, 16.sp, modifier = Modifier.weight(W_DT))
            T(
                s.v0?.let { formatNumber(it, 1, bg) } ?: text.setV0,
                if (s.v0 == null) Accent else if (s.v0Override != null) Blue else main,
                16.sp,
                modifier = Modifier
                    .weight(W_V0)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onV0)
                    .padding(vertical = 14.dp),
            )
            // Muzzle energy E = ½·m·v² from the chronograph speed and slug weight (grams)
            if (showJ) {
                val joules = if (grams != null) s.v0?.let { energyJoules(grams, it) } else null
                T(joules?.let { formatNumber(it, 0, bg) } ?: text.dash, main, 16.sp, modifier = Modifier.weight(W_J))
            }
            val bc = s.bc(g7)
            T(
                bc?.let { formatNumber(it, 3, bg) } ?: if (s.math?.let { (if (g7) it.g7 else it.g1)?.outOfRange } == true) text.outOfRange else text.dash,
                main,
                if (bc == null) 12.sp else 16.sp,
                FontWeight.Bold,
                Modifier.weight(W_BC),
                maxLines = 2,
            )
        }
    }
}

@Composable
fun ColumnScope.HistoryScreen(vm: AudioBcViewModel) {
    val text = vm.text()
    val bg = text.bulgarian
    var confirm by remember { mutableStateOf<Long?>(null) }
    val format = remember { SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.US) }
    Header(text.history, { vm.screen = if (vm.shots.isNotEmpty()) Screen.RESULTS else Screen.MAIN })
    if (vm.busy) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Gap(10.dp)
            T(text.loading, Muted)
        }
    }
    if (vm.historyItems.isEmpty()) {
        T(text.historyEmpty, Muted, 15.sp, modifier = Modifier.padding(16.dp))
    }
    LazyColumn(
        Modifier
            .weight(1f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(vm.historyItems, key = { it.id }) { item ->
            val current = item.id == vm.sessionId
            Row(
                Modifier
                    .fillMaxWidth()
                    .card(selected = current)
                    .clickable(enabled = !vm.busy) { vm.openSession(item.id) }
                    .heightIn(min = 64.dp)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    T(format.format(Date(item.id)), Text, 16.sp, FontWeight.SemiBold)
                    T(text.sessionLine(item.shots, item.valid, item.distance), Muted, 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    T("G1 ${item.g1?.let { formatNumber(it, 3, bg) } ?: text.dash}", Accent, 14.sp, FontWeight.Bold)
                    T("G7 ${item.g7?.let { formatNumber(it, 3, bg) } ?: text.dash}", Text, 14.sp)
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { confirm = item.id },
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyph.TRASH, Muted, 18.dp) }
            }
        }
    }
    confirm?.let { id ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Card,
            title = { T(text.deleteConfirm, Text, 17.sp, FontWeight.Bold, maxLines = 2) },
            text = { T(format.format(Date(id)), Muted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSession(id); confirm = null }) { T(text.delete, Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { T(text.cancel, Muted) } },
        )
    }
}

// Shot list column weights: No · Δt · V0 · J · BC
private const val W_NO = 0.55f
private const val W_DT = 1f
private const val W_V0 = 1f
private const val W_J = 0.75f
private const val W_BC = 1.1f

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
