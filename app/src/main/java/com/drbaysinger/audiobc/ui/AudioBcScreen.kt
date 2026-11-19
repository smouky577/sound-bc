package com.drbaysinger.audiobc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drbaysinger.audiobc.data.Source
import com.drbaysinger.audiobc.net.Ht3006

@Composable
fun AudioBcScreen(vm: AudioBcViewModel = viewModel()) {
    var about by remember { mutableStateOf(false) }
    // Messages pop up as a short toast so they never push the layout (no scroll).
    val toastContext = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(vm.message) {
        vm.message?.let {
            android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }
    if (about) AboutDialog { about = false }
    BackHandler(enabled = vm.screen != Screen.MAIN) {
        vm.screen = if (vm.screen == Screen.HISTORY && vm.shots.isNotEmpty()) Screen.RESULTS else Screen.MAIN
    }
    // Layout follows the mockup exactly: fixed text scale (the system font on Honor/Huawei is wider than the mockup font).
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, UI_FONT_SCALE)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .systemBarsPadding()
                .imePadding()
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
        ) {
            when (vm.screen) {
                Screen.MAIN -> MainScreen(vm) { about = true }
                Screen.RESULTS -> ResultsScreen(vm)
                Screen.HISTORY -> HistoryScreen(vm)
            }
        }
    }
}

