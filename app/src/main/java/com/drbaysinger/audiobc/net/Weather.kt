package com.drbaysinger.audiobc.net

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class WeatherNow(
    val temperature: Double?,
    val humidity: Double?,
    /** Station (surface) pressure, hPa. */
    val surfacePressure: Double?,
)

/**
 * Current conditions:
 * - pressure from the phone barometer (Sensor.TYPE_PRESSURE is station pressure, hPa);
 * - temperature, humidity and the fallback surface_pressure from Open-Meteo by GPS (no API key).
 */
class WeatherSource(context: Context) {
    private val app = context.applicationContext

    fun hasBarometer(): Boolean {
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    /** Average of ~10 barometer readings, or null without a sensor. */
    suspend fun barometer(): Double? {
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return null
        return withTimeoutOrNull(4000) {
            suspendCancellableCoroutine { cont ->
                val values = ArrayList<Float>()
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        values += event.values[0]
                        if (values.size >= 10) {
                            sm.unregisterListener(this)
                            if (cont.isActive) cont.resume(values.average())
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }?.takeIf { it in 300.0..1100.0 }
    }

    @SuppressLint("MissingPermission")
    suspend fun location(): Location? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < 30 * 60_000) return recent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in providers) {
                val fresh = withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        try {
                            lm.getCurrentLocation(provider, null, app.mainExecutor) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        } catch (_: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return fresh
            }
        } else {
            for (provider in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
                val fresh = withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (cont.isActive) cont.resume(location)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                            override fun onProviderEnabled(p: String) {}
                            override fun onProviderDisabled(p: String) {}
                        }
                        try {
                            @Suppress("DEPRECATION")
                            lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                        } catch (_: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                        cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return recent
    }

    suspend fun openMeteo(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,surface_pressure",
            lat,
            lon,
        )
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            try {
                if (c.responseCode !in 200..299) return@withContext null
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val cur = json.getJSONObject("current")
                fun d(k: String) = if (cur.has(k) && !cur.isNull(k)) cur.getDouble(k) else null
                WeatherNow(d("temperature_2m"), d("relative_humidity_2m"), d("surface_pressure"))
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    suspend fun placeName(loc: Location): String = withContext(Dispatchers.IO) {
        val fallback = String.format(Locale.US, "%.3f, %.3f", loc.latitude, loc.longitude)
        if (!Geocoder.isPresent()) return@withContext fallback
        try {
            val a = Geocoder(app).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
