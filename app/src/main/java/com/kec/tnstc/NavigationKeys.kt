package com.kec.tnstc

sealed interface ScreenRoute

data object HomeScreenRoute : ScreenRoute

data class AvailableBusesRoute(
    val fromStopId: String,
    val fromStopName: String,
    val toStopId: String,
    val toStopName: String
) : ScreenRoute

data class BusTrackingRoute(
    val busId: String,
    val busNumber: String,
    val fromStopName: String,
    val toStopName: String,
    val etaMinutes: Int,
    val distanceKm: Double,
    val availableSeats: Int,
    val totalSeats: Int,
    val crowdStatus: String
) : ScreenRoute
