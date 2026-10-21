package com.example.halloapp.location

object CurrentLocationHolder {
    @Volatile
    var lat: Double? = null

    @Volatile
    var lon: Double? = null
}
