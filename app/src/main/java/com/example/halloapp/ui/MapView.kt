package com.example.halloapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.halloapp.data.GeoPoint
import com.example.halloapp.location.CurrentLocationHolder
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun GeoMapView(geoPoints: List<GeoPoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    AndroidView(
        modifier = modifier,
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
                    view.controller.animateTo(OsmGeoPoint(lat, lon))
                }
            }
            view.invalidate()
        }
    )
}

private const val ENTER_RADIUS_M = 50.0
private const val EXIT_RADIUS_M = 100.0
