package com.example.halloapp.ui

import android.view.View
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
fun GeoMapView(points: List<GeoPoint>, modifier: Modifier = Modifier) {
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

            points.forEach { point ->
                val center = OsmGeoPoint(point.lat, point.lon)
                view.overlays.add(
                    Polygon(view.context).apply {
                        points = Polygon.pointsAsCircle(center, 50.0)
                        outlinePaint.color = android.graphics.Color.argb(200, 33, 150, 243)
                        outlinePaint.strokeWidth = 2f
                        fillPaint.color = android.graphics.Color.argb(40, 33, 150, 243)
                    }
                )
                view.overlays.add(
                    Polygon(view.context).apply {
                        points = Polygon.pointsAsCircle(center, 100.0)
                        outlinePaint.color = android.graphics.Color.argb(200, 33, 150, 243)
                        outlinePaint.strokeWidth = 2f
                        outlinePaint.setPathEffect(android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f))
                        fillPaint.color = android.graphics.Color.argb(15, 33, 150, 243)
                    }
                )
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
