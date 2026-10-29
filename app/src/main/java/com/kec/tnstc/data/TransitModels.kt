package com.kec.tnstc.data

data class Stop(
    val id: String,
    val name: String,
    val nameTa: String = "",
    val district: String = "Erode",
    val districtTa: String = "ஈரோடு",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isMajorHub: Boolean = false
)

data class RouteStop(
    val stopId: String,
    val name: String,
    val nameTa: String = "",
    val sequence: Int,
    val distanceFromStartKm: Double = 0.0,
    val durationMinsFromStart: Int = 0
)

data class TransitRoute(
    val id: String,
    val routeNumber: String,
    val routeName: String,
    val routeNameTa: String,
    val originStopId: String,
    val destinationStopId: String,
    val busType: String = "Express", // "Ordinary Town", "Deluxe", "Super Fast", "Ultra Deluxe", "SETC Classic", "AC Sleeper"
    val operator: String = "TNSTC", // "TNSTC Erode", "TNSTC Coimbatore", "TNSTC Salem", "SETC", "TNSTC Madurai", etc.
    val stops: List<RouteStop>,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMins: Int = 0,
    val baseAdultPrice: Int = 18,
    val baseChildPrice: Int = 9,
    val isWomenFreeTravel: Boolean = false
)

data class BusItem(
    val busId: String,
    val busNumber: String,
    val routeId: String,
    val routeName: String,
    val totalSeats: Int,
    val occupiedSeats: Int,
    val availableSeats: Int,
    val seatsFreeingAtNextStop: Int = 8, // Passengers getting down at this stop
    val expectedAvailableSeats: Int = 26, // Total available after passengers get down
    val crowdStatus: String, // "LOW", "MEDIUM", "HIGH"
    val adultTicketPrice: Int = 18, // Major / Full Ticket (₹)
    val childTicketPrice: Int = 9,  // Minor / Half Ticket (₹)
    val isWomenFreeTravel: Boolean = false, // TN Govt Vidiyal Payanam Scheme
    val etaMinutes: Int,
    val distanceKm: Double,
    val isLive: Boolean,
    val currentLatitude: Double,
    val currentLongitude: Double,
    val lastUpdatedSecondsAgo: Long
)

data class BusLocationUpdate(
    val busId: String,
    val latitude: Double,
    val longitude: Double,
    val etaMinutes: Int,
    val distanceKm: Double,
    val availableSeats: Int,
    val occupiedSeats: Int,
    val seatsFreeingAtNextStop: Int = 8,
    val expectedAvailableSeats: Int = 26,
    val crowdStatus: String,
    val adultTicketPrice: Int = 18,
    val childTicketPrice: Int = 9,
    val lastUpdatedSecondsAgo: Long
)

data class RecentTrip(
    val id: String,
    val fromStopName: String,
    val toStopName: String,
    val fromStopId: String,
    val toStopId: String,
    val dateText: String
)

data class WakeAlert(
    val alertId: String,
    val busId: String,
    val destinationStopId: String,
    val destinationStopName: String,
    val alertDistanceKm: Double,
    val active: Boolean
)

data class ConnectingTripItem(
    val tripId: String,
    val transferStop: Stop,
    val leg1Bus: BusItem,
    val leg2Bus: BusItem,
    val layoverMinutes: Int,
    val totalEtaMinutes: Int,
    val totalAdultFare: Int,
    val totalChildFare: Int,
    val isLeg1WomenFree: Boolean,
    val isLeg2WomenFree: Boolean
)

enum class AppThemeMode(val titleEn: String, val titleTa: String) {
    LIGHT("Light Mode", "வெளிர் பயன்முறை"),
    DARK("Dark Mode", "இருண்ட பயன்முறை"),
    SYSTEM("System Default", "கணினி இயல்புநிலை")
}

enum class AppLanguage(val code: String, val displayName: String, val subtitle: String) {
    ENGLISH("en", "English", "Default Language"),
    TAMIL("ta", "தமிழ்", "Tamil Language")
}

data class WakeUpRingtone(
    val id: String,
    val nameEn: String,
    val nameTa: String,
    val descriptionEn: String,
    val descriptionTa: String,
    val toneCode: Int
)


