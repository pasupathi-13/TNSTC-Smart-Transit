package com.kec.tnstc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusDetailsScreen(
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
    onTrackLiveBusClicked: () -> Unit,
    onWakeMeUpClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bus Details", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF6F8FA))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Bus Number", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            Text(busNumber, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1976D2))
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = "🟢 LIVE",
                                color = Color(0xFF2E7D32),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFECEFF1))
                    Spacer(modifier = Modifier.height(16.dp))

                    DetailRow(label = "Route", value = "$fromStopName → $toStopName")
                    DetailRow(label = "Boarding Stop", value = fromStopName)
                    DetailRow(label = "Destination", value = toStopName)
                    DetailRow(label = "ETA", value = "$etaMinutes minutes")
                    DetailRow(label = "Distance", value = "$distanceKm km")
                    DetailRow(label = "Available Seats", value = "$availableSeats / $totalSeats")
                    DetailRow(label = "Occupied Seats", value = "${totalSeats - availableSeats}")
                    DetailRow(label = "Crowd Level", value = crowdStatus.uppercase())
                    DetailRow(label = "Last Updated", value = "5 seconds ago")

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFECEFF1))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("🎟️ TICKET FARE", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    DetailRow(label = "Adult (Major / Full Ticket)", value = "₹18")
                    DetailRow(label = "Child (Minor / Half Ticket)", value = "₹9")
                    DetailRow(label = "Women Commuters (மகளிர்)", value = "FREE / ₹0 (வெற்றிப் பயணம் திட்டம்)")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onTrackLiveBusClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Track Live Bus", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onWakeMeUpClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100))
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFE65100))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wake Me Up Alert", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color.Gray)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
    }
}
