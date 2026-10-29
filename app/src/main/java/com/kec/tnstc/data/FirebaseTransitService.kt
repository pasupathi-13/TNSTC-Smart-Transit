package com.kec.tnstc.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live Firebase Service for TNSTC Smart Transit
 * Synchronizes with both Firebase Realtime Database (RTDB) and Cloud Firestore
 * populated by the admin portal.
 */
object FirebaseTransitService {

    private const val TAG = "FirebaseTransitService"
    private const val RTDB_URL = "https://tnstc-smart-transit-default-rtdb.firebaseio.com"

    private val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }

    /**
     * Fetch all stops directly from the Firebase Database (RTDB + Firestore)
     * including stops collection, route intermediate stops, and bus routes.
     */
    suspend fun fetchStopsFromDatabase(): List<Stop>? = withContext(Dispatchers.IO) {
        val stopMap = LinkedHashMap<String, Stop>()

        // 1. Cloud Firestore: stops collection
        try {
            val db = firestore
            if (db != null) {
                val snapshot = db.collection("stops").get().await()
                for (doc in snapshot.documents) {
                    val stop = parseFirestoreStop(doc)
                    if (stop != null) {
                        stopMap[stop.id] = stop
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firestore stops collection: ${e.message}")
        }

        // 2. Cloud Firestore: routes collection (extract all intermediate stops from routes)
        try {
            val db = firestore
            if (db != null) {
                val snapshot = db.collection("routes").get().await()
                for (doc in snapshot.documents) {
                    extractStopsFromFirestoreDoc(doc, stopMap)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firestore routes collection: ${e.message}")
        }

        // 3. Cloud Firestore: buses collection (extract stops from bus routes)
        try {
            val db = firestore
            if (db != null) {
                val snapshot = db.collection("buses").get().await()
                for (doc in snapshot.documents) {
                    extractStopsFromFirestoreDoc(doc, stopMap)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firestore buses collection: ${e.message}")
        }

        // 4. Firebase Realtime Database: /stops.json
        try {
            val jsonText = fetchHttpText("$RTDB_URL/stops.json")
            if (!jsonText.isNullOrBlank() && jsonText != "null") {
                val list = parseJsonStops(jsonText)
                for (s in list) {
                    stopMap[s.id] = s
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "RTDB /stops.json error: ${e.message}")
        }

        // 5. Firebase Realtime Database: /routes.json
        try {
            val jsonText = fetchHttpText("$RTDB_URL/routes.json")
            if (!jsonText.isNullOrBlank() && jsonText != "null") {
                extractStopsFromJsonString(jsonText, stopMap)
            }
        } catch (e: Exception) {
            Log.d(TAG, "RTDB /routes.json error: ${e.message}")
        }

        // 6. Firebase Realtime Database: /buses.json
        try {
            val jsonText = fetchHttpText("$RTDB_URL/buses.json")
            if (!jsonText.isNullOrBlank() && jsonText != "null") {
                extractStopsFromJsonString(jsonText, stopMap)
            }
        } catch (e: Exception) {
            Log.d(TAG, "RTDB /buses.json error: ${e.message}")
        }

        if (stopMap.isNotEmpty()) {
            Log.d(TAG, "Loaded ${stopMap.size} stops from Firebase Database")
            stopMap.values.toList()
        } else {
            null
        }
    }

    /**
     * Search live buses from the Firebase Database (Firestore routes/buses + RTDB).
     */
    suspend fun searchBusesFromDatabase(
        fromStopId: String,
        toStopId: String,
        fromName: String,
        toName: String
    ): List<BusItem>? = withContext(Dispatchers.IO) {
        val foundBuses = mutableListOf<BusItem>()

        // 1. Search in Cloud Firestore `routes` collection (e.g., ROUTE-101)
        try {
            val db = firestore
            if (db != null) {
                val routesSnapshot = db.collection("routes").get().await()
                for (doc in routesSnapshot.documents) {
                    val routeStops = extractOrderedStopsFromDoc(doc)
                    if (routeStops.isNotEmpty()) {
                        val fromIdx = routeStops.indexOfFirst {
                            it.id.equals(fromStopId, ignoreCase = true) || it.name.equals(fromName, ignoreCase = true)
                        }
                        val toIdx = routeStops.indexOfFirst {
                            it.id.equals(toStopId, ignoreCase = true) || it.name.equals(toName, ignoreCase = true)
                        }

                        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                            val isForward = fromIdx < toIdx
                            val routeNumber = doc.getString("routeNumber") ?: doc.getString("name") ?: doc.id
                            val busNumber = doc.getString("busNumber") ?: "TN-33-${doc.id}"
                            val busType = doc.getString("busType") ?: doc.getString("type") ?: "Town Bus / Express"
                            val fare = doc.getLong("fare")?.toInt() ?: doc.getLong("adultPrice")?.toInt() ?: 16

                            foundBuses.add(
                                BusItem(
                                    busId = "DB_${doc.id}_${if (isForward) "FWD" else "REV"}",
                                    busNumber = "$busNumber ($busType)",
                                    routeId = doc.id,
                                    routeName = "$fromName → $toName (${doc.id})",
                                    totalSeats = doc.getLong("totalSeats")?.toInt() ?: 40,
                                    occupiedSeats = 18,
                                    availableSeats = 22,
                                    seatsFreeingAtNextStop = 6,
                                    expectedAvailableSeats = 28,
                                    crowdStatus = doc.getString("crowdStatus") ?: "LOW",
                                    adultTicketPrice = fare,
                                    childTicketPrice = (fare / 2).coerceAtLeast(5),
                                    isWomenFreeTravel = doc.getBoolean("isWomenFreeTravel") ?: (fare <= 20),
                                    etaMinutes = kotlin.math.abs(toIdx - fromIdx) * 4 + 2,
                                    distanceKm = kotlin.math.abs(toIdx - fromIdx) * 3.5 + 1.0,
                                    isLive = true,
                                    currentLatitude = routeStops[fromIdx].latitude,
                                    currentLongitude = routeStops[fromIdx].longitude,
                                    lastUpdatedSecondsAgo = 2L
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firestore routes search error: ${e.message}")
        }

        // 2. Search in Cloud Firestore `buses` collection
        try {
            val db = firestore
            if (db != null) {
                val snapshot = db.collection("buses").get().await()
                if (!snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val bus = parseFirestoreBusItem(doc, fromName, toName)
                        if (bus != null) foundBuses.add(bus)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firestore buses fetch error: ${e.message}")
        }

        // 3. Try Firebase Realtime Database (/buses.json)
        try {
            val url = URL("$RTDB_URL/buses.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonText = reader.readText()
                reader.close()

                if (jsonText.isNotBlank() && jsonText != "null") {
                    val list = parseJsonBuses(jsonText, fromName, toName)
                    foundBuses.addAll(list)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "RTDB buses fetch error: ${e.message}")
        }

        if (foundBuses.isNotEmpty()) foundBuses else null
    }

    /**
     * Extract the exact ordered list of intermediate stops for a route from Firestore
     */
    suspend fun fetchRouteIntermediateStopsFromDatabase(fromStopId: String, toStopId: String, fromName: String, toName: String): List<Stop>? = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext null
            val snapshot = db.collection("routes").get().await()
            for (doc in snapshot.documents) {
                val stops = extractOrderedStopsFromDoc(doc)
                if (stops.isNotEmpty()) {
                    val fromIdx = stops.indexOfFirst { it.id.equals(fromStopId, ignoreCase = true) || it.name.equals(fromName, ignoreCase = true) }
                    val toIdx = stops.indexOfFirst { it.id.equals(toStopId, ignoreCase = true) || it.name.equals(toName, ignoreCase = true) }
                    if (fromIdx != -1 && toIdx != -1 && kotlin.math.abs(toIdx - fromIdx) > 1) {
                        return@withContext if (fromIdx < toIdx) {
                            stops.subList(fromIdx + 1, toIdx)
                        } else {
                            stops.subList(toIdx + 1, fromIdx).reversed()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    private fun extractOrderedStopsFromDoc(doc: DocumentSnapshot): List<Stop> {
        val list = mutableListOf<Stop>()
        val data = doc.data ?: return list
        for ((_, value) in data) {
            if (value is List<*>) {
                for (item in value) {
                    if (item is Map<*, *>) {
                        val name = (item["name"] ?: item["stopName"] ?: item["stop_name"])?.toString()?.trim()
                        if (!name.isNullOrBlank()) {
                            val id = (item["id"] ?: item["stopId"] ?: name).toString().trim()
                            val nameTa = (item["nameTa"] ?: item["name_ta"] ?: "")?.toString()?.trim() ?: ""
                            val district = (item["district"] ?: item["districtName"] ?: "Tamil Nadu")?.toString()?.trim() ?: "Tamil Nadu"
                            val districtTa = (item["districtTa"] ?: item["district_ta"] ?: "தமிழ்நாடு")?.toString()?.trim() ?: "தமிழ்நாடு"
                            val latStr = (item["lat"] ?: item["latitude"])?.toString()
                            val lngStr = (item["lng"] ?: item["longitude"])?.toString()
                            val lat = latStr?.toDoubleOrNull() ?: 0.0
                            val lng = lngStr?.toDoubleOrNull() ?: 0.0
                            val isKey = (item["isKeyStop"] as? Boolean) ?: (item["isMajorHub"] as? Boolean) ?: false
                            val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
                                it.name.equals(name, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
                            }
                            val coords = sanitizeCoordinates(name, id, lat, lng)
                            list.add(
                                Stop(
                                    id = id,
                                    name = name,
                                    nameTa = if (nameTa.isNotBlank()) nameTa else (catalogMatch?.nameTa ?: ""),
                                    district = if (district != "Tamil Nadu") district else (catalogMatch?.district ?: "Erode"),
                                    districtTa = if (districtTa != "தமிழ்நாடு") districtTa else (catalogMatch?.districtTa ?: "ஈரோடு"),
                                    latitude = coords.first,
                                    longitude = coords.second,
                                    isMajorHub = isKey || (catalogMatch?.isMajorHub ?: false)
                                )
                            )
                        }
                    }
                }
            }
        }
        return list
    }

    /**
     * Fetch Live Bus Telemetry for a specific bus from Database
     */
    suspend fun fetchBusLocationFromDatabase(busId: String): BusLocationUpdate? = withContext(Dispatchers.IO) {
        // 1. Try Firestore
        try {
            val db = firestore
            if (db != null) {
                val doc = db.collection("buses").document(busId).get().await()
                if (doc.exists()) {
                    val total = getIntField(doc, "totalSeats", "total_seats") ?: 40
                    val available = getIntField(doc, "availableSeats", "available_seats") ?: 18
                    val occupied = getIntField(doc, "occupiedSeats", "occupied_seats") ?: (total - available)
                    val freeing = getIntField(doc, "seatsFreeingAtNextStop", "seats_freeing") ?: 8

                    return@withContext BusLocationUpdate(
                        busId = doc.id,
                        latitude = getDoubleField(doc, "currentLatitude", "latitude", "lat") ?: 11.3410,
                        longitude = getDoubleField(doc, "currentLongitude", "longitude", "lng") ?: 77.7170,
                        etaMinutes = getIntField(doc, "etaMinutes", "eta") ?: 6,
                        distanceKm = getDoubleField(doc, "distanceKm", "distance") ?: 2.4,
                        availableSeats = available,
                        occupiedSeats = occupied,
                        seatsFreeingAtNextStop = freeing,
                        expectedAvailableSeats = (available + freeing).coerceAtMost(total),
                        crowdStatus = getStringField(doc, "crowdStatus", "crowd_status") ?: "LOW",
                        adultTicketPrice = getIntField(doc, "adultTicketPrice", "fare", "price") ?: 18,
                        childTicketPrice = getIntField(doc, "childTicketPrice", "child_fare") ?: 9,
                        lastUpdatedSecondsAgo = getLongField(doc, "lastUpdatedSecondsAgo", "updated_at") ?: 3L
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Try RTDB
        try {
            val url = URL("$RTDB_URL/buses/$busId.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonText = reader.readText()
                reader.close()

                if (jsonText.isNotBlank() && jsonText != "null") {
                    val obj = JSONObject(jsonText)
                    val total = obj.optInt("totalSeats", obj.optInt("total_seats", 40))
                    val available = obj.optInt("availableSeats", obj.optInt("available_seats", 18))
                    val occupied = obj.optInt("occupiedSeats", total - available)
                    val freeing = obj.optInt("seatsFreeingAtNextStop", 8)

                    return@withContext BusLocationUpdate(
                        busId = busId,
                        latitude = obj.optDouble("currentLatitude", obj.optDouble("latitude", obj.optDouble("lat", 11.3410))),
                        longitude = obj.optDouble("currentLongitude", obj.optDouble("longitude", obj.optDouble("lng", 77.7170))),
                        etaMinutes = obj.optInt("etaMinutes", obj.optInt("eta", 6)),
                        distanceKm = obj.optDouble("distanceKm", obj.optDouble("distance", 2.4)),
                        availableSeats = available,
                        occupiedSeats = occupied,
                        seatsFreeingAtNextStop = freeing,
                        expectedAvailableSeats = (available + freeing).coerceAtMost(total),
                        crowdStatus = obj.optString("crowdStatus", obj.optString("crowd_status", "LOW")),
                        adultTicketPrice = obj.optInt("adultTicketPrice", obj.optInt("fare", 18)),
                        childTicketPrice = obj.optInt("childTicketPrice", 9),
                        lastUpdatedSecondsAgo = obj.optLong("lastUpdatedSecondsAgo", 3L)
                    )
                }
            }
        } catch (_: Exception) {}

        null
    }

    private fun parseFirestoreStop(doc: DocumentSnapshot): Stop? {
        val name = getStringField(doc, "name", "stopName", "stop_name") ?: return null
        val id = doc.id
        val lat = getDoubleField(doc, "latitude", "lat") ?: 0.0
        val lng = getDoubleField(doc, "longitude", "lng") ?: 0.0
        val coords = sanitizeCoordinates(name, id, lat, lng)
        val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
        }
        return Stop(
            id = id,
            name = name,
            nameTa = getStringField(doc, "nameTa", "name_ta") ?: (catalogMatch?.nameTa ?: ""),
            district = getStringField(doc, "district", "districtName") ?: (catalogMatch?.district ?: "Erode"),
            districtTa = getStringField(doc, "districtTa", "district_ta") ?: (catalogMatch?.districtTa ?: "ஈரோடு"),
            latitude = coords.first,
            longitude = coords.second,
            isMajorHub = getBooleanField(doc, "isMajorHub", "is_hub") ?: (catalogMatch?.isMajorHub ?: false)
        )
    }

    private fun parseJsonStops(jsonText: String): List<Stop> {
        val list = mutableListOf<Stop>()
        if (jsonText.trim().startsWith("[")) {
            val arr = JSONArray(jsonText)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", obj.optString("stopName", obj.optString("stop_name", "")))
                if (name.isNotBlank()) {
                    val id = obj.optString("id", "STOP_$i")
                    val lat = obj.optDouble("latitude", obj.optDouble("lat", 0.0))
                    val lng = obj.optDouble("longitude", obj.optDouble("lng", 0.0))
                    val coords = sanitizeCoordinates(name, id, lat, lng)
                    val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
                        it.name.equals(name, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
                    }
                    list.add(
                        Stop(
                            id = id,
                            name = name,
                            nameTa = obj.optString("nameTa", obj.optString("name_ta", catalogMatch?.nameTa ?: "")),
                            district = obj.optString("district", catalogMatch?.district ?: "Erode"),
                            districtTa = obj.optString("districtTa", catalogMatch?.districtTa ?: "ஈரோடு"),
                            latitude = coords.first,
                            longitude = coords.second,
                            isMajorHub = obj.optBoolean("isMajorHub", catalogMatch?.isMajorHub ?: false)
                        )
                    )
                }
            }
        } else if (jsonText.trim().startsWith("{")) {
            val root = JSONObject(jsonText)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val name = obj.optString("name", obj.optString("stopName", obj.optString("stop_name", "")))
                if (name.isNotBlank()) {
                    val id = obj.optString("id", key)
                    val lat = obj.optDouble("latitude", obj.optDouble("lat", 0.0))
                    val lng = obj.optDouble("longitude", obj.optDouble("lng", 0.0))
                    val coords = sanitizeCoordinates(name, id, lat, lng)
                    val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
                        it.name.equals(name, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
                    }
                    list.add(
                        Stop(
                            id = id,
                            name = name,
                            nameTa = obj.optString("nameTa", obj.optString("name_ta", catalogMatch?.nameTa ?: "")),
                            district = obj.optString("district", catalogMatch?.district ?: "Erode"),
                            districtTa = obj.optString("districtTa", catalogMatch?.districtTa ?: "ஈரோடு"),
                            latitude = coords.first,
                            longitude = coords.second,
                            isMajorHub = obj.optBoolean("isMajorHub", catalogMatch?.isMajorHub ?: false)
                        )
                    )
                }
            }
        }
        return list
    }

    private fun parseFirestoreBusItem(doc: DocumentSnapshot, fromName: String, toName: String): BusItem? {
        val busNum = getStringField(doc, "busNumber", "bus_number", "number") ?: return null
        val total = getIntField(doc, "totalSeats", "total_seats") ?: 40
        val available = getIntField(doc, "availableSeats", "available_seats") ?: 18
        val occupied = getIntField(doc, "occupiedSeats", "occupied_seats") ?: (total - available)
        val freeing = getIntField(doc, "seatsFreeingAtNextStop", "seats_freeing") ?: 8
        val adultFare = getIntField(doc, "adultTicketPrice", "fare", "price", "adult_fare") ?: 18
        val childFare = getIntField(doc, "childTicketPrice", "child_fare") ?: (adultFare / 2).coerceAtLeast(4)

        return BusItem(
            busId = doc.id,
            busNumber = busNum,
            routeId = getStringField(doc, "routeId", "route_id") ?: "R_DEFAULT",
            routeName = getStringField(doc, "routeName", "route_name") ?: "$fromName → $toName",
            totalSeats = total,
            occupiedSeats = occupied,
            availableSeats = available,
            seatsFreeingAtNextStop = freeing,
            expectedAvailableSeats = (available + freeing).coerceAtMost(total),
            crowdStatus = getStringField(doc, "crowdStatus", "crowd_status") ?: "LOW",
            adultTicketPrice = adultFare,
            childTicketPrice = childFare,
            isWomenFreeTravel = getBooleanField(doc, "isWomenFreeTravel", "women_free", "is_women_free") ?: false,
            etaMinutes = getIntField(doc, "etaMinutes", "eta") ?: 6,
            distanceKm = getDoubleField(doc, "distanceKm", "distance") ?: 2.4,
            isLive = getBooleanField(doc, "isLive", "live") ?: true,
            currentLatitude = getDoubleField(doc, "currentLatitude", "latitude", "lat") ?: 11.3410,
            currentLongitude = getDoubleField(doc, "currentLongitude", "longitude", "lng") ?: 77.7170,
            lastUpdatedSecondsAgo = getLongField(doc, "lastUpdatedSecondsAgo", "updated_at") ?: 3L
        )
    }

    private fun parseJsonBuses(jsonText: String, fromName: String, toName: String): List<BusItem> {
        val list = mutableListOf<BusItem>()
        if (jsonText.trim().startsWith("[")) {
            val arr = JSONArray(jsonText)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val bus = parseJsonObjectBus(obj, "BUS_$i", fromName, toName)
                if (bus != null) list.add(bus)
            }
        } else if (jsonText.trim().startsWith("{")) {
            val root = JSONObject(jsonText)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val bus = parseJsonObjectBus(obj, key, fromName, toName)
                if (bus != null) list.add(bus)
            }
        }
        return list
    }

    private fun parseJsonObjectBus(obj: JSONObject, defaultId: String, fromName: String, toName: String): BusItem? {
        val busNum = obj.optString("busNumber", obj.optString("bus_number", obj.optString("number", "")))
        if (busNum.isBlank()) return null
        val total = obj.optInt("totalSeats", obj.optInt("total_seats", 40))
        val available = obj.optInt("availableSeats", obj.optInt("available_seats", 18))
        val occupied = obj.optInt("occupiedSeats", total - available)
        val freeing = obj.optInt("seatsFreeingAtNextStop", 8)
        val adultFare = obj.optInt("adultTicketPrice", obj.optInt("fare", 18))
        val childFare = obj.optInt("childTicketPrice", (adultFare / 2).coerceAtLeast(4))

        return BusItem(
            busId = obj.optString("busId", obj.optString("id", defaultId)),
            busNumber = busNum,
            routeId = obj.optString("routeId", "ROUTE001"),
            routeName = obj.optString("routeName", "$fromName → $toName"),
            totalSeats = total,
            occupiedSeats = occupied,
            availableSeats = available,
            seatsFreeingAtNextStop = freeing,
            expectedAvailableSeats = (available + freeing).coerceAtMost(total),
            crowdStatus = obj.optString("crowdStatus", "LOW"),
            adultTicketPrice = adultFare,
            childTicketPrice = childFare,
            isWomenFreeTravel = obj.optBoolean("isWomenFreeTravel", false),
            etaMinutes = obj.optInt("etaMinutes", 6),
            distanceKm = obj.optDouble("distanceKm", 2.4),
            isLive = obj.optBoolean("isLive", true),
            currentLatitude = obj.optDouble("currentLatitude", 11.3410),
            currentLongitude = obj.optDouble("currentLongitude", 77.7170),
            lastUpdatedSecondsAgo = obj.optLong("lastUpdatedSecondsAgo", 3L)
        )
    }

    private fun getStringField(doc: DocumentSnapshot, vararg fieldNames: String): String? {
        for (name in fieldNames) {
            val v = doc.getString(name)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun getIntField(doc: DocumentSnapshot, vararg fieldNames: String): Int? {
        for (name in fieldNames) {
            val v = doc.getLong(name)
            if (v != null) return v.toInt()
        }
        return null
    }

    private fun getDoubleField(doc: DocumentSnapshot, vararg fieldNames: String): Double? {
        for (name in fieldNames) {
            val v = doc.getDouble(name)
            if (v != null) return v
        }
        return null
    }

    private fun getLongField(doc: DocumentSnapshot, vararg fieldNames: String): Long? {
        for (name in fieldNames) {
            val v = doc.getLong(name)
            if (v != null) return v
        }
        return null
    }

    private fun getBooleanField(doc: DocumentSnapshot, vararg fieldNames: String): Boolean? {
        for (name in fieldNames) {
            val v = doc.getBoolean(name)
            if (v != null) return v
        }
        return null
    }

    private fun fetchHttpText(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val content = reader.readText()
                reader.close()
                content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeCoordinates(stopName: String, stopId: String, lat: Double, lng: Double): Pair<Double, Double> {
        val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.name.equals(stopName, ignoreCase = true) ||
            it.id.equals(stopId, ignoreCase = true) ||
            it.nameTa.equals(stopName, ignoreCase = true) ||
            stopName.contains(it.name, ignoreCase = true) ||
            it.name.contains(stopName, ignoreCase = true)
        }

        if (catalogMatch != null) {
            val isZero = lat == 0.0 || lng == 0.0
            val distToCatalog = if (!isZero) {
                TamilNaduTransitDatabase.calculateHaversineKm(lat, lng, catalogMatch.latitude, catalogMatch.longitude)
            } else 999.0

            if (isZero || distToCatalog > 5.0) {
                // If coordinates are zero, missing, or > 5 km away from authentic catalog, use authentic catalog coordinates!
                return Pair(catalogMatch.latitude, catalogMatch.longitude)
            }
            return Pair(lat, lng)
        }

        if (lat != 0.0 && lng != 0.0) {
            return Pair(lat, lng)
        }
        return Pair(11.3410, 77.7170)
    }

    private fun extractStopsFromFirestoreDoc(doc: DocumentSnapshot, map: MutableMap<String, Stop>) {
        try {
            val data = doc.data ?: return

            // Scan all fields in the document
            for ((_, value) in data) {
                if (value is List<*>) {
                    for (item in value) {
                        if (item is Map<*, *>) {
                            val name = (item["name"] ?: item["stopName"] ?: item["stop_name"])?.toString()?.trim()
                            if (!name.isNullOrBlank()) {
                                val id = (item["id"] ?: item["stopId"] ?: name).toString().trim()
                                val nameTa = (item["nameTa"] ?: item["name_ta"] ?: "")?.toString()?.trim() ?: ""
                                val district = (item["district"] ?: item["districtName"] ?: "Tamil Nadu")?.toString()?.trim() ?: "Tamil Nadu"
                                val districtTa = (item["districtTa"] ?: item["district_ta"] ?: "தமிழ்நாடு")?.toString()?.trim() ?: "தமிழ்நாடு"
                                val latStr = (item["lat"] ?: item["latitude"])?.toString()
                                val lngStr = (item["lng"] ?: item["longitude"])?.toString()
                                val lat = latStr?.toDoubleOrNull() ?: 0.0
                                val lng = lngStr?.toDoubleOrNull() ?: 0.0
                                val isKey = (item["isKeyStop"] as? Boolean) ?: (item["isMajorHub"] as? Boolean) ?: false

                                val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
                                    it.name.equals(name, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
                                }
                                val coords = sanitizeCoordinates(name, id, lat, lng)

                                map[id] = Stop(
                                    id = id,
                                    name = name,
                                    nameTa = if (nameTa.isNotBlank()) nameTa else (catalogMatch?.nameTa ?: ""),
                                    district = if (district != "Tamil Nadu") district else (catalogMatch?.district ?: "Erode"),
                                    districtTa = if (districtTa != "தமிழ்நாடு") districtTa else (catalogMatch?.districtTa ?: "ஈரோடு"),
                                    latitude = coords.first,
                                    longitude = coords.second,
                                    isMajorHub = isKey || (catalogMatch?.isMajorHub ?: false)
                                )
                            }
                        } else if (item is String) {
                            addStopByName(item, map)
                        }
                    }
                }
            }

            // Also check individual fields: from, to, origin, destination
            val fromName = getStringField(doc, "from", "origin", "fromStop", "source")
            if (!fromName.isNullOrBlank()) addStopByName(fromName, map)
            val toName = getStringField(doc, "to", "destination", "toStop", "dest")
            if (!toName.isNullOrBlank()) addStopByName(toName, map)
        } catch (_: Exception) {}
    }

    private fun extractStopsFromJsonString(jsonText: String, map: MutableMap<String, Stop>) {
        try {
            if (jsonText.trim().startsWith("[")) {
                val arr = JSONArray(jsonText)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    extractStopsFromJsonObject(obj, map)
                }
            } else if (jsonText.trim().startsWith("{")) {
                val root = JSONObject(jsonText)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key)
                    if (obj != null) {
                        extractStopsFromJsonObject(obj, map)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractStopsFromJsonObject(obj: JSONObject, map: MutableMap<String, Stop>) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val stopsArr = obj.optJSONArray(key)
            if (stopsArr != null) {
                for (i in 0 until stopsArr.length()) {
                    val item = stopsArr.opt(i)
                    if (item is String) {
                        addStopByName(item, map)
                    } else if (item is JSONObject) {
                        val name = item.optString("name", item.optString("stopName", item.optString("stop_name", ""))).trim()
                        if (name.isNotBlank()) {
                            val id = item.optString("id", item.optString("stopId", name)).trim()
                            val nameTa = item.optString("nameTa", item.optString("name_ta", "")).trim()
                            val district = item.optString("district", item.optString("districtName", "Tamil Nadu")).trim()
                            val districtTa = item.optString("districtTa", "தமிழ்நாடு").trim()
                            val latStr = if (item.has("lat")) item.optString("lat") else item.optString("latitude")
                            val lngStr = if (item.has("lng")) item.optString("lng") else item.optString("longitude")
                            val lat = latStr.toDoubleOrNull() ?: item.optDouble("lat", item.optDouble("latitude", 0.0))
                            val lng = lngStr.toDoubleOrNull() ?: item.optDouble("lng", item.optDouble("longitude", 0.0))
                            val isKey = item.optBoolean("isKeyStop", item.optBoolean("isMajorHub", false))

                            val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
                                it.name.equals(name, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
                            }
                            val coords = sanitizeCoordinates(name, id, lat, lng)

                            map[id] = Stop(
                                id = id,
                                name = name,
                                nameTa = if (nameTa.isNotBlank()) nameTa else (catalogMatch?.nameTa ?: ""),
                                district = if (district != "Tamil Nadu") district else (catalogMatch?.district ?: "Erode"),
                                districtTa = if (districtTa != "தமிழ்நாடு") districtTa else (catalogMatch?.districtTa ?: "ஈரோடு"),
                                latitude = coords.first,
                                longitude = coords.second,
                                isMajorHub = isKey || (catalogMatch?.isMajorHub ?: false)
                            )
                        }
                    }
                }
            }
        }
        val from = obj.optString("from", obj.optString("origin", obj.optString("fromStop", "")))
        if (from.isNotBlank()) addStopByName(from, map)
        val to = obj.optString("to", obj.optString("destination", obj.optString("toStop", "")))
        if (to.isNotBlank()) addStopByName(to, map)
    }

    private fun addStopByName(rawName: String, map: MutableMap<String, Stop>) {
        val clean = rawName.trim()
        if (clean.isBlank()) return

        val catalogMatch = TamilNaduTransitDatabase.allStops.firstOrNull {
            it.name.equals(clean, ignoreCase = true) ||
            it.nameTa.equals(clean, ignoreCase = true) ||
            it.id.equals(clean, ignoreCase = true) ||
            it.name.contains(clean, ignoreCase = true)
        }

        if (catalogMatch != null) {
            map[catalogMatch.id] = catalogMatch
        } else {
            val generatedId = "STOP_" + clean.replace(Regex("[^A-Za-z0-9]"), "_").uppercase()
            val coords = sanitizeCoordinates(clean, generatedId, 0.0, 0.0)
            map[generatedId] = Stop(
                id = generatedId,
                name = clean,
                nameTa = "",
                district = "Erode",
                districtTa = "ஈரோடு",
                latitude = coords.first,
                longitude = coords.second,
                isMajorHub = false
            )
        }
    }
}