/** Existing top bar: title, language flag, Dr. Baysinger. */
@Composable
fun TopBar(vm: AudioBcViewModel, onAbout: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("SoundBC", Accent, 24.sp, FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LanguageFlag(bulgarian = vm.settings.bulgarian, onClick = vm::toggleLanguage)
            T(
                "by Dr. Baysinger",
                Muted,
                11.sp,
                modifier = Modifier
                    .clickable(onClick = onAbout)
                    .padding(start = 8.dp, top = 14.dp, bottom = 14.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.MainScreen(vm: AudioBcViewModel, onAbout: () -> Unit) {
    val text = vm.text()
    val s = vm.settings
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else vm.message = text.micDenied
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.useCurrentWeather(result.values.any { it })
    }
    val wifiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.toggleChrono()
    }
    val openAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.pickFiles(uris)
    }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    TopBar(vm, onAbout)
    Column(
        // No weight: the fields take what they need, the panel below gets the rest (never a scroll).
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // 1. Record | Pick files
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val live = vm.live
            if (vm.recording) {
                val secs = (live?.seconds ?: 0.0).toInt()
                Pill(Modifier.weight(1f), filled = true, color = Danger, height = 52.dp, enabled = !vm.busy, onClick = vm::stopAndAnalyze) {
                    androidx.compose.foundation.text.BasicText(
                        text.stop("%d:%02d".format(secs / 60, secs % 60), live?.shots ?: 0),
                        style = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp),
                    )
                }
            } else {
                Pill(Modifier.weight(1f), filled = true, height = 52.dp, enabled = !vm.busy, onClick = {
                    if (granted(Manifest.permission.RECORD_AUDIO)) vm.startRecording()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Dot(Color(0xFFD32F2F), 12.dp)
                    Gap(10.dp)
                    T(text.record, OnAccent, 16.sp, FontWeight.Bold)
                }
            }
            Pill(Modifier.weight(1f), filled = false, height = 52.dp, enabled = !vm.busy && !vm.recording, onClick = {
                openAudio.launch(arrayOf("audio/*", "audio/mp4", "audio/mpeg", "audio/aac", "audio/wav", "audio/x-wav", "audio/mp4a-latm"))
            }) {
                T(if (vm.fileCount > 0) "${text.pick} · ${vm.fileCount}" else text.pick, Text, 16.sp, FontWeight.SemiBold)
            }
        }

        // 2. V0 + switch | HT3006
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .card(shape = RoundedCornerShape(12.dp))
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // "V0 m/s" is only a hint inside the empty field; once a value is typed the unit follows the digits.
                        NumberInput(
                            s.v0, vm::setV0, Modifier.fillMaxWidth(), 20.sp, FontWeight.Bold,
                            placeholderContent = {
                                androidx.compose.material3.Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)) { append("V0 ") }
                                        append(text.unitMs)
                                    },
                                    color = Grey,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            },
                            suffix = { T(" ${text.unitMs}", Muted, 15.sp) },
                        )
                    }
                    Gap(8.dp)
                    Switch(
                        checked = s.sharedV0,
                        onCheckedChange = vm::setShared,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent,
                            checkedThumbColor = OnAccent,
                            uncheckedTrackColor = Line,
                            uncheckedThumbColor = Muted,
                            uncheckedBorderColor = Line,
                        ),
                    )
                }
                T(text.sharedV0, Text, 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                val cs = vm.chronoState
                Pill(Modifier.heightIn(min = 52.dp), filled = false, height = 52.dp, onClick = {
                    val needed = if (Build.VERSION.SDK_INT >= 33) {
                        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    if (needed.all(::granted)) vm.toggleChrono() else wifiPermission.launch(needed)
                }) {
                    if (cs == Ht3006.State.CONNECTING) {
                        CircularProgressIndicator(color = Blue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Glyph.WIFI, if (cs == Ht3006.State.CONNECTED) Blue else Muted, 18.dp)
                    }
                    Gap(10.dp)
                    T(if (cs == Ht3006.State.CONNECTED) "${text.ht} · ${vm.importedCount}" else text.ht, Text, 16.sp, FontWeight.Bold)
                }
                T(
                    when (cs) {
                        Ht3006.State.CONNECTING, Ht3006.State.FAILED -> text.importing
                        Ht3006.State.CONNECTED -> text.tapToDisconnect
                        Ht3006.State.DISCONNECTED -> text.tapToImport
                    },
                    if (cs == Ht3006.State.CONNECTED) Blue else Muted,
                    13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // 3. Range | Slug — same 52 dp height as the V0 / HT3006 row above
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Range: hint "Range m ◎" when empty, "150 m ◎" once filled (◎ = the amber gong mark).
            Row(
                Modifier.weight(1f).height(52.dp).card(shape = RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberInput(
                    s.distance, vm::setDistance, Modifier.fillMaxWidth(), 20.sp, FontWeight.Bold,
                    placeholderContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)) { append("${text.distanceHint} ") }
                                    append(text.unitM)
                                },
                                color = Grey,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Gap(8.dp)
                            Icon(Glyph.TARGET, Accent, 16.dp)
                        }
                    },
                    suffix = {
                        T(" ${text.unitM}", Muted, 15.sp)
                        Gap(8.dp)
                        Icon(Glyph.TARGET, Accent, 16.dp)
                    },
                )
            }
            Row(
                Modifier.weight(1f).height(52.dp).card(shape = RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberInput(
                    s.caliber, vm::setCaliber, Modifier, 18.sp, FontWeight.Bold,
                    placeholderContent = { Hint(text.caliberHint, text.unitMm) },
                    suffix = { T(" ${text.unitMm}", Muted, 14.sp) },
                )
                T(" · ", Muted, 16.sp, FontWeight.Bold)
                NumberInput(
                    s.weight, vm::setWeight, Modifier.weight(1f), 18.sp, FontWeight.Bold,
                    placeholderContent = { Hint(text.slugHint, text.unitGr) },
                    suffix = { T(" ${text.unitGr}", Muted, 14.sp) },
                )
            }
        }

        // 4. Conditions
        ConditionsCard(vm, text) {
            val perms = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            if (perms.any(::granted)) vm.useCurrentWeather(true) else locationPermission.launch(perms)
        }

        // 5. Waveform | Spectrogram
        Row(Modifier.padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TabCard(!s.spectrogram, Modifier.weight(1f), { vm.setSpectrogram(false) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyph.WAVE, if (!s.spectrogram) Accent else Text, 18.dp)
                    Gap(8.dp)
                    T(text.waveform, if (!s.spectrogram) Accent else Text, 16.sp, FontWeight.SemiBold)
                }
            }
            TabCard(s.spectrogram, Modifier.weight(1f), { vm.setSpectrogram(true) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyph.BARS, if (s.spectrogram) Accent else Text, 18.dp)
                    Gap(8.dp)
                    T(text.spectrogram, if (s.spectrogram) Accent else Text, 16.sp, FontWeight.SemiBold)
                }
            }
        }
    }

    // While the keyboard is open the fixed panel and Analyze step aside so the fields stay visible.
    if (WindowInsets.isImeVisible) return

    HorizontalDivider(color = Line)

    // 6. Fixed panel above Analyze
    PanelCard(vm, text, Modifier.weight(1f))

    // 7. Analyze
    Pill(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        filled = true,
        enabled = !vm.busy,
        height = 54.dp,
        onClick = vm::analyze,
    ) {
        if (vm.busy) {
            CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Gap(10.dp)
            T(vm.status ?: text.analyze, OnAccent, 15.sp, FontWeight.SemiBold)
        } else {
            T(text.analyze, OnAccent, 20.sp, FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionsCard(vm: AudioBcViewModel, text: L10n, onWeather: () -> Unit) {
    val s = vm.settings
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .card(shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(text.conditions, Accent, 17.sp, FontWeight.Bold)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                // Use current weather - compact pill between the title and Edit
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentTint)
                        .dashedBorder(Accent, 12.dp)
                        .clickable(enabled = !vm.weatherBusy, onClick = onWeather)
                        .height(40.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (vm.weatherBusy) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    } else {
                        Icon(Glyph.PIN, Accent, 15.dp)
                    }
                    Gap(6.dp)
                    T(text.useWeather, Accent, 14.sp, FontWeight.SemiBold)
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.conditionsOpen = !vm.conditionsOpen }
                    .heightIn(min = 44.dp)
                    .padding(start = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(if (vm.conditionsOpen) text.done else text.edit, Muted, 14.sp)
                Gap(4.dp)
                Icon(if (vm.conditionsOpen) Glyph.CHEVRON_UP else Glyph.CHEVRON_DOWN, Muted, 16.dp)
            }
        }
        // The card keeps its closed size: when Edit is open the fields cover the chips, legend and
        // sound/density line instead of growing the card (no scroll on the main screen).
        Box {
            Column(Modifier.alpha(if (vm.conditionsOpen) 0f else 1f)) {
                    FlowRow(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Chip("T", "${s.temperature} °C", dotFor(s.tSource))
                        Chip("P", "${s.pressure} hPa", dotFor(s.pSource))
                        Chip("RH", "${s.humidity} %", dotFor(s.rhSource))
                        Chip("Mic", "${s.microphone} m", Grey)
                        Chip("k", s.k.ifBlank { text.auto }, Grey)
                    }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Blue); Gap(5.dp); T(text.legendBaro, Muted, 13.sp); Gap(12.dp)
                    Dot(Accent); Gap(5.dp); T(text.legendMeteo, Muted, 13.sp); Gap(12.dp)
                    Dot(Grey); Gap(5.dp); T(text.legendManual, Muted, 13.sp)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val rho = vm.airDensity
                    val c = vm.soundSpeed
                    // c = speed of sound, rho = air density (both from T / P / RH), labelled so anyone can read them.
                    T(
                        "${text.soundLabel} ${c?.let { formatNumber(it, 1, false) } ?: "—"} ${text.unitMs} · " +
                            "${text.densityLabel} ${rho?.let { formatNumber(it, 3, false) } ?: "—"} kg/m³",
                        Muted,
                        14.sp,
                    )
                }
            }
            if (vm.conditionsOpen) {
                Column(
                    Modifier.matchParentSize().padding(top = 6.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledField("T (°C)", s.temperature, vm::setTemperature, Modifier.weight(1f).fillMaxHeight(), dot = dotFor(s.tSource), compact = true)
                        LabeledField("P (hPa)", s.pressure, vm::setPressure, Modifier.weight(1f).fillMaxHeight(), dot = dotFor(s.pSource), compact = true)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledField("RH (%)", s.humidity, vm::setHumidity, Modifier.weight(1f).fillMaxHeight(), dot = dotFor(s.rhSource), compact = true)
                        LabeledField("Mic (m)", s.microphone, vm::setMicrophone, Modifier.weight(1f).fillMaxHeight(), compact = true)
                        LabeledField("k", s.k, vm::setK, Modifier.weight(1f).fillMaxHeight(), placeholder = text.auto, compact = true)
                    }
                }
            }
        }
    }
}

