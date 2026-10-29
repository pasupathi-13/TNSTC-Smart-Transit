package com.kec.tnstc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kec.tnstc.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

data class TransitUiState(
    val stops: List<Stop> = emptyList(),
    val filteredStops: List<Stop> = emptyList(),
    val stopSearchQuery: String = "",
    val selectedDistrictFilter: String = "All",
    val selectedBoardingStop: Stop? = null,
    val selectedDestinationStop: Stop? = null,
    val isSearchingBuses: Boolean = false,
    val availableBuses: List<BusItem> = emptyList(),
    val connectingBuses: List<ConnectingTripItem> = emptyList(),
    val selectedBusFilterTab: Int = 0, // 0: Direct Buses, 1: Connecting / Next Route
    val searchErrorMessage: String? = null,
    val selectedBus: BusItem? = null,
    val liveLocation: BusLocationUpdate? = null,
    val activeAlarm: WakeAlert? = null,
    val alarmTriggered: Boolean = false,
    val alertDistanceSettingKm: Double = 0.2, // Default 200m proximity alarm for testing (0.2 km)
    val userLocation: Pair<Double, Double>? = null, // Passenger live GPS location (lat, lng)
    val testAlarmLocation: Pair<Double, Double>? = null, // Custom 200m test target tapped on map
    val testAlarmLabel: String? = null,
    val recentTrips: List<RecentTrip> = emptyList(),
    val currentTab: Int = 0,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val selectedRingtoneId: String = "TNSTC_CHIME",
    val customRingtoneUri: String? = null,
    val customRingtoneName: String? = null,
    val isPlayingPreview: Boolean = false
)


class TransitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository()

    private val _uiState = MutableStateFlow(TransitUiState())
    val uiState: StateFlow<TransitUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var wakeAlertTriggered = false

    val availableRingtones = listOf(
        WakeUpRingtone(
            id = "TNSTC_CHIME",
            nameEn = "TNSTC Transit Chime",
            nameTa = "TNSTC இயல்பு அறிவிப்பு மணி",
            descriptionEn = "Classic state transit announcement chime",
            descriptionTa = "பாரம்பரிய போக்குவரத்து அறிவிப்பு மணி ஒலி",
            toneCode = android.media.ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK
        ),
        WakeUpRingtone(
            id = "TEMPLE_BELL",
            nameEn = "Temple Bell",
            nameTa = "கோவில் மணி ஒலி",
            descriptionEn = "Devotional gentle temple bell ringing",
            descriptionTa = "பக்திமிக்க கோவில் மணி ஒலி",
            toneCode = android.media.ToneGenerator.TONE_PROP_BEEP2
        ),
        WakeUpRingtone(
            id = "RADAR_BEEP",
            nameEn = "Radar Ping",
            nameTa = "ரேடார் ஒலி",
            descriptionEn = "High-priority rhythmic radar pulse",
            descriptionTa = "துல்லியமான ரேடார் எச்சரிக்கை ஒலி",
            toneCode = android.media.ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE
        ),
        WakeUpRingtone(
            id = "GENTLE_MELODY",
            nameEn = "Gentle Awakening",
            nameTa = "மெல்லிய இசை",
            descriptionEn = "Soft melodic tone for peaceful wake up",
            descriptionTa = "அமைதியான மெல்லிய விழிப்பு ஒலி",
            toneCode = android.media.ToneGenerator.TONE_PROP_PROMPT
        ),
        WakeUpRingtone(
            id = "LOUD_SIREN",
            nameEn = "Loud Wake-Up Alarm",
            nameTa = "அவசர விழிப்பு அலாரம்",
            descriptionEn = "Strong emergency siren for deep sleepers",
            descriptionTa = "உரத்த அவசர அலாரம் ஒலி",
            toneCode = android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
        )
    )

    init {
        loadStops()
        seedRecentTrips()
    }

    fun setBottomNavTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    fun setLanguage(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = themeMode) }
    }

    fun setWakeUpRingtone(ringtoneId: String) {
        DestinationAlarmController.stopPreview()
        _uiState.update { it.copy(selectedRingtoneId = ringtoneId, isPlayingPreview = false) }
    }

    fun setCustomLocalRingtone(uriString: String, displayName: String) {
        DestinationAlarmController.stopPreview()
        _uiState.update {
            it.copy(
                customRingtoneUri = uriString,
                customRingtoneName = displayName,
                selectedRingtoneId = "CUSTOM_LOCAL",
                isPlayingPreview = false
            )
        }
    }

    fun clearCustomLocalRingtone() {
        DestinationAlarmController.stopPreview()
        _uiState.update {
            it.copy(
                customRingtoneUri = null,
                customRingtoneName = null,
                selectedRingtoneId = "TNSTC_CHIME",
                isPlayingPreview = false
            )
        }
    }

    fun saveCustomAudioFromUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var displayName = "Custom_Alarm_Song"
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                displayName = it.getString(nameIdx) ?: displayName
                            }
                        }
                    }
                } catch (_: Exception) {}

                val file = java.io.File(context.filesDir, "custom_dest_alarm_audio")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val savedUriString = android.net.Uri.fromFile(file).toString()
                setCustomLocalRingtone(savedUriString, displayName)
            } catch (e: Exception) {
                setCustomLocalRingtone(uri.toString(), "Custom Audio")
            }
        }
    }

    fun playRingtonePreview(ringtoneId: String) {
        viewModelScope.launch {
            if (_uiState.value.isPlayingPreview) {
                DestinationAlarmController.stopPreview()
                _uiState.update { it.copy(isPlayingPreview = false) }
                return@launch
            }

            _uiState.update { it.copy(isPlayingPreview = true) }
            val context = getApplication<Application>()

            if (ringtoneId == "CUSTOM_LOCAL" && _uiState.value.customRingtoneUri != null) {
                DestinationAlarmController.playPreview(context, _uiState.value.customRingtoneUri)
            } else {
                val ringtone = availableRingtones.find { it.id == ringtoneId } ?: availableRingtones.first()
                DestinationAlarmController.playPreview(context, null, ringtone.toneCode)
            }

            delay(4000)
            DestinationAlarmController.stopPreview()
            _uiState.update { it.copy(isPlayingPreview = false) }
        }
    }

    fun stopAudioPreview() {
        DestinationAlarmController.stopPreview()
        _uiState.update { it.copy(isPlayingPreview = false) }
    }

    fun setBusFilterTab(index: Int) {
        _uiState.update { it.copy(selectedBusFilterTab = index) }
    }

    fun loadStops() {
        viewModelScope.launch {
            val stops = repository.getStops()
            if (stops.isNotEmpty()) {
                _uiState.update { state ->
                    val curFrom = state.selectedBoardingStop
                    val curTo = state.selectedDestinationStop

                    val defaultFrom = curFrom ?: stops.find { it.name.contains("Erode", ignoreCase = true) } ?: stops[0]
                    val defaultTo = curTo ?: stops.find { it.name.contains("Perundurai", ignoreCase = true) } ?: stops.getOrNull(1) ?: stops[0]

                    state.copy(
                        stops = stops,
                        filteredStops = stops,
                        selectedBoardingStop = defaultFrom,
                        selectedDestinationStop = defaultTo
                    )
                }
            }
        }
    }

    fun onStopSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                if (state.selectedDistrictFilter == "All") state.stops
                else state.stops.filter { it.district.equals(state.selectedDistrictFilter, ignoreCase = true) }
            } else {
                val q = query.trim().lowercase()
                val dbMatches = state.stops.filter { stop ->
                    (stop.name.lowercase().contains(q) ||
                     stop.nameTa.lowercase().contains(q) ||
                     stop.district.lowercase().contains(q) ||
                     stop.districtTa.lowercase().contains(q) ||
                     stop.id.lowercase().contains(q)) &&
                    (state.selectedDistrictFilter == "All" || stop.district.equals(state.selectedDistrictFilter, ignoreCase = true))
                }
                val catalogMatches = TamilNaduTransitDatabase.allStops.filter { cat ->
                    (cat.name.lowercase().contains(q) ||
                     cat.nameTa.lowercase().contains(q) ||
                     cat.district.lowercase().contains(q) ||
                     cat.districtTa.lowercase().contains(q) ||
                     cat.id.lowercase().contains(q)) &&
                    (state.selectedDistrictFilter == "All" || cat.district.equals(state.selectedDistrictFilter, ignoreCase = true)) &&
                    dbMatches.none { it.id.equals(cat.id, ignoreCase = true) || it.name.equals(cat.name, ignoreCase = true) }
                }
                (dbMatches + catalogMatches).distinctBy { it.id }
            }
            state.copy(stopSearchQuery = query, filteredStops = filtered)
        }
    }

    fun selectCustomPlace(
        placeName: String,
        isBoarding: Boolean,
        context: android.content.Context? = null,
        onResolved: ((Stop) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val stop = repository.resolveOrGeocodeStop(placeName, context)
            _uiState.update { state ->
                val updatedStops = if (state.stops.any { it.id == stop.id }) state.stops else listOf(stop) + state.stops
                if (isBoarding) {
                    state.copy(stops = updatedStops, selectedBoardingStop = stop, searchErrorMessage = null)
                } else {
                    state.copy(stops = updatedStops, selectedDestinationStop = stop, searchErrorMessage = null)
                }
            }
            onResolved?.invoke(stop)
        }
    }

    fun onDistrictFilterChanged(district: String) {
        _uiState.update { state ->
            val query = state.stopSearchQuery
            val filtered = state.stops.filter { stop ->
                (district == "All" || stop.district.equals(district, ignoreCase = true)) &&
                (query.isBlank() || stop.name.contains(query, ignoreCase = true) || stop.nameTa.contains(query, ignoreCase = true))
            }
            state.copy(selectedDistrictFilter = district, filteredStops = filtered)
        }
    }

    fun selectBoardingStop(stop: Stop) {
        _uiState.update { it.copy(selectedBoardingStop = stop) }
    }

    fun selectDestinationStop(stop: Stop) {
        _uiState.update { it.copy(selectedDestinationStop = stop) }
    }

    fun swapStops() {
        _uiState.update { state ->
            val temp = state.selectedBoardingStop
            state.copy(
                selectedBoardingStop = state.selectedDestinationStop,
                selectedDestinationStop = temp
            )
        }
    }

    fun findBuses(onSuccess: () -> Unit = {}) {
        val from = _uiState.value.selectedBoardingStop
        val to = _uiState.value.selectedDestinationStop

        if (from == null || to == null) {
            _uiState.update { it.copy(searchErrorMessage = "Please select both boarding and destination stops.") }
            return
        }

        if (from.id == to.id || from.name.equals(to.name, ignoreCase = true)) {
            _uiState.update { it.copy(searchErrorMessage = "Boarding and destination stops cannot be the same.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingBuses = true, searchErrorMessage = null) }

            addRecentTrip(from.name, to.name, from.id, to.id)

            val directBuses = repository.searchBuses(from.id, to.id, from.name, to.name)
            val connectingTrips = repository.searchConnectingBuses(from.id, to.id, from.name, to.name)

            val initialTab = if (directBuses.isNotEmpty()) 0 else 1

            _uiState.update {
                it.copy(
                    isSearchingBuses = false,
                    availableBuses = directBuses,
                    connectingBuses = connectingTrips,
                    selectedBusFilterTab = initialTab,
                    searchErrorMessage = if (directBuses.isEmpty() && connectingTrips.isEmpty()) "No buses found on this route." else null
                )
            }

            if (directBuses.isNotEmpty() || connectingTrips.isNotEmpty()) {
                onSuccess()
            }
        }
    }

    fun selectBusForTracking(bus: BusItem) {
        _uiState.update { it.copy(selectedBus = bus) }
        startLivePolling(bus.busId)
    }

    private var busSimulationIndex = 0

    private fun startLivePolling(busId: String) {
        pollingJob?.cancel()
        wakeAlertTriggered = false
        busSimulationIndex = 0

        pollingJob = viewModelScope.launch {
            while (isActive) {
                val from = _uiState.value.selectedBoardingStop ?: TamilNaduTransitDatabase.allStops.firstOrNull()
                val dest = _uiState.value.selectedDestinationStop ?: TamilNaduTransitDatabase.allStops.getOrNull(1)

                if (from != null && dest != null) {
                    var waypoints = getRouteRoadWaypoints(from, dest)
                    if (waypoints.size < 5) {
                        val pts = mutableListOf<Pair<Double, Double>>()
                        val totalSteps = 40
                        for (i in 0..totalSteps) {
                            val t = i.toDouble() / totalSteps
                            val lat = from.latitude + t * (dest.latitude - from.latitude)
                            val lng = from.longitude + t * (dest.longitude - from.longitude)
                            pts.add(Pair(lat, lng))
                        }
                        waypoints = pts
                    }

                    // Move bus forward step-by-step along the route points towards destination
                    busSimulationIndex = (busSimulationIndex + 1).coerceAtMost(waypoints.size - 1)
                    val curPoint = waypoints[busSimulationIndex]

                    val remainingDistKm = calculateDistanceKm(
                        curPoint.first, curPoint.second,
                        dest.latitude, dest.longitude
                    )
                    val dynamicEta = maxOf(1, (remainingDistKm / 0.5).toInt())

                    val update = BusLocationUpdate(
                        busId = busId,
                        latitude = curPoint.first,
                        longitude = curPoint.second,
                        etaMinutes = dynamicEta,
                        distanceKm = (round(remainingDistKm * 10) / 10.0),
                        availableSeats = 18,
                        occupiedSeats = 22,
                        seatsFreeingAtNextStop = 6,
                        expectedAvailableSeats = 24,
                        crowdStatus = "LOW",
                        adultTicketPrice = 18,
                        childTicketPrice = 9,
                        lastUpdatedSecondsAgo = 1L
                    )
                    _uiState.update { it.copy(liveLocation = update) }

                    // Check alarm condition ONLY for the MOVING BUS entering the destination/target radius
                    val alarm = _uiState.value.activeAlarm
                    if (alarm != null && alarm.active && !wakeAlertTriggered) {
                        val customTarget = _uiState.value.testAlarmLocation
                        val targetLat = customTarget?.first ?: dest.latitude
                        val targetLng = customTarget?.second ?: dest.longitude
                        val targetName = if (customTarget != null) {
                            _uiState.value.testAlarmLabel ?: "Target (${(alarm.alertDistanceKm * 1000).toInt()}m)"
                        } else {
                            dest.name
                        }

                        val distToTargetKm = calculateDistanceKm(
                            curPoint.first, curPoint.second,
                            targetLat, targetLng
                        )

                        // Trigger ONLY when the moving bus reaches within target radius (e.g. 200m = 0.2 km)
                        if (distToTargetKm <= alarm.alertDistanceKm) {
                            wakeAlertTriggered = true
                            triggerDestinationAlarm(targetName, distToTargetKm)
                        }
                    }
                }

                delay(1500) // Smooth 1.5s step progression
            }
        }
    }

    fun setCustomMapTestAlarm(lat: Double, lng: Double, radiusKm: Double = 0.2) {
        val bus = _uiState.value.selectedBus
        val busId = bus?.busId ?: "BUS_TRACK"
        val radiusMeters = (radiusKm * 1000).toInt()

        val alert = WakeAlert(
            alertId = "MAP_TEST_" + System.currentTimeMillis(),
            busId = busId,
            destinationStopId = "CUSTOM_MAP_TEST",
            destinationStopName = "Tapped Target (${if (radiusKm < 1.0) "${radiusMeters}m" else "${radiusKm}km"})",
            alertDistanceKm = radiusKm,
            active = true
        )

        wakeAlertTriggered = false
        _uiState.update {
            it.copy(
                activeAlarm = alert,
                alertDistanceSettingKm = radiusKm,
                testAlarmLocation = Pair(lat, lng),
                testAlarmLabel = "Tapped Target (${if (radiusKm < 1.0) "${radiusMeters}m" else "${radiusKm}km"})",
                alarmTriggered = false
            )
        }
        // NOTE: Will trigger ONLY when the moving bus enters this radius, not on initial tap!
    }

    fun updateTestAlarmRadius(radiusKm: Double) {
        val curLoc = _uiState.value.testAlarmLocation
        if (curLoc != null) {
            setCustomMapTestAlarm(curLoc.first, curLoc.second, radiusKm)
        } else {
            val dest = _uiState.value.selectedDestinationStop
            if (dest != null) {
                setWakeMeUpAlert(radiusKm)
            }
        }
    }

    fun clearCustomMapTestAlarm() {
        _uiState.update {
            it.copy(
                testAlarmLocation = null,
                testAlarmLabel = null
            )
        }
    }

    private var lastAccurateLocation: android.location.Location? = null

    fun updateFilteredUserLocation(loc: android.location.Location) {
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return

        val prev = lastAccurateLocation
        if (prev != null) {
            val dist = loc.distanceTo(prev)
            // If movement is under 3 meters, ignore to prevent micro-jitter when stationary
            if (dist < 3.0f) {
                return
            }
            val timeDiff = loc.time - prev.time
            // If the new fix is significantly less accurate and recent, ignore the jump
            if (loc.hasAccuracy() && prev.hasAccuracy() && loc.accuracy > prev.accuracy + 25f && timeDiff < 20_000L) {
                return
            }
        }

        // Filter out wild jumps (> 120m accuracy error) if we already have a fix
        if (loc.hasAccuracy() && loc.accuracy > 120f && prev != null) {
            return
        }

        lastAccurateLocation = loc
        _uiState.update { it.copy(userLocation = Pair(loc.latitude, loc.longitude)) }
    }

    fun updateUserLocation(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        _uiState.update { it.copy(userLocation = Pair(lat, lng)) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun startUserLocationUpdates() {
        val context = getApplication<Application>()
        var fusedStarted = false

        // 1. Primary: Google Play Services FusedLocationProviderClient (Smooth, filtered, high-accuracy)
        try {
            val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    updateFilteredUserLocation(loc)
                }
            }

            val req = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                2500L
            ).setMinUpdateDistanceMeters(3f) // 3m minimum change prevents stationary jitter
            .setMinUpdateIntervalMillis(1500L)
            .build()

            fusedClient.requestLocationUpdates(
                req,
                object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        val last = result.lastLocation ?: return
                        updateFilteredUserLocation(last)
                    }
                },
                android.os.Looper.getMainLooper()
            )
            fusedStarted = true
        } catch (_: Exception) {}

        // 2. Hardware GPS satellite backup ONLY if FusedClient is unavailable
        if (!fusedStarted) {
            try {
                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (lm != null && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    val gpsLoc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    if (gpsLoc != null) {
                        updateFilteredUserLocation(gpsLoc)
                    }

                    val gpsListener = object : android.location.LocationListener {
                        override fun onLocationChanged(loc: android.location.Location) {
                            updateFilteredUserLocation(loc)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    lm.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        3000L,
                        3f,
                        gpsListener,
                        android.os.Looper.getMainLooper()
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun setWakeMeUpAlert(distanceKm: Double) {
        val dest = _uiState.value.selectedDestinationStop ?: return
        val bus = _uiState.value.selectedBus ?: return

        val alert = WakeAlert(
            alertId = "WA_" + System.currentTimeMillis(),
            busId = bus.busId,
            destinationStopId = dest.id,
            destinationStopName = dest.name,
            alertDistanceKm = distanceKm,
            active = true
        )

        wakeAlertTriggered = false
        _uiState.update {
            it.copy(
                activeAlarm = alert,
                alertDistanceSettingKm = distanceKm,
                alarmTriggered = false
            )
        }
    }

    fun triggerDestinationAlarm(destName: String, distKm: Double) {
        val context = getApplication<Application>()
        val ringtoneId = _uiState.value.selectedRingtoneId
        val tone = availableRingtones.find { it.id == ringtoneId }
        val toneCode = tone?.toneCode ?: android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK

        val customUri = if (ringtoneId == "CUSTOM_LOCAL") _uiState.value.customRingtoneUri else null

        DestinationAlarmController.startAlarm(context, toneCode, customUri)
        val meters = (distKm * 1000).toInt().coerceAtLeast(50)
        DestinationAlarmController.showArrivalNotification(context, destName, meters)

        _uiState.update { it.copy(alarmTriggered = true) }
    }

    fun cancelWakeAlert() {
        val context = getApplication<Application>()
        DestinationAlarmController.stopAlarm(context)
        _uiState.update { it.copy(activeAlarm = null, alarmTriggered = false) }
    }

    fun dismissAlarmTriggeredDialog() {
        val context = getApplication<Application>()
        DestinationAlarmController.stopAlarm(context)
        _uiState.update { it.copy(alarmTriggered = false, activeAlarm = null) }
    }

    fun testTriggerWakeAlarm() {
        val destName = _uiState.value.selectedDestinationStop?.name ?: _uiState.value.activeAlarm?.destinationStopName ?: "Destination Stop"
        wakeAlertTriggered = true
        triggerDestinationAlarm(destName, 0.2) // Immediate 200m test trigger
    }

    fun simulateBusAt200Meters() {
        val dest = _uiState.value.selectedDestinationStop ?: TamilNaduTransitDatabase.allStops.firstOrNull()
        val busId = _uiState.value.selectedBus?.busId ?: "BUS_TRACK"

        // Place simulated bus ~140 meters away from target or destination stop (strictly inside 200m radius)
        val customTarget = _uiState.value.testAlarmLocation
        val targetLat = customTarget?.first ?: dest?.latitude ?: 11.3410
        val targetLng = customTarget?.second ?: dest?.longitude ?: 77.7170
        val testLat = targetLat - 0.0011
        val testLng = targetLng

        val testLoc = BusLocationUpdate(
            busId = busId,
            latitude = testLat,
            longitude = testLng,
            etaMinutes = 1,
            distanceKm = 0.14,
            availableSeats = 18,
            occupiedSeats = 22,
            seatsFreeingAtNextStop = 6,
            expectedAvailableSeats = 24,
            crowdStatus = "LOW",
            adultTicketPrice = 18,
            childTicketPrice = 9,
            lastUpdatedSecondsAgo = 1L
        )
        _uiState.update { it.copy(liveLocation = testLoc) }

        val targetName = if (customTarget != null) {
            _uiState.value.testAlarmLabel ?: "Tapped Target (200m)"
        } else {
            dest?.name ?: "Destination (200m)"
        }
        wakeAlertTriggered = true
        triggerDestinationAlarm(targetName, 0.14)
    }

    private fun addRecentTrip(fromName: String, toName: String, fromId: String, toId: String) {
        val newTrip = RecentTrip(
            id = "TRIP_" + System.currentTimeMillis(),
            fromStopName = fromName,
            toStopName = toName,
            fromStopId = fromId,
            toStopId = toId,
            dateText = "Today"
        )
        _uiState.update { state ->
            val updated = listOf(newTrip) + state.recentTrips.filterNot { it.fromStopId == fromId && it.toStopId == toId }
            state.copy(recentTrips = updated.take(10))
        }
    }

    private fun seedRecentTrips() {
        val trips = listOf(
            RecentTrip("1", "Erode Central Bus Stand", "Perundurai New Bus Stand", "ERD_CBS", "ERD_PERUNDURAI", "Today"),
            RecentTrip("2", "Erode Central Bus Stand", "Thindal Murugan Temple", "ERD_CBS", "ERD_THINDAL", "Yesterday"),
            RecentTrip("3", "Chennai Kilambakkam (KCBT)", "Madurai Mattuthavani (MIBT)", "CHN_KCBT", "MDU_MATTUTHAVANI", "2 days ago"),
            RecentTrip("4", "Coimbatore Gandhipuram Central BS", "Salem New Bus Stand (MGR BS)", "CBE_GANDHIPURAM", "SLM_NEW_BS", "3 days ago")
        )
        _uiState.update { it.copy(recentTrips = trips) }
    }

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 || lat2 == 0.0) return 1.5
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun getRouteRoadWaypoints(from: Stop?, to: Stop?): List<Pair<Double, Double>> {
        return if (from != null && to != null) {
            repository.getRoadPolylineBetweenStops(from, to)
        } else {
            emptyList()
        }
    }

    suspend fun fetchRealRoadPolyline(from: Stop, to: Stop, intermediateStops: List<Stop>): List<Pair<Double, Double>> {
        val resolvedFrom = if (from.latitude == 0.0 || from.longitude == 0.0) repository.resolveOrGeocodeStop(from.name) else from
        val resolvedTo = if (to.latitude == 0.0 || to.longitude == 0.0) repository.resolveOrGeocodeStop(to.name) else to
        return repository.getRealRoadPolyline(resolvedFrom, resolvedTo, intermediateStops)
    }

    suspend fun fetchRouteIntermediateStops(from: Stop, to: Stop): List<Stop> {
        try {
            val dbIntermediates = repository.getIntermediateStopsFromRoute(from.id, to.id, from.name, to.name)
            if (!dbIntermediates.isNullOrEmpty()) {
                val sanitized = dbIntermediates.map { s ->
                    val coords = FirebaseTransitService.sanitizeCoordinates(s.name, s.id, s.latitude, s.longitude)
                    s.copy(latitude = coords.first, longitude = coords.second)
                }
                return sanitized
            }
        } catch (_: Exception) {}

        return getIntermediateStops(from, to)
    }

    fun getIntermediateStops(from: Stop?, to: Stop?): List<Stop> {
        if (from == null || to == null) return emptyList()
        val stops = TamilNaduTransitDatabase.findIntermediateStops(from.id, to.id)
        return stops.map { s ->
            val coords = FirebaseTransitService.sanitizeCoordinates(s.name, s.id, s.latitude, s.longitude)
            s.copy(latitude = coords.first, longitude = coords.second)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        try {
            DestinationAlarmController.stopPreview()
            DestinationAlarmController.stopAlarm(getApplication())
        } catch (_: Exception) {}
    }
}
