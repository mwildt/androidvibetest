package com.example.halloapp.data

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GeoPoint(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val createdAt: Long
)

data class GeoEvent(
    val id: Long,
    val pointId: Long,
    val type: String,
    val lat: Double,
    val lon: Double,
    val distance: Float,
    val timestamp: Long
)

class GeoStore(private val dir: File) {
    private val pointsFile = File(dir, "points.json")
    private val eventsFile = File(dir, "events.json")

    private val lock = Any()

    fun loadPoints(): List<GeoPoint> = synchronized(lock) {
        readList(pointsFile).map { it.toPoint() }
    }

    fun loadEvents(): List<GeoEvent> = synchronized(lock) {
        readList(eventsFile).map { it.toEvent() }
    }

    fun savePoints(points: List<GeoPoint>) = synchronized(lock) {
        writeList(pointsFile, points.map { it.toJson() })
    }

    fun appendEvent(event: GeoEvent) = synchronized(lock) {
        val events = loadEvents().toMutableList()
        events.add(event)
        writeList(eventsFile, events.map { it.toJson() })
    }

    private fun readList(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val text = file.readText()
            if (text.isBlank()) emptyList() else JSONArray(text).mapObjects()
        }.getOrDefault(emptyList())
    }

    private fun writeList(file: File, json: List<JSONObject>) {
        val arr = JSONArray()
        json.forEach { arr.put(it) }
        dir.mkdirs()
        file.writeText(arr.toString())
    }

    private fun JSONArray.mapObjects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.toPoint() = GeoPoint(
        id = getLong("id"),
        lat = getDouble("lat"),
        lon = getDouble("lon"),
        createdAt = getLong("createdAt")
    )

    private fun JSONObject.toEvent() = GeoEvent(
        id = getLong("id"),
        pointId = getLong("pointId"),
        type = getString("type"),
        lat = getDouble("lat"),
        lon = getDouble("lon"),
        distance = getDouble("distance").toFloat(),
        timestamp = getLong("timestamp")
    )

    private fun GeoPoint.toJson() = JSONObject()
        .put("id", id)
        .put("lat", lat)
        .put("lon", lon)
        .put("createdAt", createdAt)

    private fun GeoEvent.toJson() = JSONObject()
        .put("id", id)
        .put("pointId", pointId)
        .put("type", type)
        .put("lat", lat)
        .put("lon", lon)
        .put("distance", distance.toDouble())
        .put("timestamp", timestamp)

    companion object {
        fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val results = FloatArray(3)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }
    }
}
