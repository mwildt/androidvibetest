package com.example.halloapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.halloapp.data.GeoEvent
import com.example.halloapp.data.GeoPoint
import com.example.halloapp.data.GeoStore
import com.example.halloapp.location.CurrentLocationHolder
import com.example.halloapp.location.GeoTrackingService
import com.example.halloapp.ui.GeoMapView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var store: GeoStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GeoStore(File(filesDir, "geo"))

        if (!hasPermissions()) {
            permissionLauncher.launch(permissionsToAsk().toTypedArray())
        }
        GeoTrackingService.start(this)

        setContent {
            MaterialTheme {
                GeoAppScreen(
                    store = store,
                    hasPermissions = ::hasPermissions,
                    hasBackgroundPermission = ::hasBackgroundPermission,
                    requestPermissions = { permissionLauncher.launch(permissionsToAsk().toTypedArray()) },
                    requestBackgroundPermission = { backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                    onMark = { name -> GeoTrackingService.mark(this, name) },
                    onSaveAt = { lat, lon, name -> GeoTrackingService.savePointAt(this, lat, lon, name) },
                    onStartService = { GeoTrackingService.start(this) },
                    onStopService = { GeoTrackingService.stop(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GeoTrackingService.refreshPoints(this)
    }

    private fun hasPermissions(): Boolean {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasBackgroundPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun permissionsToAsk(): MutableList<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms
    }
}

@Composable
fun GeoAppScreen(
    store: GeoStore,
    hasPermissions: () -> Boolean,
    hasBackgroundPermission: () -> Boolean,
    requestPermissions: () -> Unit,
    requestBackgroundPermission: () -> Unit,
    onMark: (String) -> Unit,
    onSaveAt: (Double, Double, String) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    var points by remember { mutableStateOf(store.loadPoints()) }
    var events by remember { mutableStateOf(store.loadEvents()) }
    var tracking by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingMapPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationIssue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            points = store.loadPoints()
            events = store.loadEvents()
            locationIssue = when {
                !hasPermissions() -> null
                CurrentLocationHolder.gpsEnabled == false &&
                    CurrentLocationHolder.networkEnabled == false ->
                    "Standort ist deaktiviert. Bitte in den Android-Einstellungen den Standort (GPS) einschalten."
                CurrentLocationHolder.lastFixAt == null && tracking ->
                    "Noch keine Position empfangen. GPS-Signal wird gesucht… (draußen funktioniert es am besten)"
                else -> null
            }
        }
    }

    if (showNameDialog) {
        NameDialog(
            onConfirm = { name ->
                showNameDialog = false
                pendingMapPoint?.let { (lat, lon) ->
                    pendingMapPoint = null
                    onSaveAt(lat, lon, name)
                } ?: run {
                    if (hasPermissions()) onMark(name) else requestPermissions()
                }
            },
            onDismiss = {
                showNameDialog = false
                pendingMapPoint = null
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (tab != 1) {
                ExtendedFloatingActionButton(
                    onClick = { showNameDialog = true }
                ) {
                    Text("Position markieren")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!hasPermissions()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Standortberechtigung erforderlich. Tippe auf den Button, um sie zu erteilen.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (!hasBackgroundPermission()) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Für Hintergrund-Logging „Immer erlauben“ für den Standort aktivieren.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = requestBackgroundPermission) {
                            Text("Erteilen")
                        }
                    }
                }
            }
            locationIssue?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Punkte (${points.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Events (${events.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Karte") })
            }
            when (tab) {
                0 -> PointsList(points)
                1 -> EventsList(events)
                else -> GeoMapView(
                    geoPoints = points,
                    modifier = Modifier.fillMaxSize(),
                    onMapLongPress = { lat, lon ->
                        pendingMapPoint = lat to lon
                        showNameDialog = true
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (tracking) "Tracking aktiv" else "Tracking gestoppt", style = MaterialTheme.typography.bodySmall)
                if (tracking) {
                    TextButton(onClick = { onStopService(); tracking = false }) {
                        Text("Tracking stoppen")
                    }
                } else {
                    TextButton(onClick = { onStartService(); tracking = true }) {
                        Text("Tracking starten")
                    }
                }
            }
        }
    }
}

@Composable
fun NameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Position markieren") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name des Ortes") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "Punkt" }) },
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun PointsList(points: List<GeoPoint>) {
    if (points.isEmpty()) {
        EmptyHint("Noch keine Punkte markiert. Tippe auf „Position markieren“.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(points.reversed(), key = { it.id }) { point ->
            ListItem(
                headlineContent = { Text(point.name) },
                supportingContent = { Text("%.5f / %.5f – %s".format(point.lat, point.lon, formatTime(point.createdAt))) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun EventsList(events: List<GeoEvent>) {
    if (events.isEmpty()) {
        EmptyHint("Noch keine Events. Nähere dich einem markierten Punkt (< 50 m) und entferne dich wieder (> 100 m).")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(events.reversed(), key = { it.id }) { event ->
            val typeText = if (event.type == "enter") "Betreten" else "Verlassen"
            ListItem(
                headlineContent = { Text("$typeText – ${event.pointName}") },
                supportingContent = {
                    Text(
                        "%.0f m – %s".format(event.distance, formatTime(event.timestamp))
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))
