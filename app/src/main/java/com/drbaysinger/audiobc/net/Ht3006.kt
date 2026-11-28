package com.drbaysinger.audiobc.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Live link to the HT-X3006 / HT-50 chronograph.
 *
 * The chronograph does not use Bluetooth: it opens its own Wi-Fi hotspot
 * (SSID "HT-X3000" or "HT50", password 88888888) and serves an HTML page with the
 * shot list "1: 812.3", "2: 809.8", … Same protocol as the Chrono app
 * (Projects/Chrono, ChronoViewModel).
 *
 * [connect] joins the hotspot for this app only and keeps it until [disconnect]. The page is
 * polled every second. The shots already stored on the chronograph when the link comes up are
 * ignored; only shots fired after that are reported, in order.
 */
class Ht3006(context: Context) {
    private val app = context.applicationContext
    private val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    /** Snapshot sent to the listener after every change. */
    data class Live(val state: State, val shots: List<Double>, val weightGrams: Double?)

    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var network: Network? = null
    private var poller: Job? = null
    private var host: String? = null
    private var missed = 0

    /** Raw list on the device at the last poll, null until the first page (the baseline) was read. */
    private var lastSeen: List<Double>? = null
    private val shots = ArrayList<Double>()
    private var weight: Double? = null
    private var state = State.DISCONNECTED
    private var listener: (Live) -> Unit = {}

    val isActive: Boolean get() = poller != null

    fun connect(scope: CoroutineScope, onUpdate: (Live) -> Unit) {
        disconnect()
        listener = onUpdate
        lastSeen = null
        shots.clear()
        weight = null
        host = null
        missed = 0
        setState(State.CONNECTING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) requestHotspot(0)
        poller = scope.launch(Dispatchers.IO) {
            while (isActive) {
                poll()
                delay(if (state == State.CONNECTED) POLL_MS else RETRY_MS)
            }
        }
    }

    fun disconnect() {
        poller?.cancel()
        poller = null
        callback?.let { cb -> runCatching { connectivity.unregisterNetworkCallback(cb) } }
        callback = null
        network = null
        if (state != State.DISCONNECTED) setState(State.DISCONNECTED)
    }

    /** Starts a new series: shots collected so far are dropped, the link stays up. */
    fun clearShots() {
        synchronized(shots) { shots.clear() }
        emit()
    }

