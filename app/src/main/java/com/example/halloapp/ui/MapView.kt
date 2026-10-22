package com.example.halloapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
    onMapLongPress: (Double, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
        }
    }

    DisposableEffect(Unit) {
        mapView.overlays.add(
            0,
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean = false

                override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                    p?.let { onMapLongPress(it.latitude, it.longitude) }
                    return true
                }
            })
        )
        onDispose { mapView.onDetach() }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { mapView },
            update = { view ->
                view.overlays.removeAll { it is Marker || it is Polygon }
                val currentLat = CurrentLocationHolder.lat
                val currentLon = CurrentLocationHolder.lon

                geoPoints.forEach { point ->
                    val center = OsmGeoPoint(point.lat, point.lon)

                    val enterCircle = Polygon(view)
                    enterCircle.setPoints(Polygon.pointsAsCircle(center, ENTER_RADIUS_M))
                    enterCircle.outlinePaint.color = android.graphics.Color.argb(200, 33, 150, 243)
                    enterCircle.outlinePaint.strokeWidth = 2f
                    enterCircle.fillPaint.color = android.graphics.Color.argb(40, 33, 150, 243)
                    view.overlays.add(enterCircle)

                    val exitCircle = Polygon(view)
                    exitCircle.setPoints(Polygon.pointsAsCircle(center, EXIT_RADIUS_M))
                    exitCircle.outlinePaint.color = android.graphics.Color.argb(200, 33, 150, 243)
                    exitCircle.outlinePaint.strokeWidth = 2f
                    exitCircle.outlinePaint.setPathEffect(android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f))
                    exitCircle.fillPaint.color = android.graphics.Color.argb(15, 33, 150, 243)
                    view.overlays.add(exitCircle)

                    view.overlays.add(
                        Marker(view).apply {
                            position = center
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = point.name
                        }
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
    }
}

private const val ENTER_RADIUS_M = 25.0
private const val EXIT_RADIUS_M = 50.0