private fun dotFor(source: Source) = when (source) {
    Source.BAROMETER -> Blue
    Source.METEO -> Accent
    Source.MANUAL -> Grey
}

@Composable
private fun Chip(label: String, value: String, dot: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .height(34.dp)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, Muted, 15.sp)
        Gap(5.dp)
        T(value, Text, 15.sp, FontWeight.Bold)
        Gap(7.dp)
        Dot(dot)
    }
}

@Composable
private fun PanelCard(vm: AudioBcViewModel, text: L10n, modifier: Modifier = Modifier) {
    val spec = vm.settings.spectrogram
    Column(
        modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .card()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val live = vm.live
        val shot = vm.panelShot
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                vm.recording -> {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Danger))
                    Gap(6.dp)
                    T(text.live, Muted, 13.sp, modifier = Modifier.weight(1f))
                }
                shot != null -> {
                    T("${shot.name} · ${if (spec) text.spectrogram.lowercase() else text.waveform.lowercase()}", Muted, 13.sp, modifier = Modifier.weight(1f))
                    T("${formatNumber(shot.viewStart, 1, false)} – ${formatNumber(shot.viewEnd, 1, false)} s", Muted, 13.sp)
                }
                else -> Spacer(Modifier.height(18.dp))
            }
        }
        val chartMod = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .weight(1f)
        when {
            vm.recording && live != null && spec && live.spectrogram != null -> LiveSpectrogram(live.spectrogram, chartMod)
            vm.recording && live != null -> LiveChart(live.tail, live.threshold, chartMod)
            shot != null && shot.envelope.isNotEmpty() -> {
                val sg = if (spec) vm.spectrogramFor(shot) else null
                ShotChart(shot, sg, spec, interactive = false, modifier = chartMod)
            }
            else -> Spacer(chartMod)
        }
    }
}

/** Fixed text scale, independent of the system font size setting, so the layout matches the mockup. */
private const val UI_FONT_SCALE = 0.9f

/** Grey field hint: a small word followed by the bold unit, e.g. "slug gr". */
@Composable
private fun Hint(word: String, unit: String) {
    androidx.compose.material3.Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)) { append("$word ") }
            append(unit)
        },
        color = Grey,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
