package com.example.halloapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.halloapp.data.GeoPoint
import com.example.halloapp.location.CurrentLocationHolder
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun GeoMapView(
    geoPoints: List<GeoPoint>,
    modifier: Modifier = Modifier,
    onSaveAt: (Double, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
        }
    }

    var pendingLat by remember { mutableStateOf<Double?>(null) }
    var pendingLon by remember { mutableStateOf<Double?>(null) }

    DisposableEffect(Unit) {
        mapView.overlays.add(
            0,
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean = false

                override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                    p?.let {
                        pendingLat = it.latitude
                        pendingLon = it.longitude
                    }
                    return true
                }
            })
        )
        onDispose { mapView.onDetach() }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.overlays.removeAll { it is Marker || it is Polygon }
                val currentLat = CurrentLocationHolder.lat
                val currentLon = CurrentLocationHolder.lon

                geoPoints.forEach { point ->
                    addCircles(
                        view, point.lat, point.lon,
                        blueColor = true, withMarker = true, title = point.name
                    )
                }

                val pLat = pendingLat
                val pLon = pendingLon
                if (pLat != null && pLon != null) {
                    addCircles(
                        view, pLat, pLon,
                        blueColor = false, withMarker = true, title = "Neue Position"
                    )
                }

                currentLat?.let { lat ->
                    currentLon?.let { lon ->
                        view.overlays.add(
                            Marker(view).apply {
                                position = OsmGeoPoint(lat, lon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = androidx.core.content.ContextCompat.getDrawable(
                                    view.context,
                                    android.R.drawable.ic_menu_mylocation
                                )
                                title = "Aktuelle Position"
                            }
                        )
                    }
                }
                view.invalidate()
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmallFloatingActionButton(
                onClick = {
                    val lat = CurrentLocationHolder.lat
                    val lon = CurrentLocationHolder.lon
                    if (lat != null && lon != null) {
                        mapView.controller.animateTo(OsmGeoPoint(lat, lon))
                        mapView.controller.setZoom(18.0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Zur aktuellen Position")
            }
            SmallFloatingActionButton(
                onClick = { mapView.controller.setZoom((mapView.zoomLevelDouble + 1.0).coerceAtMost(21.0)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
            SmallFloatingActionButton(
                onClick = { mapView.controller.setZoom((mapView.zoomLevelDouble - 1.0).coerceAtLeast(3.0)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("–", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (pendingLat != null && pendingLon != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        onSaveAt(pendingLat!!, pendingLon!!)
                        pendingLat = null
                        pendingLon = null
                    }
                ) {
                    Text("Position speichern…")
                }
                TextButton(
                    onClick = {
                        pendingLat = null
                        pendingLon = null
                    }
                ) {
                    Text("Markierung entfernen")
                }
            }
        }
    }
}

private fun addCircles(
    view: MapView,
    lat: Double,
    lon: Double,
    blueColor: Boolean,
    withMarker: Boolean,
    title: String
) {
    val center = OsmGeoPoint(lat, lon)
    val outline = if (blueColor) android.graphics.Color.argb(200, 33, 150, 243)
    else android.graphics.Color.argb(220, 255, 152, 0)
    val fillEnter = if (blueColor) android.graphics.Color.argb(40, 33, 150, 243)
    else android.graphics.Color.argb(50, 255, 152, 0)
    val fillExit = if (blueColor) android.graphics.Color.argb(15, 33, 150, 243)
    else android.graphics.Color.argb(20, 255, 152, 0)

    val enterCircle = Polygon(view)
    enterCircle.setPoints(Polygon.pointsAsCircle(center, ENTER_RADIUS_M))
    enterCircle.outlinePaint.color = outline
    enterCircle.outlinePaint.strokeWidth = if (blueColor) 2f else 3f
    enterCircle.fillPaint.color = fillEnter
    view.overlays.add(enterCircle)

    val exitCircle = Polygon(view)
    exitCircle.setPoints(Polygon.pointsAsCircle(center, EXIT_RADIUS_M))
    exitCircle.outlinePaint.color = outline
    exitCircle.outlinePaint.strokeWidth = if (blueColor) 2f else 3f
    exitCircle.outlinePaint.setPathEffect(android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f))
    exitCircle.fillPaint.color = fillExit
    view.overlays.add(exitCircle)

    if (withMarker) {
        view.overlays.add(
            Marker(view).apply {
                position = center
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                this.title = title
            }
        )
    }
}

private const val ENTER_RADIUS_M = 25.0
private const val EXIT_RADIUS_M = 50.0