    private fun requestHotspot(index: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || index >= SSIDS.size) return
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(SSIDS[index])
            .setWpa2Passphrase(PASSWORD)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                network = n
                host = null
                missed = 0
            }

            override fun onLost(n: Network) {
                // Android drops this routinely for internet-less APs while the link still works;
                // the poll result decides the state.
                if (network == n) network = null
                host = null
            }

            override fun onUnavailable() {
                // This SSID is not around: try the other model's name.
                callback = null
                if (index + 1 < SSIDS.size) requestHotspot(index + 1)
                else if (state != State.CONNECTED) setState(State.FAILED)
            }
        }
        callback = cb
        try {
            connectivity.requestNetwork(request, cb, 30_000)
        } catch (_: Exception) {
            callback = null
            setState(State.FAILED)
        }
    }

    private suspend fun poll() = withContext(Dispatchers.IO) {
        val net = network
        val known = host
        val text = if (known != null) {
            fetch("http://$known/", net)?.let(::htmlToText)
        } else {
            var found: String? = null
            for (h in HOSTS) {
                val t = fetch("http://$h/", net)?.let(::htmlToText) ?: continue
                if (looksLikeChrono(t) || net != null) {
                    host = h
                    found = t
                    break
                }
            }
            found
        }
        if (text == null) {
            if (++missed >= MAX_MISSED) {
                host = null
                if (state == State.CONNECTED) setState(State.CONNECTING)
            }
            return@withContext
        }
        missed = 0
        onPage(text)
        if (state != State.CONNECTED) setState(State.CONNECTED)
    }

    private var lastText: String? = null

    private fun onPage(text: String) {
        // Field-test log: every new chronograph page (adb logcat -s SoundBC-HT).
        if (text != lastText) {
            lastText = text
            android.util.Log.i("SoundBC-HT", "page: " + text.take(3000))
        }
        val page = parsePage(text)
        page.weightGrams?.let { weight = it }
        val previous = lastSeen
        if (previous == null) {
            lastSeen = page.shots // baseline: what is already stored on the chronograph is ignored
            emit()
            return
        }
        if (page.shots != previous) {
            val added = newShots(previous, page.shots)
            lastSeen = page.shots
            if (added.isNotEmpty()) synchronized(shots) { shots.addAll(added) }
            emit()
        }
    }

    private fun setState(s: State) {
        state = s
        emit()
    }

    private fun emit() {
        val copy = synchronized(shots) { shots.toList() }
        listener(Live(state, copy, weight))
    }

    private fun fetch(url: String, network: Network?): String? = try {
        val connection = (network?.openConnection(URL(url)) ?: URL(url).openConnection()) as HttpURLConnection
        connection.connectTimeout = 1500
        connection.readTimeout = 2500
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }.takeIf { it.isNotEmpty() }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    /** Shots on one page, oldest first, and the slug weight if the page shows it. */
    data class Page(val shots: List<Double>, val weightGrams: Double?)

    companion object {
        val SSIDS = listOf("HT-X3000", "HT50")
        const val PASSWORD = "88888888"
        val HOSTS = listOf("192.168.4.1", "8.8.8.8", "192.168.1.1", "192.168.0.1")
        private const val POLL_MS = 1000L
        private const val RETRY_MS = 1500L
        private const val MAX_MISSED = 3
        /** Accept anything the chronograph shows, even a finger flick (~1 m/s) when testing indoors. */
        private const val MIN_SPEED = 0.1
        private const val MAX_SPEED = 1500.0

        private val SHOT_REGEX = "(?<![\\d.])(\\d{1,3})\\s*[:：]\\s*(\\d+(?:[.,]\\d+)?)".toRegex()
        private val VELOCITY_REGEX = "(?i)(?:velocity|speed|скорост|速度|初速)[^0-9]*(\\d+(?:[.,]\\d+)?)".toRegex()
        private val FIRE_RATE_REGEX = "(?i)(?:firerate|fire rate|射速)[^0-9]*(\\d+(?:\\.\\d+)?)".toRegex()
        private val WEIGHT_REGEX = "(?i)(?:weight|wgt|mass|pellet|тегло|重量|弹重)[^0-9]*(\\d+(?:[.,]\\d+)?)".toRegex()
        private val MARKER_REGEX = "(?i)(m/s|fps|ft/s|速度|射速|chrono)".toRegex()

        fun htmlToText(html: String): String = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</(p|div|tr|li|td|th)>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        fun looksLikeChrono(text: String) =
            FIRE_RATE_REGEX.containsMatchIn(text) || MARKER_REGEX.containsMatchIn(text)

        private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

        /** Shot list in m/s, oldest first (a page that only shows fps is converted). */
        fun parsePage(text: String): Page {
            val fps = text.contains("fps", true) && !text.contains("m/s", true)
            val scale = if (fps) 0.3048 else 1.0
            val raw = SHOT_REGEX.findAll(text)
                .mapNotNull { num(it.groupValues[2]) }
                .map { it * scale }
                .filter { it in MIN_SPEED..MAX_SPEED }
                .toList()
            val live = VELOCITY_REGEX.find(text)?.groupValues?.get(1)?.let(::num)?.times(scale)?.takeIf { it in MIN_SPEED..MAX_SPEED }
            val weight = WEIGHT_REGEX.find(text)?.groupValues?.get(1)?.let(::num)?.takeIf { it in 0.01..1000.0 }
            return Page(chronological(raw, live), weight)
        }

        /** Some pages list the newest shot first; the live reading tells which end is newest. */
        private fun chronological(raw: List<Double>, live: Double?): List<Double> {
            if (raw.isEmpty()) return if (live != null) listOf(live) else emptyList()
            if (live == null || raw.size == 1) return raw
            return if (abs(raw.first() - live) + 0.05 < abs(raw.last() - live)) raw.asReversed() else raw
        }

        /**
         * Shots that appeared between two polls: the longest tail of [before] that is a head of
         * [after] is the overlap, the rest of [after] is new (the device list may scroll).
         */
        fun newShots(before: List<Double>, after: List<Double>): List<Double> {
            for (i in minOf(before.size, after.size) downTo 1) {
                if (before.takeLast(i) == after.take(i)) return after.drop(i)
            }
            return after
        }
    }
}
