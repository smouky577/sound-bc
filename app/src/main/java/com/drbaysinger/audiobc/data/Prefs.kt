package com.drbaysinger.audiobc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Where a condition value came from. Drives the colored dot on its chip. */
enum class Source { BAROMETER, METEO, MANUAL }

private val Context.store: DataStore<Preferences> by preferencesDataStore(
    name = "soundbc",
    produceMigrations = { context ->
        listOf(
            androidx.datastore.preferences.SharedPreferencesMigration(context, "audiobc"),
        )
    },
)

/** Every user setting, persisted with DataStore between launches. */
data class Settings(
    val bulgarian: Boolean = true,
    val distance: String = "100",
    val v0: String = "",
    val sharedV0: Boolean = true,
    val caliber: String = "",
    val weight: String = "",
    val temperature: String = "15",
    val pressure: String = "1013",
    val humidity: String = "50",
    val microphone: String = "0.5",
    val k: String = "",
    val tSource: Source = Source.MANUAL,
    val pSource: Source = Source.MANUAL,
    val rhSource: Source = Source.MANUAL,
    val weatherPlace: String = "",
    val weatherTime: String = "",
    val spectrogram: Boolean = false,
    val g7: Boolean = false,
    val imported: String = "",
)

class Prefs(private val context: Context) {
    private object K {
        val bg = booleanPreferencesKey("bg")
        val d = stringPreferencesKey("d")
        val v0 = stringPreferencesKey("v0")
        val shared = booleanPreferencesKey("shared")
        val cal = stringPreferencesKey("cal")
        val w = stringPreferencesKey("w")
        val t = stringPreferencesKey("t")
        val p = stringPreferencesKey("p")
        val rh = stringPreferencesKey("rh")
        val mic = stringPreferencesKey("mic")
        val k = stringPreferencesKey("k2")
        val tS = stringPreferencesKey("t_src")
        val pS = stringPreferencesKey("p_src")
        val rhS = stringPreferencesKey("rh_src")
        val place = stringPreferencesKey("wx_place")
        val time = stringPreferencesKey("wx_time")
        val spec = booleanPreferencesKey("spec")
        val g7 = booleanPreferencesKey("g7")
        val imported = stringPreferencesKey("imported")
    }

    fun loadBlocking(): Settings = runBlocking {
        val p = try {
            context.store.data.first()
        } catch (_: Exception) {
            return@runBlocking Settings()
        }
        val def = Settings()
        fun src(key: Preferences.Key<String>) =
            p[key]?.let { runCatching { Source.valueOf(it) }.getOrNull() } ?: Source.MANUAL
        Settings(
            bulgarian = p[K.bg] ?: def.bulgarian,
            distance = p[K.d] ?: def.distance,
            v0 = p[K.v0] ?: def.v0,
            sharedV0 = p[K.shared] ?: def.sharedV0,
            caliber = p[K.cal] ?: def.caliber,
            weight = p[K.w] ?: def.weight,
            temperature = p[K.t] ?: def.temperature,
            pressure = p[K.p] ?: def.pressure,
            humidity = p[K.rh] ?: def.humidity,
            microphone = p[K.mic] ?: def.microphone,
            k = p[K.k] ?: def.k,
            tSource = src(K.tS),
            pSource = src(K.pS),
            rhSource = src(K.rhS),
            weatherPlace = p[K.place] ?: "",
            weatherTime = p[K.time] ?: "",
            spectrogram = p[K.spec] ?: false,
            g7 = p[K.g7] ?: false,
            imported = p[K.imported] ?: "",
        )
    }

    suspend fun save(s: Settings) {
        context.store.edit { p: MutablePreferences ->
            p[K.bg] = s.bulgarian
            p[K.d] = s.distance
            p[K.v0] = s.v0
            p[K.shared] = s.sharedV0
            p[K.cal] = s.caliber
            p[K.w] = s.weight
            p[K.t] = s.temperature
            p[K.p] = s.pressure
            p[K.rh] = s.humidity
            p[K.mic] = s.microphone
            p[K.k] = s.k
            p[K.tS] = s.tSource.name
            p[K.pS] = s.pSource.name
            p[K.rhS] = s.rhSource.name
            p[K.place] = s.weatherPlace
            p[K.time] = s.weatherTime
            p[K.spec] = s.spectrogram
            p[K.g7] = s.g7
            p[K.imported] = s.imported
        }
    }
}
