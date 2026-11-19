package com.drbaysinger.audiobc.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drbaysinger.audiobc.audio.LiveState
import com.drbaysinger.audiobc.audio.Recorder
import com.drbaysinger.audiobc.audio.Spectrogram
import com.drbaysinger.audiobc.audio.computeSpectrogram
import com.drbaysinger.audiobc.audio.decodeToMonoFloat
import com.drbaysinger.audiobc.audio.processRecording
import com.drbaysinger.audiobc.audio.splitSession
import com.drbaysinger.audiobc.ballistics.Atmosphere
import com.drbaysinger.audiobc.ballistics.DragTable
import com.drbaysinger.audiobc.ballistics.ShotMath
import com.drbaysinger.audiobc.ballistics.Stats
import com.drbaysinger.audiobc.ballistics.computeShot
import com.drbaysinger.audiobc.ballistics.statsOf
import com.drbaysinger.audiobc.data.Prefs
import com.drbaysinger.audiobc.data.SavedSession
import com.drbaysinger.audiobc.data.SavedShot
import com.drbaysinger.audiobc.data.SessionConditions
import com.drbaysinger.audiobc.data.SessionStore
import com.drbaysinger.audiobc.data.SessionSummary
import com.drbaysinger.audiobc.data.Settings
import com.drbaysinger.audiobc.data.Source
import com.drbaysinger.audiobc.export.buildSheet
import com.drbaysinger.audiobc.export.writeXlsx
import com.drbaysinger.audiobc.net.Ht3006
import com.drbaysinger.audiobc.net.WeatherSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { MAIN, RESULTS, HISTORY }

/** One analyzed shot: its audio, markers and result. */
data class ShotUi(
    val number: Int,
    val name: String,
    val audio: String,
    val sampleRate: Int,
    val samples: FloatArray,
    val envelope: FloatArray,
    val threshold: Double,
    val t1: Double,
    val t2: Double,
    val muzzleFound: Boolean,
    val gongFound: Boolean,
    val viewStart: Double,
    val viewEnd: Double,
    val v0Override: Double?,
    val v0: Double?,
    val math: ShotMath?,
    val decodeError: String?,
) {
    val duration: Double get() = if (sampleRate > 0) envelope.size.toDouble() / sampleRate else 0.0
    val deltaT: Double? get() = if (muzzleFound && gongFound && t2 > t1) t2 - t1 else null

    /** Counts toward mean / SD only with a gong hit and a solved BC. */
    fun valid(g7: Boolean): Boolean = gongFound && muzzleFound && bc(g7) != null
    fun bc(g7: Boolean): Double? = (if (g7) math?.g7 else math?.g1)?.bc
    fun impact(g7: Boolean): Double? = (if (g7) math?.g7 else math?.g1)?.impactSpeed
}

private data class PickedFile(val uri: Uri, val name: String)

class AudioBcViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val store = SessionStore(app)
    private val recorder = Recorder(app)
    private val weatherSource = WeatherSource(app)
    private val chrono = Ht3006(app)

    val tables by lazy {
        val assets = getApplication<Application>().assets
        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        DragTable.parse(read("g1_cd.csv")) to DragTable.parse(read("g7_cd.csv"))
    }

    // ---- persisted settings ----
    var settings by mutableStateOf(prefs.loadBlocking())
        private set

    // ---- screen / session state ----
    var screen by mutableStateOf(Screen.MAIN)
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var recording by mutableStateOf(false)
        private set
    var live by mutableStateOf<LiveState?>(null)
        private set
    /** Live link to the HT3006. */
    var chronoState by mutableStateOf(Ht3006.State.DISCONNECTED)
        private set
    var importedCount by mutableStateOf(0)
        private set
    var weatherBusy by mutableStateOf(false)
        private set
    var conditionsOpen by mutableStateOf(false)
    var historyItems by mutableStateOf<List<SessionSummary>>(emptyList())
        private set

    val shots = mutableStateListOf<ShotUi>()
    var selected by mutableStateOf(0)
    var sessionId by mutableStateOf<Long?>(null)
        private set
    var sessionCond by mutableStateOf<SessionConditions?>(null)
        private set
    var resultsSpectrogram by mutableStateOf(false)

    /** Shown in the fixed panel on Main when nothing has been analyzed yet (last picked file). */
    var preview by mutableStateOf<ShotUi?>(null)
        private set
    private val files = mutableStateListOf<PickedFile>()
    val fileCount: Int get() = files.size

    val spectrograms = mutableStateMapOf<String, Spectrogram>()
    private val specJobs = HashSet<String>()

    private var mathJob: Job? = null
    private var saveJob: Job? = null
    private var settingsJob: Job? = null

    init {
        importedCount = imported().size
    }

    fun text() = L10n(settings.bulgarian)

    // =============================== settings ===============================

    private fun update(change: (Settings) -> Settings) {
        settings = change(settings)
        message = null
        val snapshot = settings
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            delay(150)
            prefs.save(snapshot)
        }
    }

    fun toggleLanguage() = update { it.copy(bulgarian = !it.bulgarian) }
    fun setDistance(v: String) = update { it.copy(distance = v) }
    /** Typing V0 by hand means the chronograph is not used: its imported shots are dropped. */
    fun setV0(v: String) {
        importedCount = 0
        update { it.copy(v0 = v, imported = "") }
    }
    fun setShared(v: Boolean) = update { it.copy(sharedV0 = v) }
    fun setCaliber(v: String) = update { it.copy(caliber = v) }
    fun setWeight(v: String) = update { it.copy(weight = v) }
    fun setTemperature(v: String) = update { it.copy(temperature = v, tSource = Source.MANUAL) }
    fun setPressure(v: String) = update { it.copy(pressure = v, pSource = Source.MANUAL) }
    fun setHumidity(v: String) = update { it.copy(humidity = v, rhSource = Source.MANUAL) }
    fun setMicrophone(v: String) = update { it.copy(microphone = v) }
    fun setK(v: String) = update { it.copy(k = v) }
    fun setSpectrogram(v: Boolean) {
        update { it.copy(spectrogram = v) }
        resultsSpectrogram = v
        recorder.liveSpectrogram = v
    }
    fun setG7(v: Boolean) = update { it.copy(g7 = v) }

    private fun imported(): List<Double> =
        settings.imported.split(';').mapNotNull { it.toDoubleOrNull() }

    val airDensity: Double?
        get() {
            val t = num(settings.temperature) ?: return null
            val p = num(settings.pressure) ?: return null
            val rh = num(settings.humidity) ?: return null
            if (t <= -273.0 || p <= 0) return null
            return Atmosphere.density(t, p, rh)
        }
    val soundSpeed: Double?
        get() = num(settings.temperature)?.takeIf { it > -273.0 }?.let(Atmosphere::speedOfSound)

    // =============================== recording ===============================

    /** Call after RECORD_AUDIO is granted. Starts a new session. */
    fun startRecording() {
        if (busy || recording) return
        newSession()
        // A new series: only chronograph shots fired from now on belong to it.
        if (chrono.isActive) {
            chrono.clearShots()
            importedCount = 0
            update { it.copy(imported = "") }
        }
        recorder.gongWindow = gongWindow(currentCondFrom(settings))
        recorder.liveSpectrogram = settings.spectrogram
        val ok = recorder.start(viewModelScope) { live = it }
        if (!ok) {
            message = text().micFailed
            return
        }
        recording = true
    }

    fun stopAndAnalyze() = analyze()

    private fun newSession() {
        files.clear()
        shots.clear()
        spectrograms.clear()
        specJobs.clear()
        preview = null
        sessionId = null
        sessionCond = null
        selected = 0
        message = null
    }

    // =============================== files ===============================

    fun pickFiles(uris: List<Uri>) {
        if (uris.isEmpty() || busy || recording) return
        newSession()
        val resolver = getApplication<Application>().contentResolver
        uris.forEach { files += PickedFile(it, displayName(resolver, it)) }
        val last = files.last()
        val cond = currentCondFrom(settings)
        viewModelScope.launch {
            preview = try {
                val pcm = withContext(Dispatchers.Default) { decodeToMonoFloat(getApplication(), last.uri) }
                withContext(Dispatchers.Default) {
                    detect(files.size, last.name, "", pcm.samples, pcm.sampleRate, cond, null)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // =============================== HT3006 ===============================

    /** Connects to the chronograph (or disconnects when already linked). The link stays up until tapped again. */
    fun toggleChrono() {
        if (chrono.isActive) {
            chrono.disconnect()
            return
        }
        message = null
        importedCount = 0
        update { it.copy(imported = "") }
        chrono.connect(viewModelScope) { live ->
            viewModelScope.launch(Dispatchers.Main) { onChronoLive(live) }
        }
    }

    private fun onChronoLive(live: Ht3006.Live) {
        chronoState = live.state
        if (live.shots.size == importedCount && live.shots.isNotEmpty() == settings.imported.isNotBlank()) {
            live.weightGrams?.let { w -> if (num(settings.weight) != w) update { it.copy(weight = fmt(w, 2)) } }
            return
        }
        importedCount = live.shots.size
        if (live.shots.isEmpty()) {
            update { it.copy(imported = "") }
            return
        }
        // Each shot keeps its own chronograph speed (switch off); the field shows the average.
        update {
            it.copy(
                v0 = fmt(live.shots.average(), 1),
                sharedV0 = false,
                imported = live.shots.joinToString(";") { v -> String.format(Locale.US, "%.2f", v) },
                weight = live.weightGrams?.let { w -> fmt(w, 2) } ?: it.weight,
            )
        }
        if (shots.isNotEmpty() && !recording) recomputeAll()
    }

    // =============================== weather ===============================

    /** Call after the location permission request (granted or not). */
    fun useCurrentWeather(locationAllowed: Boolean) {
        if (weatherBusy) return
        weatherBusy = true
        message = null
        viewModelScope.launch {
            try {
                val baro = weatherSource.barometer()
                val loc = if (locationAllowed) weatherSource.location() else null
                val wx = loc?.let { weatherSource.openMeteo(it.latitude, it.longitude) }
                val place = loc?.let { weatherSource.placeName(it) }
                if (baro == null && wx == null) {
                    message = text().weatherFailed
                    return@launch
                }
                val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                update { s ->
                    var n = s
                    if (baro != null) {
                        n = n.copy(pressure = fmt(baro, 1), pSource = Source.BAROMETER)
                    } else if (wx?.surfacePressure != null) {
                        n = n.copy(pressure = fmt(wx.surfacePressure, 1), pSource = Source.METEO)
                    }
                    wx?.temperature?.let { n = n.copy(temperature = fmt(it, 1), tSource = Source.METEO) }
                    wx?.humidity?.let { n = n.copy(humidity = fmt(it, 0), rhSource = Source.METEO) }
                    n.copy(weatherPlace = place ?: if (loc == null) text().noLocation else "", weatherTime = time)
                }
            } finally {
                weatherBusy = false
            }
        }
    }

    // =============================== analysis ===============================

    fun analyze() {
        if (busy) return
        val strings = text()
        val cond = currentCondFrom(settings)
        if (parseCond(cond) == null) {
            message = strings.checkValues
            return
        }
        message = null
        viewModelScope.launch {
            busy = true
            try {
                val built = when {
                    recording -> analyzeRecording(cond)
                    files.isNotEmpty() && shots.isEmpty() -> analyzeFiles(cond)
                    shots.isNotEmpty() -> {
                        // Re-run the math on the current session with the current conditions.
                        val reused = shots.toList()
                        sessionCond = cond
                        withContext(Dispatchers.Default) { reused.map { recompute(it, cond) } }
                    }
                    else -> {
                        message = strings.needInput
                        return@launch
                    }
                }
                if (built.isEmpty()) {
                    message = strings.noShotsFound
                    return@launch
                }
                shots.clear()
                shots.addAll(built)
                sessionCond = cond
                if (sessionId == null) sessionId = System.currentTimeMillis()
                selected = built.indexOfFirst { it.decodeError == null }.coerceAtLeast(0)
                resultsSpectrogram = settings.spectrogram
                if (built.any { it.decodeError == null && it.v0 == null }) message = strings.needV0
                saveSession(writeAudio = true)
                screen = Screen.RESULTS
            } finally {
                busy = false
                status = null
            }
        }
    }

    private suspend fun analyzeRecording(cond: SessionConditions): List<ShotUi> {
        status = text().stopping
        val (samples, liveShots) = recorder.stop()
        recording = false
        live = null
        val rate = recorder.sampleRate
        val c = parseCond(cond)!!
        val expected = c.distance / Atmosphere.speedOfSound(c.temperature) + c.distance / (c.sharedSpeed ?: 800.0)
        val segments = withContext(Dispatchers.Default) { splitSession(samples, rate, liveShots, expected) }
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        return segments.mapIndexed { i, (offset, seg) ->
            status = text().shotOf(i + 1, segments.size)
            withContext(Dispatchers.Default) {
                detect(i + 1, "$stamp +${fmt(offset, 1)} s", "shot_${i + 1}.pcm", seg, rate, cond, null)
            }
        }
    }

    private suspend fun analyzeFiles(cond: SessionConditions): List<ShotUi> {
        val app = getApplication<Application>()
        return files.mapIndexed { i, file ->
            status = text().fileOf(i + 1, files.size, file.name)
            try {
                val pcm = withContext(Dispatchers.Default) { decodeToMonoFloat(app, file.uri) }
                withContext(Dispatchers.Default) {
                    detect(i + 1, file.name, "shot_${i + 1}.pcm", pcm.samples, pcm.sampleRate, cond, null)
                }
            } catch (e: Exception) {
                errorShot(i + 1, file.name, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun errorShot(number: Int, name: String, error: String) = ShotUi(
        number, name, "", 0, FloatArray(0), FloatArray(0), 0.0, 0.0, 0.0,
        false, false, 0.0, 0.0, null, null, null, error,
    )

    /** Detection + math for one shot's audio. */
    private fun detect(
        number: Int,
        name: String,
        audio: String,
        samples: FloatArray,
        rate: Int,
        cond: SessionConditions,
        override: Double?,
    ): ShotUi {
        val c = parseCond(cond)
        val v0 = override ?: speedFor(number, cond)
        val p = processRecording(
            samples = samples,
            sampleRate = rate,
            k = c?.k,
            distanceM = c?.distance ?: 100.0,
            muzzleVelocity = v0 ?: 800.0,
            soundSpeed = Atmosphere.speedOfSound(c?.temperature ?: 15.0),
        )
        val duration = p.envelope.size.toDouble() / rate
        val (vs, ve) = viewWindow(p.t1, p.t2, p.muzzleFound, p.gongFound, duration)
        val base = ShotUi(
            number = number,
            name = name,
            audio = audio,
            sampleRate = rate,
            samples = samples,
            envelope = p.envelope,
            threshold = p.threshold,
            t1 = p.t1,
            t2 = p.t2,
            muzzleFound = p.muzzleFound,
            gongFound = p.gongFound,
            viewStart = vs,
            viewEnd = ve,
            v0Override = override,
            v0 = v0,
            math = null,
            decodeError = null,
        )
        return recompute(base, cond)
    }

    private fun viewWindow(t1: Double, t2: Double, muzzle: Boolean, gong: Boolean, duration: Double): Pair<Double, Double> {
        if (!muzzle) return 0.0 to duration
        val start = (t1 - 0.08).coerceAtLeast(0.0)
        val end = if (gong) (t2 + 0.25).coerceAtMost(duration) else duration
        return start to end.coerceAtLeast(start + 0.05)
    }

    private fun speedFor(number: Int, cond: SessionConditions): Double? {
        if (cond.sharedV0) return num(cond.v0)?.takeIf { it > 0 }
        return imported().getOrNull(number - 1) ?: num(cond.v0)?.takeIf { it > 0 }
    }

    private fun recompute(shot: ShotUi, cond: SessionConditions): ShotUi {
        if (shot.decodeError != null) return shot
        val c = parseCond(cond)
        val v0 = shot.v0Override ?: speedFor(shot.number, cond)
        val math = if (c != null && v0 != null && shot.muzzleFound && shot.gongFound) {
            val (g1, g7) = tables
            computeShot(shot.t1, shot.t2, v0, c.distance, c.temperature, c.pressure, c.humidity, c.microphone, g1, g7)
        } else {
            null
        }
        return shot.copy(v0 = v0, math = math)
    }

    private fun recomputeAll() {
        val cond = sessionCond ?: return
        val current = shots.toList()
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { current.map { recompute(it, cond) } }
            if (shots.size == out.size) out.forEachIndexed { i, s -> shots[i] = s }
            scheduleSave()
        }
    }

    // =============================== results ===============================

    fun select(index: Int) {
        if (index in shots.indices) selected = index
    }

    /** which = 1 SHOT marker, 2 GONG marker. Dragging or placing a marker marks it as found. */
    fun moveMarker(index: Int, which: Int, time: Double) {
        if (busy || index !in shots.indices) return
        val shot = shots[index]
        val t = time.coerceIn(0.0, shot.duration)
        shots[index] = if (which == 1) shot.copy(t1 = t, muzzleFound = true) else shot.copy(t2 = t, gongFound = true)
        val cond = sessionCond ?: return
        mathJob?.cancel()
        mathJob = viewModelScope.launch {
            delay(40)
            val target = shots.getOrNull(index) ?: return@launch
            val solved = withContext(Dispatchers.Default) { recompute(target, cond) }
            val now = shots.getOrNull(index) ?: return@launch
            if (now.t1 == solved.t1 && now.t2 == solved.t2) shots[index] = solved
            scheduleSave()
        }
    }

    fun setShotV0(index: Int, value: String) {
        val shot = shots.getOrNull(index) ?: return
        val cond = sessionCond ?: return
        val v = num(value)?.takeIf { it > 0 }
        shots[index] = recompute(shot.copy(v0Override = v), cond)
        scheduleSave()
    }

    fun stats(g7: Boolean): Stats? = statsOf(shots.filter { it.valid(g7) }.mapNotNull { it.bc(g7) })

    fun spectrogramFor(shot: ShotUi): Spectrogram? {
        val key = "${sessionId}_${shot.number}_${shot.name}"
        spectrograms[key]?.let { return it }
        if (shot.samples.isEmpty() || !specJobs.add(key)) return null
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) {
                computeSpectrogram(shot.samples, shot.sampleRate, shot.viewStart, shot.viewEnd)
            }
            spectrograms[key] = s
        }
        return null
    }

    /** What the fixed panel on Main shows: selected shot of the session, else the picked-file preview. */
    val panelShot: ShotUi? get() = shots.getOrNull(selected) ?: preview

    // =============================== history ===============================

    fun openHistory() {
        screen = Screen.HISTORY
        viewModelScope.launch {
            historyItems = withContext(Dispatchers.IO) { store.list() }
        }
    }

    fun openSession(id: Long) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val saved = withContext(Dispatchers.IO) { store.load(id) } ?: return@launch
                val built = withContext(Dispatchers.Default) {
                    saved.shots.mapIndexed { i, s ->
                        if (s.decodeError != null) return@mapIndexed errorShot(i + 1, s.name, s.decodeError)
                        val samples = store.readAudio(id, s.audio) ?: return@mapIndexed errorShot(i + 1, s.name, "audio missing")
                        val p = processRecording(
                            samples, s.sampleRate, num(saved.conditions.k),
                            num(saved.conditions.distance) ?: 100.0, s.v0 ?: 800.0,
                            Atmosphere.speedOfSound(num(saved.conditions.temperature) ?: 15.0),
                        )
                        val dur = p.envelope.size.toDouble() / s.sampleRate
                        val (vs, ve) = viewWindow(s.t1, s.t2, s.muzzleFound, s.gongFound, dur)
                        recompute(
                            ShotUi(
                                i + 1, s.name, s.audio, s.sampleRate, samples, p.envelope, p.threshold,
                                s.t1, s.t2, s.muzzleFound, s.gongFound, vs, ve, s.v0Override(saved), s.v0, null, null,
                            ),
                            saved.conditions,
                        )
                    }
                }
                files.clear()
                preview = null
                spectrograms.clear()
                specJobs.clear()
                shots.clear()
                shots.addAll(built)
                sessionId = id
                sessionCond = saved.conditions
                selected = built.indexOfFirst { it.decodeError == null }.coerceAtLeast(0)
                resultsSpectrogram = settings.spectrogram
                screen = Screen.RESULTS
            } finally {
                busy = false
            }
        }
    }

    /** V0 saved per shot is an override only when it differs from what the conditions give. */
    private fun SavedShot.v0Override(session: SavedSession): Double? {
        val v = v0 ?: return null
        val base = if (session.conditions.sharedV0) num(session.conditions.v0) else null
        return if (base != null && kotlin.math.abs(base - v) < 1e-6) null else v
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            if (sessionId == id) sessionId = null
            historyItems = withContext(Dispatchers.IO) { store.list() }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            saveSession(writeAudio = false)
        }
    }

    private suspend fun saveSession(writeAudio: Boolean) {
        val id = sessionId ?: return
        val cond = sessionCond ?: return
        val list = shots.toList()
        val g1 = stats(false)?.mean
        val g7 = stats(true)?.mean
        withContext(Dispatchers.IO) {
            if (writeAudio) list.forEach { if (it.audio.isNotEmpty()) store.writeAudio(id, it.audio, it.samples) }
            store.save(
                SavedSession(
                    id = id,
                    conditions = cond,
                    shots = list.map {
                        SavedShot(it.name, it.audio, it.sampleRate, it.t1, it.t2, it.muzzleFound, it.gongFound, it.v0, it.decodeError)
                    },
                    g1 = g1,
                    g7 = g7,
                ),
            )
        }
    }

    // =============================== export ===============================

    /** Writes the session as an Excel workbook (.xlsx). */
    fun exportExcel(uri: Uri) {
        val cond = sessionCond ?: return
        val sheet = buildSheet(shots.toList(), cond, stats(false), stats(true), tables, text())
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { writeXlsx(sheet, it) }
            message = text().exported
        } catch (e: Exception) {
            message = e.message
        }
    }

    fun exportName(): String {
        val id = sessionId ?: System.currentTimeMillis()
        return "SoundBC_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(id)) + ".xlsx"
    }

    override fun onCleared() {
        chrono.disconnect()
        if (recording) viewModelScope.launch { recorder.stop() }
        super.onCleared()
    }

    // =============================== helpers ===============================

    private fun currentCondFrom(s: Settings) = SessionConditions(
        distance = s.distance,
        v0 = s.v0,
        sharedV0 = s.sharedV0,
        caliber = s.caliber,
        weight = s.weight,
        temperature = s.temperature,
        pressure = s.pressure,
        humidity = s.humidity,
        microphone = s.microphone,
        k = s.k,
        chrono = s.imported.isNotBlank(),
    )

    private fun gongWindow(cond: SessionConditions): ClosedFloatingPointRange<Double> {
        val c = parseCond(cond)
        val d = c?.distance ?: 100.0
        val sound = Atmosphere.speedOfSound(c?.temperature ?: 15.0)
        val v = c?.sharedSpeed ?: 800.0
        val lo = (d / sound + d / v - 0.02).coerceAtLeast(0.02)
        val hi = d / sound + 2.5 * d / v + 0.1
        return lo..hi
    }

    data class Cond(
        val distance: Double,
        val temperature: Double,
        val pressure: Double,
        val humidity: Double,
        val microphone: Double,
        val k: Double?,
        val sharedSpeed: Double?,
    )

    /** Null when a required value is missing or out of range. V0 is checked per shot. */
    fun parseCond(c: SessionConditions): Cond? {
        val d = num(c.distance) ?: return null
        val t = num(c.temperature) ?: return null
        val p = num(c.pressure) ?: return null
        val rh = num(c.humidity) ?: return null
        val mic = num(c.microphone) ?: return null
        val k = if (c.k.isBlank()) null else (num(c.k) ?: return null)
        if (d <= 0 || t <= -273.15 || p <= 0 || rh !in 0.0..100.0 || mic < 0 || (k != null && k <= 0)) return null
        return Cond(d, t, p, rh, mic, k, num(c.v0)?.takeIf { it > 0 })
    }
}

fun num(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun fmt(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v)

private fun displayName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment ?: "audio"
}
