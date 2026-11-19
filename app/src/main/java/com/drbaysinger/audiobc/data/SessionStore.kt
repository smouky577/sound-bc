package com.drbaysinger.audiobc.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Conditions and inputs the session was analyzed with. */
data class SessionConditions(
    val distance: String,
    val v0: String,
    val sharedV0: Boolean,
    val caliber: String,
    val weight: String,
    val temperature: String,
    val pressure: String,
    val humidity: String,
    val microphone: String,
    val k: String,
    /** V0 (and the slug weight) were imported from the chronograph. */
    val chrono: Boolean = false,
)

data class SavedShot(
    val name: String,
    val audio: String,
    val sampleRate: Int,
    val t1: Double,
    val t2: Double,
    val muzzleFound: Boolean,
    val gongFound: Boolean,
    val v0: Double?,
    val decodeError: String?,
)

data class SessionSummary(
    val id: Long,
    val shots: Int,
    val valid: Int,
    val g1: Double?,
    val g7: Double?,
    val distance: String,
)

data class SavedSession(
    val id: Long,
    val conditions: SessionConditions,
    val shots: List<SavedShot>,
    val g1: Double?,
    val g7: Double?,
)

/**
 * Every analyzed session is kept under files/sessions/<id>/:
 * session.json (markers, conditions, V0, results) and one 16-bit PCM file per shot,
 * so a session can be reopened with its waveform and draggable markers.
 */
class SessionStore(context: Context) {
    private val root = File(context.filesDir, "sessions").apply { mkdirs() }

    fun dir(id: Long) = File(root, id.toString()).apply { mkdirs() }

    fun writeAudio(id: Long, fileName: String, samples: FloatArray) {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) buffer.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        File(dir(id), fileName).writeBytes(buffer.array())
    }

    fun readAudio(id: Long, fileName: String): FloatArray? {
        val file = File(File(root, id.toString()), fileName)
        if (!file.exists()) return null
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / 2) { buffer.short / 32768f }
    }

    fun save(session: SavedSession) {
        val json = JSONObject()
        json.put("id", session.id)
        val c = session.conditions
        json.put(
            "conditions",
            JSONObject()
                .put("distance", c.distance).put("v0", c.v0).put("sharedV0", c.sharedV0)
                .put("caliber", c.caliber).put("weight", c.weight)
                .put("temperature", c.temperature).put("pressure", c.pressure)
                .put("humidity", c.humidity).put("microphone", c.microphone).put("k", c.k)
                .put("chrono", c.chrono),
        )
        val shots = JSONArray()
        session.shots.forEach { s ->
            shots.put(
                JSONObject()
                    .put("name", s.name).put("audio", s.audio).put("rate", s.sampleRate)
                    .put("t1", s.t1).put("t2", s.t2)
                    .put("muzzle", s.muzzleFound).put("gong", s.gongFound)
                    .put("v0", s.v0 ?: JSONObject.NULL)
                    .put("error", s.decodeError ?: JSONObject.NULL),
            )
        }
        json.put("shots", shots)
        json.put("g1", session.g1 ?: JSONObject.NULL)
        json.put("g7", session.g7 ?: JSONObject.NULL)
        val file = File(dir(session.id), "session.json")
        val tmp = File(file.parentFile, "session.json.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file).also { if (!it) file.writeText(json.toString()) }
    }

    fun load(id: Long): SavedSession? = try {
        val json = JSONObject(File(File(root, id.toString()), "session.json").readText())
        val c = json.getJSONObject("conditions")
        val shots = json.getJSONArray("shots")
        SavedSession(
            id = json.getLong("id"),
            conditions = SessionConditions(
                distance = c.optString("distance"),
                v0 = c.optString("v0"),
                sharedV0 = c.optBoolean("sharedV0", true),
                caliber = c.optString("caliber"),
                weight = c.optString("weight"),
                temperature = c.optString("temperature"),
                pressure = c.optString("pressure"),
                humidity = c.optString("humidity"),
                microphone = c.optString("microphone"),
                k = c.optString("k"),
                chrono = c.optBoolean("chrono", false),
            ),
            shots = (0 until shots.length()).map { i ->
                val s = shots.getJSONObject(i)
                SavedShot(
                    name = s.optString("name"),
                    audio = s.optString("audio"),
                    sampleRate = s.optInt("rate"),
                    t1 = s.optDouble("t1"),
                    t2 = s.optDouble("t2"),
                    muzzleFound = s.optBoolean("muzzle"),
                    gongFound = s.optBoolean("gong"),
                    v0 = if (s.isNull("v0")) null else s.optDouble("v0"),
                    decodeError = if (s.isNull("error")) null else s.optString("error"),
                )
            },
            g1 = if (json.isNull("g1")) null else json.optDouble("g1"),
            g7 = if (json.isNull("g7")) null else json.optDouble("g7"),
        )
    } catch (_: Exception) {
        null
    }

    fun list(): List<SessionSummary> = (root.listFiles() ?: emptyArray())
        .mapNotNull { it.name.toLongOrNull() }
        .sortedDescending()
        .mapNotNull { id ->
            val s = load(id) ?: return@mapNotNull null
            SessionSummary(
                id = id,
                shots = s.shots.size,
                valid = s.shots.count { it.gongFound && it.decodeError == null },
                g1 = s.g1,
                g7 = s.g7,
                distance = s.conditions.distance,
            )
        }

    fun delete(id: Long) {
        File(root, id.toString()).deleteRecursively()
    }
}
