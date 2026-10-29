package com.kec.tnstc.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.*
import com.kec.tnstc.data.AppLanguage
import com.kec.tnstc.data.Stop
import com.kec.tnstc.data.TamilNaduTransitDatabase
import kotlinx.coroutines.launch

@Composable
fun BusTrackingScreen(
    viewModel: TransitViewModel,
    busId: String,
    busNumber: String,
    fromStopName: String,
    toStopName: String,
    etaMinutes: Int,
    distanceKm: Double,
    availableSeats: Int,
    totalSeats: Int,
    crowdStatus: String,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isTamil = state.language == AppLanguage.TAMIL
    val liveLoc = state.liveLocation
    val coroutineScope = rememberCoroutineScope()

    val currentEta = liveLoc?.etaMinutes ?: etaMinutes
    val currentDist = liveLoc?.distanceKm ?: distanceKm
    val currentSeats = liveLoc?.availableSeats ?: availableSeats

    val fromStop = remember(fromStopName, state.stops, state.selectedBoardingStop) {
        state.stops.find { it.name.equals(fromStopName, ignoreCase = true) || it.id.equals(fromStopName, ignoreCase = true) }
            ?: state.selectedBoardingStop
            ?: TamilNaduTransitDatabase.allStops.find { it.name.equals(fromStopName, ignoreCase = true) || it.id.equals(fromStopName, ignoreCase = true) }
            ?: state.stops.firstOrNull()
    }
    val toStop = remember(toStopName, state.stops, state.selectedDestinationStop) {
        state.stops.find { it.name.equals(toStopName, ignoreCase = true) || it.id.equals(toStopName, ignoreCase = true) }
            ?: state.selectedDestinationStop
            ?: TamilNaduTransitDatabase.allStops.find { it.name.equals(toStopName, ignoreCase = true) || it.id.equals(toStopName, ignoreCase = true) }
            ?: state.stops.lastOrNull()
    }

    val fromCoords = remember(fromStop) {
        if (fromStop != null) {
            com.kec.tnstc.data.FirebaseTransitService.sanitizeCoordinates(fromStop.name, fromStop.id, fromStop.latitude, fromStop.longitude)
        } else Pair(11.340998, 77.717000)
    }
    val toCoords = remember(toStop) {
        if (toStop != null) {
            com.kec.tnstc.data.FirebaseTransitService.sanitizeCoordinates(toStop.name, toStop.id, toStop.latitude, toStop.longitude)
        } else Pair(11.277977, 77.584748)
    }

    val fromLat = fromCoords.first
    val fromLng = fromCoords.second
    val toLat = toCoords.first
    val toLng = toCoords.second

    val fromLatLng = remember(fromLat, fromLng) { LatLng(fromLat, fromLng) }
    val toLatLng = remember(toLat, toLng) { LatLng(toLat, toLng) }

    var intermediateStops: List<Stop> by remember(fromStop, toStop) {
        mutableStateOf<List<Stop>>(
            if (fromStop != null && toStop != null) viewModel.getIntermediateStops(fromStop, toStop)
            else emptyList()
        )
    }

    // Accurate road polyline strictly following the highway and authentic road nodes
    var roadPoints: List<LatLng> by remember(fromStop, toStop) {
        val initial = if (fromStop != null && toStop != null) {
            viewModel.getRouteRoadWaypoints(fromStop, toStop)
        } else {
            emptyList()
        }
        mutableStateOf<List<LatLng>>(
            if (initial.isNotEmpty()) initial.map { LatLng(it.first, it.second) }
            else listOf(fromLatLng, toLatLng)
        )
    }

    // Asynchronously fetch genuine Google Maps driving road geometry and intermediate stops
    LaunchedEffect(fromStop, toStop) {
        if (fromStop != null && toStop != null) {
            try {
                val dbIntermediates = viewModel.fetchRouteIntermediateStops(fromStop, toStop)
                intermediateStops = dbIntermediates
                val livePts = viewModel.fetchRealRoadPolyline(fromStop, toStop, dbIntermediates)
                if (livePts.isNotEmpty()) {
                    roadPoints = livePts.map { LatLng(it.first, it.second) }
                }
            } catch (_: Exception) {}
        }
    }

    // Bus position placed strictly on road coordinates
    val busLat = liveLoc?.latitude ?: (roadPoints.firstOrNull()?.latitude ?: fromLat)
    val busLng = liveLoc?.longitude ?: (roadPoints.firstOrNull()?.longitude ?: fromLng)
    val busLatLng = remember(busLat, busLng) { LatLng(busLat, busLng) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(busLatLng, 13.5f)
    }

    // Automatically frame the complete route from Boarding Stop to Destination Stop on load
    LaunchedEffect(roadPoints, fromLatLng, toLatLng) {
        if (roadPoints.isNotEmpty()) {
            try {
                val builder = com.google.android.gms.maps.model.LatLngBounds.builder()
                builder.include(fromLatLng)
                builder.include(toLatLng)
                intermediateStops.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                roadPoints.forEach { builder.include(it) }
                val bounds = builder.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120), 1000)
            } catch (_: Exception) {
                val centerLat = (fromLat + toLat) / 2
                val centerLng = (fromLng + toLng) / 2
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), 12.5f), 800)
            }
        }
    }

    var selectedAlertDistance by remember { mutableStateOf(state.alertDistanceSettingKm) }
    var showWakeConfig by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var isTrafficOn by remember { mutableStateOf(true) }

    // Context and safe initialization for Google Maps and markers
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasLocationPermission = remember(context) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        viewModel.startUserLocationUpdates()
    }

    var busMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var startMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var stopMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var stationMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var targetMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var userMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    LaunchedEffect(context) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context)
            busMarkerIcon = createLiveBusEmojiMarker()
            startMarkerIcon = createEmojiBadgeMarker("📍", AndroidColor.parseColor("#2E7D32"))
            stopMarkerIcon = createEmojiBadgeMarker("🏁", AndroidColor.parseColor("#C62828"))
            stationMarkerIcon = createEmojiBadgeMarker("🚏", AndroidColor.parseColor("#E65100"), sizePx = 90)
            targetMarkerIcon = createEmojiBadgeMarker("🎯", AndroidColor.parseColor("#EA580C"), sizePx = 110)
            userMarkerIcon = createPassengerLocationMarker()
        } catch (_: Exception) {
            try {
                busMarkerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                startMarkerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                stopMarkerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                stationMarkerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                targetMarkerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)
                userMarkerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. FULL SCREEN OFFICIAL GOOGLE MAP
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                val rKm = state.activeAlarm?.alertDistanceKm ?: state.alertDistanceSettingKm
                viewModel.setCustomMapTestAlarm(latLng.latitude, latLng.longitude, radiusKm = rKm)
            },
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = mapType,
                isTrafficEnabled = isTrafficOn
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            ),
            contentPadding = PaddingValues(top = 90.dp, bottom = 270.dp)
        ) {
            // Google Maps Driving Route - Deep Navy Outer Casing
            Polyline(
                points = roadPoints,
                color = Color(0xFF0D47A1),
                width = 20f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND
            )

            // Google Maps Driving Route - Vibrant Navigation Blue Line
            Polyline(
                points = roadPoints,
                color = Color(0xFF1976D2),
                width = 14f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND
            )

            // Google Maps Driving Route - Inner Cyan Center Highlight
            Polyline(
                points = roadPoints,
                color = Color(0xFF64B5F6),
                width = 6f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND
            )

            // Boarding Stop Marker (Green 📍)
            Marker(
                state = MarkerState(position = fromLatLng),
                title = "📍 Boarding: $fromStopName",
                icon = startMarkerIcon
            )

            // Intermediate Bus Stops (Orange 🚏)
            intermediateStops.forEach { stop ->
                val coords = remember(stop.id, stop.latitude, stop.longitude) {
                    com.kec.tnstc.data.FirebaseTransitService.sanitizeCoordinates(stop.name, stop.id, stop.latitude, stop.longitude)
                }
                Marker(
                    state = MarkerState(position = LatLng(coords.first, coords.second)),
                    title = "🚏 ${if (isTamil && stop.nameTa.isNotBlank()) stop.nameTa else stop.name}",
                    snippet = "${stop.district} • TNSTC",
                    icon = stationMarkerIcon
                )
            }

            // Destination Stop Marker (Red 🏁)
            Marker(
                state = MarkerState(position = toLatLng),
                title = "🏁 Destination: $toStopName",
                icon = stopMarkerIcon
            )

            // 🎯 Custom Tapped Test Target & Dynamic Radius Circle
            val customTarget = state.testAlarmLocation
            val activeDistKm = state.activeAlarm?.alertDistanceKm ?: state.alertDistanceSettingKm
            val activeRadiusMeters = activeDistKm * 1000.0
            if (customTarget != null) {
                val targetLatLng = LatLng(customTarget.first, customTarget.second)
                val labelDist = if (activeDistKm < 1.0) "${activeRadiusMeters.toInt()}m" else "${activeDistKm}km"
                Marker(
                    state = remember(targetLatLng) { MarkerState(position = targetLatLng) },
                    title = "🎯 $labelDist Target Point",
                    snippet = if (isTamil) "பேருந்து $labelDist சுற்றளவுக்குள் வரும்போது அலாரம் ஒலிக்கும்" else "Alarm rings when bus enters this $labelDist radius",
                    icon = targetMarkerIcon
                )
                Circle(
                    center = targetLatLng,
                    radius = activeRadiusMeters,
                    fillColor = Color(0x33F97316),
                    strokeColor = Color(0xFFEA580C),
                    strokeWidth = 3f
                )
            }

            // Destination Proximity Alarm Perimeter (e.g. Radius Circle on Destination Stop)
            val currentAlarm = state.activeAlarm
            if (currentAlarm != null && customTarget == null) {
                Circle(
                    center = toLatLng,
                    radius = currentAlarm.alertDistanceKm * 1000.0,
                    fillColor = Color(0x332E7D32),
                    strokeColor = Color(0xFF2E7D32),
                    strokeWidth = 3f
                )
            }

            // 🚌 LIVE MOVING BUS EMOJI MARKER TRAVELLING ON ROAD
            Marker(
                state = remember(busLatLng) { MarkerState(position = busLatLng) },
                title = "🚌 $busNumber",
                snippet = "ETA: $currentEta mins • Distance: ${currentDist} km",
                icon = busMarkerIcon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
            )

            // 👤 LIVE CURRENT USER / PASSENGER LOCATION MARKER
            val userLoc = state.userLocation
            if (userLoc != null && userLoc.first != 0.0 && userLoc.second != 0.0) {
                val userLatLng = remember(userLoc.first, userLoc.second) { LatLng(userLoc.first, userLoc.second) }
                Marker(
                    state = remember(userLatLng) { MarkerState(position = userLatLng) },
                    title = if (isTamil) "👤 உங்கள் இருப்பிடம்" else "👤 Your Location",
                    snippet = if (isTamil) "நேரலை இருப்பிடம்" else "Current Location",
                    icon = userMarkerIcon,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    zIndex = 3f
                )
            }
        }

        // INTERACTIVE MAP TESTING & SELECTABLE RADIUS CONTROL BAR
        val customTarget = state.testAlarmLocation
        val selectedRadiusKm = state.activeAlarm?.alertDistanceKm ?: state.alertDistanceSettingKm
        val selectedRadiusMeters = (selectedRadiusKm * 1000).toInt()
        val radiusLabel = if (selectedRadiusKm < 1.0) "${selectedRadiusMeters}m" else "${selectedRadiusKm}km"

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (customTarget != null) Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (customTarget != null) Color(0xFFEA580C) else Color(0xFF0288D1)
            ),
            shadowElevation = 5.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp, start = 12.dp, end = 68.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (customTarget != null) "🎯" else "👆", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (customTarget != null)
                                (if (isTamil) "இலக்கு வட்டம் ($radiusLabel)" else "Target Circle ($radiusLabel)")
                            else
                                (if (isTamil) "வரைபடத்தில் தட்டி இலக்கு வைக்கவும்" else "Tap map to set target circle"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (customTarget != null) Color(0xFFC2410C) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Move Bus to 200m to test proximity alarm naturally
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1565C0),
                            modifier = Modifier.clickable { viewModel.simulateBusAt200Meters() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⏩", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (isTamil) "200மீ சோதனை" else "200m Test",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Instant Ring Test Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEA580C),
                            modifier = Modifier.clickable { viewModel.testTriggerWakeAlarm() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔔", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (isTamil) "ஒலி" else "Ring",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        if (customTarget != null) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFEDD5),
                                modifier = Modifier.clickable { viewModel.clearCustomMapTestAlarm() }
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC2410C),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Quick Radius Selector Chips: 100m, 200m, 500m, 1km, 2km
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "ஆரம்:" else "Radius:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val radiusList = listOf(
                        Pair("100m", 0.1),
                        Pair("200m", 0.2),
                        Pair("500m", 0.5),
                        Pair("1km", 1.0),
                        Pair("2km", 2.0)
                    )

                    radiusList.forEach { (label, rKm) ->
                        val isSelected = Math.abs(selectedRadiusKm - rKm) < 0.05
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFFEA580C) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                viewModel.updateTestAlarmRadius(rKm)
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // TOP NAVIGATION APP BAR
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = busNumber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$fromStopName → $toStopName",
                        fontSize = 11.sp,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                // Traffic Overlay Toggle Button
                IconButton(
                    onClick = { isTrafficOn = !isTrafficOn },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isTrafficOn) Color(0xFFE8F5E9) else Color(0xFFF1F5F9))
                ) {
                    Icon(
                        imageVector = Icons.Default.Traffic,
                        contentDescription = "Toggle Traffic",
                        tint = if (isTrafficOn) Color(0xFF2E7D32) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // FLOATING MAP CONTROLS (Fit Route, Center Bus, Satellite, Alarm)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 🔍 Fit Full Route from Boarding to Destination
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val builder = com.google.android.gms.maps.model.LatLngBounds.builder()
                            builder.include(fromLatLng)
                            builder.include(toLatLng)
                            intermediateStops.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                            roadPoints.forEach { builder.include(it) }
                            val bounds = builder.build()
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120), 800)
                        } catch (_: Exception) {
                            val centerLat = (fromLat + toLat) / 2
                            val centerLng = (fromLng + toLng) / 2
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), 12.5f), 800)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF1565C0),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.ZoomOutMap, contentDescription = "View Entire Route", modifier = Modifier.size(20.dp))
            }

            // 📍 Re-Center on My Live Location
            FloatingActionButton(
                onClick = {
                    val loc = state.userLocation
                    if (loc != null && loc.first != 0.0 && loc.second != 0.0) {
                        coroutineScope.launch {
                            try {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 16f),
                                    600
                                )
                            } catch (_: Exception) {}
                        }
                    } else {
                        viewModel.startUserLocationUpdates()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF0288D1),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(20.dp))
            }

            // 🚌 Re-Center on Live Bus
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(busLatLng, 15.5f), 600)
                        } catch (_: Exception) {}
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF1565C0),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.DirectionsBus, contentDescription = "Center Bus", modifier = Modifier.size(20.dp))
            }

            // Map Layer Switcher (Normal / Hybrid Satellite)
            FloatingActionButton(
                onClick = {
                    mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF334155),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Switch Map Style", modifier = Modifier.size(20.dp))
            }

            // Wake Alarm Quick Button
            FloatingActionButton(
                onClick = { showWakeConfig = true },
                containerColor = if (state.activeAlarm != null) Color(0xFF2E7D32) else Color(0xFF1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (state.activeAlarm != null) Icons.Default.AlarmOn else Icons.Default.AddAlarm,
                    contentDescription = "Set Wake Alarm",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // BOTTOM OFFICIAL TELEMETRY DASHBOARD
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // PRIMARY LIVE STATUS ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTamil) "நேரடி வருகை நேரம்" else "Live Arrival ETA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$currentEta ${if (isTamil) "நிமிடங்கள்" else "Minutes"}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1565C0)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isTamil) "தூரம்" else "Distance",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currentDist km",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(14.dp))

                // TELEMETRY CHIPS (Seats, Speed, Live GPS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Seats Available
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isTamil) "இருக்கைகள்" else "Seats Available",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "$currentSeats / $totalSeats",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }

                    // Speed
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE0F2FE),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isTamil) "வேகம்" else "Live Speed",
                                fontSize = 11.sp,
                                color = Color(0xFF0369A1)
                            )
                            Text(
                                text = "42 km/h",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF075985)
                            )
                        }
                    }

                    // GPS Signal
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "GPS Road Sync",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "🟢 Live",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // WAKE ME UP ALARM BANNER
                val alarmDistLabel = if (state.alertDistanceSettingKm < 1.0) {
                    "${(state.alertDistanceSettingKm * 1000).toInt()}m"
                } else {
                    "${state.alertDistanceSettingKm} km"
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.activeAlarm != null) Color(0xFFE8F5E9) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.activeAlarm != null) Color(0xFF4CAF50) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWakeConfig = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.activeAlarm != null) Icons.Default.NotificationsActive else Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (state.activeAlarm != null) Color(0xFF2E7D32) else Color(0xFF475569),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (state.activeAlarm != null)
                                        (if (isTamil) "விழிப்பு அலாரம் இயங்குகிறது" else "Wake-up Alarm Active")
                                    else
                                        (if (isTamil) "இறங்குமிடம் விழிப்பு அலாரம் அமைக்கவும்" else "Set Wake-up Stop Alarm"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.activeAlarm != null) Color(0xFF1B5E20) else Color(0xFF1E293B)
                                )
                                Text(
                                    text = "${if (isTamil) "சேருமிடத்திற்கு" else "Alert within"} $alarmDistLabel",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // WAKE UP ALARM CONFIGURATION DIALOG
        if (showWakeConfig) {
            Dialog(onDismissRequest = { showWakeConfig = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFF1565C0))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTamil) "இறங்குமிடம் விழிப்பு அலாரம்" else "Destination Wake-Up Alarm",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isTamil)
                                "சேருமிடம் வருவதற்கு எத்தனை முன்னால் அலாரம் ஒலிக்க வேண்டும்?"
                            else
                                "Choose radius before destination to sound the alarm:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Radius selection chips (200m testing, 500m, 1km, 2km)
                        val alarmDistances = listOf(
                            0.2 to (if (isTamil) "200மீ\n(பரிசோதனை)" else "200m\n(Testing)"),
                            0.5 to (if (isTamil) "500மீ" else "500m"),
                            1.0 to (if (isTamil) "1 கி.மீ" else "1 km"),
                            2.0 to (if (isTamil) "2 கி.மீ" else "2 km")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            alarmDistances.forEach { (dist, label) ->
                                val isSel = selectedAlertDistance == dist
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) Color(0xFF1565C0) else Color(0xFFF1F5F9),
                                    border = if (dist == 0.2 && !isSel) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedAlertDistance = dist }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else Color(0xFF334155),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Real-Time Distance info card
                        val distMeters = (currentDist * 1000).toInt()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🏁 ${if (isTamil) "சேருமிடம்" else "Destination"}: $toStopName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isTamil)
                                        "தற்போதைய தூரம்: ${String.format("%.2f", currentDist)} கி.மீ ($distMeters மீட்டர்)"
                                    else
                                        "Current bus distance: ${String.format("%.2f", currentDist)} km ($distMeters meters)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive Instant Testing Section
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF7ED),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isTamil) "அலாரத்தை உடனே சோதிக்க (Testing):" else "Test Alarm Immediately:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC2410C)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.testTriggerWakeAlarm()
                                            showWakeConfig = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isTamil) "🔔 அலாரம் ஒலி" else "🔔 Ring Alarm Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.simulateBusAt200Meters()
                                            showWakeConfig = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isTamil) "⚡ 200மீ பேருந்து" else "⚡ Bus at 200m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.activeAlarm != null) {
                                TextButton(onClick = {
                                    viewModel.cancelWakeAlert()
                                    showWakeConfig = false
                                }) {
                                    Text(if (isTamil) "ரத்துசெய்" else "Cancel Alert", color = Color(0xFFD32F2F))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            val activeLabel = if (selectedAlertDistance < 1.0) {
                                "${(selectedAlertDistance * 1000).toInt()}${if (isTamil) "மீ" else "m"}"
                            } else {
                                "${selectedAlertDistance}${if (isTamil) "கி.மீ" else "km"}"
                            }

                            Button(
                                onClick = {
                                    viewModel.setWakeMeUpAlert(selectedAlertDistance)
                                    showWakeConfig = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) {
                                Text(
                                    text = if (isTamil) "இயக்கு ($activeLabel)" else "Activate ($activeLabel)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Creates an authentic Live 🚌 Bus Emoji Marker with double shadow, navy bezel, and pulse ring.
 */
fun createLiveBusEmojiMarker(): BitmapDescriptor {
    return try {
        val sizePx = 130
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val center = sizePx / 2f

        // Radar Pulse Outer Ring
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(75, 33, 150, 243)
        }
        canvas.drawCircle(center, center, center - 2f, pulsePaint)

        // Dark Bezel
        val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#0F172A")
        }
        canvas.drawCircle(center, center, center - 12f, bezelPaint)

        // Inner White Badge
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
        }
        canvas.drawCircle(center, center, center - 18f, whitePaint)

        // 🚌 Bus Emoji
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 52f
            textAlign = Paint.Align.CENTER
        }
        val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText("🚌", center, textY, textPaint)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (_: Exception) {
        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
    }
}

/**
 * Creates custom Emoji Badges for Stops (📍, 🏁, 🚏, 🎯)
 */
fun createEmojiBadgeMarker(emoji: String, borderColor: Int, sizePx: Int = 110): BitmapDescriptor {
    return try {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val center = sizePx / 2f

        // Outer Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(60, 0, 0, 0)
        }
        canvas.drawCircle(center, center - 2f, center - 4f, shadowPaint)

        // Outer Bezel
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
        }
        canvas.drawCircle(center, center - 4f, center - 8f, borderPaint)

        // Inner White Fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
        }
        canvas.drawCircle(center, center - 4f, center - 16f, fillPaint)

        // Emoji Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (sizePx * 0.42f)
            textAlign = Paint.Align.CENTER
        }
        val textY = center - 4f - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(emoji, center, textY, textPaint)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (_: Exception) {
        BitmapDescriptorFactory.defaultMarker()
    }
}

/**
 * Creates a clean, high-visibility Passenger Location Marker (👤 You) with cyan pulse and blue bezel.
 */
fun createPassengerLocationMarker(): BitmapDescriptor {
    return try {
        val sizePx = 110
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val center = sizePx / 2f

        // Cyan Pulse Ring
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(60, 2, 136, 209)
        }
        canvas.drawCircle(center, center, center - 2f, pulsePaint)

        // Dark Cyan Bezel
        val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#0288D1")
        }
        canvas.drawCircle(center, center, center - 8f, bezelPaint)

        // Inner White Fill
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
        }
        canvas.drawCircle(center, center, center - 14f, whitePaint)

        // 👤 Emoji Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText("👤", center, textY, textPaint)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (_: Exception) {
        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
    }
}


