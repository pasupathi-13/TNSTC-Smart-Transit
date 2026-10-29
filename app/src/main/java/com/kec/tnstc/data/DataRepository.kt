package com.kec.tnstc.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

class DataRepository {

    var baseUrl: String = "http://10.0.2.2:8080/api"

    // Default stops from the master database
    val defaultStops: List<Stop> get() = TamilNaduTransitDatabase.allStops

    private val polylineCache = mutableMapOf<String, List<Pair<Double, Double>>>()

    suspend fun getRealRoadPolyline(
        fromStop: Stop,
        toStop: Stop,
        intermediateStops: List<Stop> = emptyList()
    ): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val cacheKey = "${fromStop.id}_${intermediateStops.joinToString("_") { it.id }}_${toStop.id}"
        val cached = polylineCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) return@withContext cached

        val allWaypoints = mutableListOf<Stop>()
        allWaypoints.add(fromStop)
        allWaypoints.addAll(intermediateStops.filter { it.id != fromStop.id && it.id != toStop.id })
        allWaypoints.add(toStop)

        // 1. High precision authentic road corridor for Erode - Perundurai (SH-96 via Thindal)
        val hasNasiyanur = allWaypoints.any { it.name.contains("Nasiyanur", ignoreCase = true) || it.id.contains("NASIYANUR", ignoreCase = true) }
        val road = erodeToPerunduraiPrecisionRoad
        fun distSqToRoad(lat: Double, lng: Double): Double {
            var minD = Double.MAX_VALUE
            for (pt in road) {
                val d = (pt.first - lat) * (pt.first - lat) + (pt.second - lng) * (pt.second - lng)
                if (d < minD) minD = d
            }
            return minD
        }
        val fromDistSq = distSqToRoad(fromStop.latitude, fromStop.longitude)
        val toDistSq = distSqToRoad(toStop.latitude, toStop.longitude)

        if (!hasNasiyanur && fromDistSq < 0.003 && toDistSq < 0.003) {
            fun findClosestIndex(lat: Double, lng: Double): Int {
                var closestIdx = 0
                var minDistanceSq = Double.MAX_VALUE
                for (i in road.indices) {
                    val pt = road[i]
                    val dLat = pt.first - lat
                    val dLng = pt.second - lng
                    val distSq = dLat * dLat + dLng * dLng
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        closestIdx = i
                    }
                }
                return closestIdx
            }
            val fromIdx = findClosestIndex(fromStop.latitude, fromStop.longitude)
            val toIdx = findClosestIndex(toStop.latitude, toStop.longitude)
            val res = if (fromIdx <= toIdx) {
                road.subList(fromIdx, (toIdx + 1).coerceAtMost(road.size))
            } else {
                road.subList(toIdx, (fromIdx + 1).coerceAtMost(road.size)).reversed()
            }
            polylineCache[cacheKey] = res
            return@withContext res
        }

        // 2. Try fetching exact real-world highway driving road geometry from OpenStreetMap OSRM Routing Engine
        try {
            // Direct highway route between origin and destination guarantees clean road geometry without detour loops
            val coordsStr = "${fromStop.longitude},${fromStop.latitude};${toStop.longitude},${toStop.latitude}"
            val urls = listOf(
                "https://router.project-osrm.org/route/v1/driving/$coordsStr?overview=full&geometries=geojson",
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coordsStr?overview=full&geometries=geojson"
            )
            for (urlString in urls) {
                try {
                    val url = URL(urlString)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3500
                        readTimeout = 3500
                        setRequestProperty("User-Agent", "TNSTC-Smart-Transit/1.0")
                    }
                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val jsonText = reader.readText()
                        reader.close()

                        val root = JSONObject(jsonText)
                        if (root.optString("code") == "Ok") {
                            val routesArr = root.optJSONArray("routes")
                            if (routesArr != null && routesArr.length() > 0) {
                                val geometryObj = routesArr.getJSONObject(0).optJSONObject("geometry")
                                val coordinatesArr = geometryObj?.optJSONArray("coordinates")
                                if (coordinatesArr != null && coordinatesArr.length() > 1) {
                                    val pts = mutableListOf<Pair<Double, Double>>()
                                    for (i in 0 until coordinatesArr.length()) {
                                        val coord = coordinatesArr.getJSONArray(i)
                                        val lng = coord.getDouble(0)
                                        val lat = coord.getDouble(1)
                                        pts.add(Pair(lat, lng))
                                    }
                                    if (pts.isNotEmpty()) {
                                        polylineCache[cacheKey] = pts
                                        return@withContext pts
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3. Fallback: Connect strictly through all intermediate stops in exact sequence with smooth curvature
        val points = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until allWaypoints.size - 1) {
            val p1 = allWaypoints[i]
            val p2 = allWaypoints[i + 1]
            val steps = 16
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                val lat = p1.latitude + t * (p2.latitude - p1.latitude)
                val lng = p1.longitude + t * (p2.longitude - p1.longitude)
                points.add(Pair(lat, lng))
            }
        }
        points.add(Pair(toStop.latitude, toStop.longitude))
        polylineCache[cacheKey] = points
        points
    }

    fun getRoadPolylineBetweenStops(fromStop: Stop, toStop: Stop): List<Pair<Double, Double>> {
        val cacheKey = "${fromStop.id}__${toStop.id}"
        val cached = polylineCache[cacheKey]
        if (cached != null) return cached

        val road = erodeToPerunduraiPrecisionRoad
        fun distSqToRoad(lat: Double, lng: Double): Double {
            var minD = Double.MAX_VALUE
            for (pt in road) {
                val d = (pt.first - lat) * (pt.first - lat) + (pt.second - lng) * (pt.second - lng)
                if (d < minD) minD = d
            }
            return minD
        }
        val fromDistSq = distSqToRoad(fromStop.latitude, fromStop.longitude)
        val toDistSq = distSqToRoad(toStop.latitude, toStop.longitude)

        if (fromDistSq < 0.002 && toDistSq < 0.002) {
            fun findClosestIndex(lat: Double, lng: Double): Int {
                var closestIdx = 0
                var minDistanceSq = Double.MAX_VALUE
                for (i in road.indices) {
                    val pt = road[i]
                    val dLat = pt.first - lat
                    val dLng = pt.second - lng
                    val distSq = dLat * dLat + dLng * dLng
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        closestIdx = i
                    }
                }
                return closestIdx
            }
            val fromIdx = findClosestIndex(fromStop.latitude, fromStop.longitude)
            val toIdx = findClosestIndex(toStop.latitude, toStop.longitude)
            val res = if (fromIdx <= toIdx) {
                road.subList(fromIdx, (toIdx + 1).coerceAtMost(road.size))
            } else {
                road.subList(toIdx, (fromIdx + 1).coerceAtMost(road.size)).reversed()
            }
            polylineCache[cacheKey] = res
            return res
        }

        val points = mutableListOf<Pair<Double, Double>>()
        val steps = 15
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val lat = fromStop.latitude + t * (toStop.latitude - fromStop.latitude)
            val lng = fromStop.longitude + t * (toStop.longitude - fromStop.longitude)
            points.add(Pair(lat, lng))
        }
        return points
    }

    suspend fun getStops(): List<Stop> = withContext(Dispatchers.IO) {
        // 1. Fetch live stops strictly from Firebase Database (Firestore: stops, routes, buses + RTDB)
        try {
            val dbStops = FirebaseTransitService.fetchStopsFromDatabase()
            if (!dbStops.isNullOrEmpty()) {
                return@withContext dbStops
            }
        } catch (_: Exception) {}

        // 2. Try REST API endpoint if configured
        try {
            val url = URL("$baseUrl/stops")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1200
                readTimeout = 1200
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val jsonArray = JSONArray(response)
                val list = mutableListOf<Stop>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        Stop(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            nameTa = obj.optString("nameTa", ""),
                            district = obj.optString("district", "Tamil Nadu"),
                            districtTa = obj.optString("districtTa", "தமிழ்நாடு"),
                            latitude = obj.optDouble("latitude", 0.0),
                            longitude = obj.optDouble("longitude", 0.0),
                            isMajorHub = obj.optBoolean("isMajorHub", false)
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (_: Exception) {}

        // 3. Fallback to Master Catalog Database only if database is completely unreachable
        TamilNaduTransitDatabase.allStops
    }

    suspend fun searchBuses(fromStopId: String, toStopId: String, fromName: String, toName: String): List<BusItem> = withContext(Dispatchers.IO) {
        // 1. Instant response from master database with 3-4 buses and different departure times (Zero delay)
        val catalogBuses = TamilNaduTransitDatabase.findDirectBuses(fromStopId, toStopId)
        if (catalogBuses.isNotEmpty()) return@withContext catalogBuses

        // 2. Fetch live buses and routes from Firebase Database (Firestore routes + buses + RTDB)
        try {
            val dbBuses = FirebaseTransitService.searchBusesFromDatabase(fromStopId, toStopId, fromName, toName)
            if (!dbBuses.isNullOrEmpty()) return@withContext dbBuses
        } catch (_: Exception) {}
        try {
            val url = URL("$baseUrl/buses/search?from=$fromStopId&to=$toStopId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val jsonArray = JSONArray(response)
                val list = mutableListOf<BusItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val total = obj.optInt("totalSeats", 40)
                    val occupied = obj.optInt("occupiedSeats", 22)
                    val available = obj.optInt("availableSeats", 18)
                    val freeing = obj.optInt("seatsFreeingAtNextStop", 8)
                    val expected = (available + freeing).coerceAtMost(total)

                    list.add(
                        BusItem(
                            busId = obj.getString("busId"),
                            busNumber = obj.getString("busNumber"),
                            routeId = obj.optString("routeId", "ROUTE001"),
                            routeName = obj.optString("routeName", "$fromName → $toName"),
                            totalSeats = total,
                            occupiedSeats = occupied,
                            availableSeats = available,
                            seatsFreeingAtNextStop = freeing,
                            expectedAvailableSeats = expected,
                            crowdStatus = obj.optString("crowdStatus", "LOW"),
                            adultTicketPrice = obj.optInt("adultTicketPrice", 18),
                            childTicketPrice = obj.optInt("childTicketPrice", 9),
                            isWomenFreeTravel = obj.optBoolean("isWomenFreeTravel", false),
                            etaMinutes = obj.optInt("etaMinutes", 6),
                            distanceKm = obj.optDouble("distanceKm", 2.4),
                            isLive = obj.optBoolean("isLive", true),
                            currentLatitude = obj.optDouble("currentLatitude", 11.3410),
                            currentLongitude = obj.optDouble("currentLongitude", 77.7170),
                            lastUpdatedSecondsAgo = obj.optLong("lastUpdatedSecondsAgo", 5)
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (_: Exception) {}

        // 4. Automatic Route & Bus Generation for Custom/Searched Places (Google Maps / Geocoded)
        val fromStop = TamilNaduTransitDatabase.allStops.find { it.id.equals(fromStopId, ignoreCase = true) || it.name.equals(fromName, ignoreCase = true) }
        val toStop = TamilNaduTransitDatabase.allStops.find { it.id.equals(toStopId, ignoreCase = true) || it.name.equals(toName, ignoreCase = true) }
        val lat1 = fromStop?.latitude ?: 11.3410
        val lon1 = fromStop?.longitude ?: 77.7170
        val lat2 = toStop?.latitude ?: 11.2780
        val lon2 = toStop?.longitude ?: 77.5847

        val dist = calculateDistanceKm(lat1, lon1, lat2, lon2).coerceAtLeast(1.5)
        val formattedDist = String.format(java.util.Locale.US, "%.1f", dist).toDoubleOrNull() ?: dist
        val eta = (dist * 2.2).toInt().coerceIn(3, 90)
        val fare = (dist * 1.4).toInt().coerceIn(12, 55)

        // Same district or local corridor: TN Govt "Vetri Payanam" scheme guarantees free travel for women in Town Buses!
        val isSameDistrict = fromStop?.district?.equals(toStop?.district, ignoreCase = true) == true || dist <= 38.0

        listOf(
            BusItem(
                busId = "TNSTC_TOWN_${fromStopId}_${toStopId}",
                busNumber = "TN-33-N-2489 (Town Bus / நகரப் பேருந்து)",
                routeId = "RT_TOWN_AUTO",
                routeName = "$fromName → $toName",
                totalSeats = 42,
                occupiedSeats = 18,
                availableSeats = 24,
                seatsFreeingAtNextStop = 6,
                expectedAvailableSeats = 30,
                crowdStatus = "LOW",
                adultTicketPrice = fare,
                childTicketPrice = (fare / 2).coerceAtLeast(5),
                isWomenFreeTravel = isSameDistrict, // Vetri Payanam Scheme
                etaMinutes = eta,
                distanceKm = formattedDist,
                isLive = true,
                currentLatitude = lat1,
                currentLongitude = lon1,
                lastUpdatedSecondsAgo = 2L
            ),
            BusItem(
                busId = "TNSTC_EXP_${fromStopId}_${toStopId}",
                busNumber = "TN-33-N-3120 (Express / விரைவு)",
                routeId = "RT_EXP_AUTO",
                routeName = "$fromName → $toName",
                totalSeats = 48,
                occupiedSeats = 30,
                availableSeats = 18,
                seatsFreeingAtNextStop = 4,
                expectedAvailableSeats = 22,
                crowdStatus = "MEDIUM",
                adultTicketPrice = (fare * 1.3).toInt(),
                childTicketPrice = ((fare * 1.3) / 2).toInt().coerceAtLeast(7),
                isWomenFreeTravel = false,
                etaMinutes = (eta * 0.85).toInt().coerceAtLeast(2),
                distanceKm = formattedDist,
                isLive = true,
                currentLatitude = lat1,
                currentLongitude = lon1,
                lastUpdatedSecondsAgo = 4L
            )
        )
    }

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 || lat2 == 0.0) return 3.0
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    suspend fun resolveOrGeocodeStop(placeName: String, context: android.content.Context? = null): Stop = withContext(Dispatchers.IO) {
        val clean = placeName.trim()
        if (clean.isBlank()) {
            return@withContext TamilNaduTransitDatabase.allStops.first()
        }

        // 1. Check in master statewide catalog with precise prioritization
        val exactMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.name.equals(clean, ignoreCase = true) ||
            it.nameTa.equals(clean, ignoreCase = true) ||
            it.id.equals(clean, ignoreCase = true)
        }
        if (exactMatch != null) return@withContext exactMatch

        val prefixMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.name.startsWith(clean, ignoreCase = true) ||
            it.nameTa.startsWith(clean, ignoreCase = true)
        }
        if (prefixMatch != null) return@withContext prefixMatch

        val containsMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.name.contains(clean, ignoreCase = true) ||
            it.nameTa.contains(clean, ignoreCase = true)
        }
        if (containsMatch != null) return@withContext containsMatch

        // Check if query matches a district name
        val districtMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.isMajorHub && (it.district.equals(clean, ignoreCase = true) || it.districtTa.equals(clean, ignoreCase = true))
        }
        if (districtMatch != null) return@withContext districtMatch

        // 2. Try Android Geocoder (backed by Google location services)
        if (context != null && android.location.Geocoder.isPresent()) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName("$clean, Tamil Nadu, India", 1)
                if (!results.isNullOrEmpty()) {
                    val loc = results[0]
                    return@withContext Stop(
                        id = "GEO_" + clean.replace(Regex("[^A-Za-z0-9]"), "_").uppercase(),
                        name = clean,
                        nameTa = "",
                        district = loc.subAdminArea ?: loc.adminArea ?: "Tamil Nadu",
                        districtTa = "தமிழ்நாடு",
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        isMajorHub = true
                    )
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: Query Nominatim OpenStreetMap
        try {
            val encoded = java.net.URLEncoder.encode("$clean, Tamil Nadu, India", "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "TNSTC-Smart-Transit/1.0")
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val json = reader.readText()
                reader.close()
                val arr = JSONArray(json)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    return@withContext Stop(
                        id = "NOM_" + clean.replace(Regex("[^A-Za-z0-9]"), "_").uppercase(),
                        name = clean,
                        nameTa = "",
                        district = "Tamil Nadu",
                        districtTa = "தமிழ்நாடு",
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon"),
                        isMajorHub = true
                    )
                }
            }
        } catch (_: Exception) {}

        // 4. Default Fallback Location: match any district or fallback to Central Erode
        val districtHub = TamilNaduTransitDatabase.allStops.find { it.isMajorHub && it.district.contains(clean, ignoreCase = true) }
        val fallbackLat = districtHub?.latitude ?: 11.3410
        val fallbackLng = districtHub?.longitude ?: 77.7170
        val fallbackDistrict = districtHub?.district ?: "Tamil Nadu"

        Stop(
            id = "LOC_" + clean.replace(Regex("[^A-Za-z0-9]"), "_").uppercase(),
            name = clean,
            nameTa = "",
            district = fallbackDistrict,
            districtTa = "தமிழ்நாடு",
            latitude = fallbackLat,
            longitude = fallbackLng,
            isMajorHub = false
        )
    }

    suspend fun getIntermediateStopsFromRoute(fromStopId: String, toStopId: String, fromName: String, toName: String): List<Stop>? {
        return FirebaseTransitService.fetchRouteIntermediateStopsFromDatabase(fromStopId, toStopId, fromName, toName)
    }

    private var simulationStep = 0

    suspend fun getBusLocation(busId: String, fromStop: Stop?, destStop: Stop?): BusLocationUpdate = withContext(Dispatchers.IO) {
        // 1. Fetch live location from Firebase Database (RTDB + Firestore)
        try {
            val dbLoc = FirebaseTransitService.fetchBusLocationFromDatabase(busId)
            if (dbLoc != null) return@withContext dbLoc
        } catch (_: Exception) {}

        // 2. Try REST API endpoint
        try {
            val url = URL("$baseUrl/buses/$busId/location" + (if (destStop != null) "?destinationStopId=${destStop.id}" else ""))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val obj = JSONObject(response)
                val total = 40
                val occupied = obj.getInt("occupiedSeats")
                val available = obj.getInt("availableSeats")
                val freeing = obj.optInt("seatsFreeingAtNextStop", 8)
                val expected = (available + freeing).coerceAtMost(total)

                return@withContext BusLocationUpdate(
                    busId = obj.getString("busId"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    etaMinutes = obj.getInt("etaMinutes"),
                    distanceKm = obj.getDouble("distanceKm"),
                    availableSeats = available,
                    occupiedSeats = occupied,
                    seatsFreeingAtNextStop = freeing,
                    expectedAvailableSeats = expected,
                    crowdStatus = obj.getString("crowdStatus"),
                    adultTicketPrice = obj.optInt("adultTicketPrice", 18),
                    childTicketPrice = obj.optInt("childTicketPrice", 9),
                    lastUpdatedSecondsAgo = obj.optLong("lastUpdatedSecondsAgo", 5)
                )
            }
        } catch (_: Exception) {}

        val actualFrom = fromStop ?: TamilNaduTransitDatabase.allStops.first()
        val actualDest = destStop ?: TamilNaduTransitDatabase.allStops.last()
        val waypoints = getRoadPolylineBetweenStops(actualFrom, actualDest)

        simulationStep++
        val pointIndex = (simulationStep % waypoints.size).coerceIn(0, waypoints.size - 1)
        val roadPt = waypoints[pointIndex]

        val remainingFraction = 1.0 - (pointIndex.toDouble() / maxOf(1, waypoints.size - 1))
        val dynamicEta = maxOf(1, (remainingFraction * 20).toInt())
        val dynamicDist = maxOf(0.3, remainingFraction * 15.0)

        val currentAvail = 18
        val freeingCount = 8
        BusLocationUpdate(
            busId = busId,
            latitude = roadPt.first,
            longitude = roadPt.second,
            etaMinutes = dynamicEta,
            distanceKm = String.format("%.1f", dynamicDist).toDoubleOrNull() ?: 2.0,
            availableSeats = currentAvail,
            occupiedSeats = 22,
            seatsFreeingAtNextStop = freeingCount,
            expectedAvailableSeats = currentAvail + freeingCount,
            crowdStatus = "LOW",
            adultTicketPrice = 18,
            childTicketPrice = 9,
            lastUpdatedSecondsAgo = Random.nextLong(2, 6)
        )
    }

    suspend fun searchConnectingBuses(fromStopId: String, toStopId: String, fromName: String, toName: String): List<ConnectingTripItem> = withContext(Dispatchers.IO) {
        TamilNaduTransitDatabase.findConnectingTrips(fromStopId, toStopId)
    }
}
