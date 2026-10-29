package com.kec.tnstc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kec.tnstc.data.AppLanguage
import com.kec.tnstc.data.BusItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun formatClockTime(additionalMinutes: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, additionalMinutes)
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(cal.time)
}

private data class CrowdConfig(
    val label: String,
    val badgeBg: Color,
    val textColor: Color,
    val borderColor: Color,
    val progressColor: Color,
    val dotColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusSelectionScreen(
    viewModel: TransitViewModel,
    fromStopName: String,
    toStopName: String,
    onBackClicked: () -> Unit,
    onBusSelected: (BusItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isTamil = state.language == AppLanguage.TAMIL
    val buses = state.availableBuses

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isTamil) "கிடைக்கும் பேருந்துகள்" else "Available Direct Buses",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isTamil) "தமிழ்நாடு அரசுப் போக்குவரத்து • TNSTC" else "TNSTC • Tamil Nadu State Transit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1565C0)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            // ROUTE SUMMARY BANNER
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fromStopName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFC62828))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = toStopName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE3F2FD),
                            border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = Color(0xFF1565C0),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${buses.size} ${if (isTamil) "பேருந்துகள்" else "Buses"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1565C0)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isTamil) "👉 நேரலை வரைபடத்தில் கண்காணிக்க ஏதேனும் ஒரு பேருந்தைத் தேர்ந்தெடுக்கவும்" else "👉 Tap any bus below to view live route map & tracking",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }
            }

            if (buses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isTamil) "நேரடிப் பேருந்துகள் வரவில்லை" else "Searching for Live Buses...",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isTamil) "இந்த வழித்தடத்தில் நேரடிப் பேருந்துகள் தொடர்பு கொள்ளப்படுகின்றன." else "Connecting to TNSTC live schedule network...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (buses.any { it.isWomenFreeTravel }) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFCE4EC),
                                border = BorderStroke(1.dp, Color(0xFFF48FB1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🌸", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isTamil) "வெற்றிப் பயணம் - மகளிர் இலவசப் பயணம்" else "Vetri Payanam: Free for Women within District",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF880E4F)
                                        )
                                        Text(
                                            text = if (isTamil) "மாவட்டத்திற்குள் இயக்கப்படும் சாதாரண நகரப் பேருந்துகளில் மகளிருக்கு கட்டணமில்லாப் பயணம் (₹0 கட்டணம்)." else "Applicable on ordinary town buses within district limits (₹0 ticket).",
                                            fontSize = 11.sp,
                                            color = Color(0xFFAD1457)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(buses) { bus ->
                        GovernmentBusCard(
                            bus = bus,
                            fromStopName = fromStopName,
                            toStopName = toStopName,
                            isTamil = isTamil,
                            onSelect = { onBusSelected(bus) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GovernmentBusCard(
    bus: BusItem,
    fromStopName: String,
    toStopName: String,
    isTamil: Boolean,
    onSelect: () -> Unit
) {
    val occupancyRatio = bus.occupiedSeats.toFloat() / maxOf(1, bus.totalSeats).toFloat()
    val tripDurationMinutes = maxOf(15, (bus.distanceKm * 1.8).toInt())
    val departureTimeStr = formatClockTime(bus.etaMinutes)
    val reachTimeStr = formatClockTime(bus.etaMinutes + tripDurationMinutes)

    // Crowd colors & labels
    val crowdConfig = when (bus.crowdStatus.uppercase()) {
        "LOW" -> CrowdConfig(
            label = if (isTamil) "குறைந்த கூட்டம் (Low)" else "Low Crowd",
            badgeBg = Color(0xFFE8F5E9),
            textColor = Color(0xFF1B5E20),
            borderColor = Color(0xFFA5D6A7),
            progressColor = Color(0xFF2E7D32),
            dotColor = Color(0xFF22C55E)
        )
        "MEDIUM", "MODERATE" -> CrowdConfig(
            label = if (isTamil) "மிதமான கூட்டம் (Medium)" else "Moderate Crowd",
            badgeBg = Color(0xFFFFF3E0),
            textColor = Color(0xFFE65100),
            borderColor = Color(0xFFFFCC80),
            progressColor = Color(0xFFF57C00),
            dotColor = Color(0xFFF59E0B)
        )
        else -> CrowdConfig(
            label = if (isTamil) "அதிக கூட்டம் (Rush)" else "High Rush",
            badgeBg = Color(0xFFFFEBEE),
            textColor = Color(0xFFB71C1C),
            borderColor = Color(0xFFEF9A9A),
            progressColor = Color(0xFFD32F2F),
            dotColor = Color(0xFFEF4444)
        )
    }

    // Service Pill Configuration
    val (serviceTitle, serviceBg, serviceColor) = when {
        bus.routeId.contains("Deluxe", ignoreCase = true) ->
            Triple(if (isTamil) "டீலக்ஸ் (DELUXE)" else "DELUXE EXPRESS", Color(0xFFE3F2FD), Color(0xFF0D47A1))
        bus.routeId.contains("Super Fast", ignoreCase = true) ->
            Triple(if (isTamil) "சூப்பர் பாஸ்ட் (SUPER FAST)" else "SUPER FAST 1-to-1", Color(0xFFEDE7F6), Color(0xFF4A148C))
        bus.routeId.contains("Ordinary", ignoreCase = true) ->
            Triple(if (isTamil) "சாதாரண (ORDINARY)" else "ORDINARY SERVICE", Color(0xFFE0F2F1), Color(0xFF004D40))
        bus.isWomenFreeTravel || bus.routeId.contains("Town", ignoreCase = true) ->
            Triple(if (isTamil) "நகரப் பேருந்து (TOWN BUS)" else "TOWN BUS", Color(0xFFE8F5E9), Color(0xFF1B5E20))
        else ->
            Triple(if (isTamil) "அரசுப் பேருந்து" else "TNSTC TRANSIT", Color(0xFFF1F5F9), Color(0xFF334155))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. TOP HEADER: Bus Registration Plate + Service Badge + Live Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Official Tamil Nadu Bus Registration Number Plate
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF1D4ED8)
                        ) {
                            Text(
                                text = "IND",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bus.busNumber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFEF08A)
                        )
                    }
                }

                // Service Type Pill & Live Pulse
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = serviceBg
                    ) {
                        Text(
                            text = serviceTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = serviceColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. TIMELINE SCHEDULE BOX: Boarding Departure Time ─── Duration ───► Destination Reach Time
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Departure / Boarding Column
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = departureTimeStr,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = if (bus.etaMinutes <= 1) (if (isTamil) "இப்போது" else "Now") else if (isTamil) "அடுத்து ${bus.etaMinutes} நிமிடம்" else "In ${bus.etaMinutes} mins",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fromStopName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Journey Duration & Distance Middle Indicator
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⏱️ $tripDurationMinutes min",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(Color(0xFFCBD5E1))
                            )
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF1565C0)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(Color(0xFFCBD5E1))
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${bus.distanceKm} km",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Destination / Reach Column
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = reachTimeStr,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = if (isTamil) "🏁 சேரும் நேரம்" else "🏁 Est. Reach",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = toStopName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. TICKET PRICE SECTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Adult Ticket Price
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎫",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (isTamil) "பெரியவர் கட்டணம்" else "Adult Ticket",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "₹${bus.adultTicketPrice}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }

                // Child Ticket Price
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👶",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (isTamil) "சிறியவர் கட்டணம்" else "Child Ticket",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "₹${bus.childTicketPrice}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            // Vetri Payanam Free Travel Scheme for Qualifying Buses
            if (bus.isWomenFreeTravel) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFDF2F8),
                    border = BorderStroke(1.dp, Color(0xFFF472B6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🌸", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isTamil) "வெற்றிப் பயணம்: மகளிருக்கு ₹0 கட்டணமில்லாப் பயணம்" else "Vetri Payanam: Free for Women (₹0 Fare)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9D174D)
                            )
                            Text(
                                text = if (isTamil) "சாதாரண நகரப் பேருந்து • தமிழக அரசு இலவசப் பயணத் திட்டம்" else "Ordinary Town Bus • TN Govt Free Travel Scheme",
                                fontSize = 10.sp,
                                color = Color(0xFFBE185D)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. CROWD LEVEL & SEAT OCCUPANCY SECTION
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Crowd Level Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = crowdConfig.badgeBg,
                            border = BorderStroke(1.dp, crowdConfig.borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(crowdConfig.dotColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = crowdConfig.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = crowdConfig.textColor
                                )
                            }
                        }

                        // Available Seats Count
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EventSeat,
                                contentDescription = null,
                                tint = if (bus.availableSeats <= 8) Color(0xFFE65100) else Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${bus.availableSeats} / ${bus.totalSeats} ${if (isTamil) "இருக்கைகள் உள்ளன" else "Seats Free"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bus.availableSeats <= 8) Color(0xFFE65100) else Color(0xFF1E293B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Linear Seat Occupancy Progress Bar
                    LinearProgressIndicator(
                        progress = { occupancyRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = crowdConfig.progressColor,
                        trackColor = Color(0xFFE2E8F0)
                    )

                    if (bus.seatsFreeingAtNextStop > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "💡",
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTamil) "${bus.seatsFreeingAtNextStop} பயணிகள் அடுத்த நிறுத்தத்தில் இறங்குவர்" else "${bus.seatsFreeingAtNextStop} passengers deboarding at next stop",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. ACTION BUTTON: "Track Live Bus" (Tapping this or the card navigates to Map)
            Button(
                onClick = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "பேருந்தைத் தேர்ந்தெடுத்து நேரலை வரைபடம் பார்க்கவும் ➔" else "Select & Track Bus on Live Map ➔",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
