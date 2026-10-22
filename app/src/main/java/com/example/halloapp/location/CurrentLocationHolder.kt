package com.example.halloapp.location

object CurrentLocationHolder {
    @Volatile
    var lat: Double? = null

    @Volatile
    var lon: Double? = null

    @Volatile
    var lastFixAt: Long? = null

    @Volatile
    var gpsEnabled: Boolean? = null

    @Volatile
    var networkEnabled: Boolean? = null
}
