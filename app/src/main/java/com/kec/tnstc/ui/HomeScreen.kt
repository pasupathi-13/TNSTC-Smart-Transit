package com.kec.tnstc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kec.tnstc.R
import com.kec.tnstc.data.AppLanguage
import com.kec.tnstc.data.AppThemeMode
import com.kec.tnstc.data.Stop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransitViewModel,
    onFindBusesClicked: (String, String, String, String) -> Unit,
    onTrackActiveAlarm: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isTamil = state.language == AppLanguage.TAMIL
    val context = androidx.compose.ui.platform.LocalContext.current

    var showBoardingDialog by remember { mutableStateOf(false) }
    var showDestinationDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = state.currentTab == 0,
                    onClick = { viewModel.setBottomNavTab(0) },
                    icon = { Icon(Icons.Default.DirectionsBus, contentDescription = "Home") },
                    label = { Text(if (isTamil) "முகப்பு" else "Home") }
                )
                NavigationBarItem(
                    selected = state.currentTab == 1,
                    onClick = { viewModel.setBottomNavTab(1) },
                    icon = { Icon(Icons.Default.History, contentDescription = "Trips") },
                    label = { Text(if (isTamil) "பயணங்கள்" else "Trips") }
                )
                NavigationBarItem(
                    selected = state.currentTab == 2,
                    onClick = { viewModel.setBottomNavTab(2) },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
                    label = { Text(if (isTamil) "எச்சரிக்கை" else "Alerts") }
                )
                NavigationBarItem(
                    selected = state.currentTab == 3,
                    onClick = { viewModel.setBottomNavTab(3) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(if (isTamil) "அமைப்புகள்" else "Settings") }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (state.currentTab) {
                0 -> HomeTabContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenBoardingDialog = {
                        viewModel.onStopSearchQueryChanged("")
                        showBoardingDialog = true
                    },
                    onOpenDestinationDialog = {
                        viewModel.onStopSearchQueryChanged("")
                        showDestinationDialog = true
                    },
                    onFindBusesClicked = onFindBusesClicked,
                    onTrackActiveAlarm = onTrackActiveAlarm
                )
                1 -> TripsTabContent(state = state, viewModel = viewModel, onFindBusesClicked = onFindBusesClicked)
                2 -> AlertsTabContent(state = state, viewModel = viewModel, onTrackActiveAlarm = onTrackActiveAlarm)
                3 -> SettingsTabContent(state = state, viewModel = viewModel)
            }


            if (showBoardingDialog) {
                SearchableStopDialog(
                    title = if (isTamil) "ஏறும் இடத்தை தேர்வு செய்க" else "Select Boarding Stop",
                    stops = state.filteredStops,
                    searchQuery = state.stopSearchQuery,
                    isTamil = isTamil,
                    onQueryChange = { viewModel.onStopSearchQueryChanged(it) },
                    onSelect = {
                        viewModel.selectBoardingStop(it)
                        showBoardingDialog = false
                    },
                    onSelectCustomPlace = { place ->
                        viewModel.selectCustomPlace(place, isBoarding = true, context = context)
                        showBoardingDialog = false
                    },
                    onDismiss = { showBoardingDialog = false }
                )
            }

            if (showDestinationDialog) {
                SearchableStopDialog(
                    title = if (isTamil) "சேருமிடத்தை தேர்வு செய்க" else "Select Destination Stop",
                    stops = state.filteredStops,
                    searchQuery = state.stopSearchQuery,
                    isTamil = isTamil,
                    onQueryChange = { viewModel.onStopSearchQueryChanged(it) },
                    onSelect = { destStop ->
                        viewModel.selectDestinationStop(destStop)
                        showDestinationDialog = false
                        // Automatically show available buses when boarding stop is already chosen
                        val boarding = state.selectedBoardingStop
                        if (boarding != null && boarding.id != destStop.id && !boarding.name.equals(destStop.name, ignoreCase = true)) {
                            viewModel.findBuses(
                                onSuccess = {
                                    onFindBusesClicked(boarding.id, boarding.name, destStop.id, destStop.name)
                                }
                            )
                        }
                    },
                    onSelectCustomPlace = { place ->
                        viewModel.selectCustomPlace(place, isBoarding = false, context = context) { destStop ->
                            val boarding = viewModel.uiState.value.selectedBoardingStop
                            if (boarding != null && boarding.id != destStop.id && !boarding.name.equals(destStop.name, ignoreCase = true)) {
                                viewModel.findBuses(
                                    onSuccess = {
                                        onFindBusesClicked(boarding.id, boarding.name, destStop.id, destStop.name)
                                    }
                                )
                            }
                        }
                        showDestinationDialog = false
                    },
                    onDismiss = { showDestinationDialog = false }
                )
            }
        }
    }
}

@Composable
fun HomeTabContent(
    state: TransitUiState,
    viewModel: TransitViewModel,
    onOpenBoardingDialog: () -> Unit,
    onOpenDestinationDialog: () -> Unit,
    onFindBusesClicked: (String, String, String, String) -> Unit,
    onTrackActiveAlarm: (String, String) -> Unit
) {
    val isTamil = state.language == AppLanguage.TAMIL

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E3C72), Color(0xFF2A5298))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(56.dp)
                                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "TNSTC Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isTamil) "TNSTC ஸ்மார்ட் பேருந்து" else "TNSTC Smart Transit",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isTamil) "தமிழ்நாடு முழுவதும் நேரடி பேருந்து கண்காணிப்பு" else "Tamil Nadu State-wide Transit Network",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-16).dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (isTamil) "எங்கு பயணம் செய்ய வேண்டும்?" else "Where are you going?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    StopInputField(
                        label = if (isTamil) "ஏறும் இடம்" else "Boarding Stop",
                        value = state.selectedBoardingStop?.name ?: (if (isTamil) "ஏறும் இடத்தை தேர்வு செய்க" else "Select your boarding stop"),
                        isSelected = state.selectedBoardingStop != null,
                        icon = Icons.Default.TripOrigin,
                        iconColor = Color(0xFF2E7D32),
                        onClick = onOpenBoardingDialog
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        IconButton(
                            onClick = { viewModel.swapStops() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Swap Stops",
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    StopInputField(
                        label = if (isTamil) "சேருமிடம்" else "Destination",
                        value = state.selectedDestinationStop?.name ?: (if (isTamil) "சேருமிடத்தை தேர்வு செய்க" else "Select your destination"),
                        isSelected = state.selectedDestinationStop != null,
                        icon = Icons.Default.Place,
                        iconColor = Color(0xFFD32F2F),
                        onClick = onOpenDestinationDialog
                    )

                    if (state.searchErrorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.searchErrorMessage,
                            color = Color(0xFFD32F2F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.findBuses(
                                onSuccess = {
                                    val from = state.selectedBoardingStop!!
                                    val to = state.selectedDestinationStop!!
                                    onFindBusesClicked(from.id, from.name, to.id, to.name)
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        enabled = !state.isSearchingBuses
                    ) {
                        if (state.isSearchingBuses) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.DirectionsBus, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTamil) "கிடைக்கும் பேருந்துகளைக் காட்டு (Available Buses)" else "Show Available Buses",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Tamil Nadu Govt "Vetri Payanam" Scheme Announcement Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF48FB1))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE91E63).copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "🌸", fontSize = 22.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isTamil) "வெற்றிப் பயணம் - மகளிர் இலவசப் பயணம்" else "Vetri Payanam - Free Women Travel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF880E4F)
                        )
                        Text(
                            text = if (isTamil) "தமிழ்நாடு அரசு அறிவிப்பு: மாவட்டத்திற்குள் சாதாரண கட்டண நகரப் பேருந்துகளில் மகளிருக்கு கட்டணமில்லாப் பயணம் (₹0 கட்டணம்)." else "TN Govt Scheme: Free travel for women passengers within the district on ordinary town buses (₹0 fare).",
                            fontSize = 11.sp,
                            color = Color(0xFFAD1457),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }


        state.activeAlarm?.let { alarm ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF6D00).copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6D00),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake Me Up Active",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            val distText = if (alarm.alertDistanceKm < 1.0) "${(alarm.alertDistanceKm * 1000).toInt()}m" else "${alarm.alertDistanceKm} km"
                            Text(
                                text = "Alert before ${alarm.destinationStopName} ($distText)",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { onTrackActiveAlarm(alarm.busId, alarm.destinationStopName) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("View", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }


    }
}

@Composable
fun StopInputField(
    label: String,
    value: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableStopDialog(
    title: String,
    stops: List<Stop>,
    searchQuery: String,
    isTamil: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (Stop) -> Unit,
    onSelectCustomPlace: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isTamil) "நிறுத்தத்தைத் தேர்வு செய்க அல்லது கூகுள் மேப்பில் தேடுக" else "Select from database or search any place via Google Maps",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            if (isTamil) "நிறுத்தம் / ஊர் பெயரைத் தேடுக..." else "Type any stop or location...",
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )

                // 1. Google Maps Auto-Routing Option if typed location is not in database or to search globally
                if (searchQuery.trim().length >= 2) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE8F0FE),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1976D2)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable { onSelectCustomPlace(searchQuery.trim()) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AddLocationAlt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isTamil) "கூகுள் மேப்பில் தேடுக: \"${searchQuery.trim()}\"" else "Locate on Google Maps: \"${searchQuery.trim()}\"",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D47A1)
                                )
                                Text(
                                    text = if (isTamil) "டேட்டாபேஸில் இல்லாவிடில் கூகுள் மேப் மூலம் தானாக வழி அமைக்கும்" else "Auto-connects real road route via Google Maps",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1565C0)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (stops.isEmpty() && searchQuery.trim().length < 2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isTamil) "நிறுத்தங்கள் ஏற்றப்படுகின்றன..." else "Loading database stops...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(stops) { stop ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(stop) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (stop.isMajorHub) Color(0xFF1565C0) else Color(0xFF475569),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (stop.isMajorHub) Icons.Default.DirectionsBus else Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isTamil && stop.nameTa.isNotBlank()) stop.nameTa else stop.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${stop.district} • ${stop.id}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isTamil) "மூடுக" else "Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TripsTabContent(
    state: TransitUiState,
    viewModel: TransitViewModel,
    onFindBusesClicked: (String, String, String, String) -> Unit
) {
    val isTamil = state.language == AppLanguage.TAMIL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isTamil) "உங்கள் பயண வரலாறு" else "Your Trip History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (isTamil) "ஒரே தட்டலில் மீண்டும் தேடலாம்" else "Search again with a single tap",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.recentTrips) { trip ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            state.stops.find { it.id == trip.fromStopId }?.let { viewModel.selectBoardingStop(it) }
                            state.stops.find { it.id == trip.toStopId }?.let { viewModel.selectDestinationStop(it) }
                            viewModel.findBuses {
                                onFindBusesClicked(trip.fromStopId, trip.fromStopName, trip.toStopId, trip.toStopName)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${trip.fromStopName} → ${trip.toStopName}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isTamil) "தேடியது: ${trip.dateText}" else "Searched ${trip.dateText}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsTabContent(
    state: TransitUiState,
    viewModel: TransitViewModel,
    onTrackActiveAlarm: (String, String) -> Unit
) {
    val isTamil = state.language == AppLanguage.TAMIL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isTamil) "சேருமிடம் விழிப்பு மணி" else "Wake Me Up Alerts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (isTamil) "சேருமிடம் நெருங்கும் போது விழிப்பூட்டும் எச்சரிக்கைகள்" else "Active and saved transit proximity alarms",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (state.activeAlarm == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isTamil) "செயலில் உள்ள எச்சரிக்கை இல்லை" else "No Active Alert",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isTamil)
                            "பேருந்தைத் தேர்ந்தெடுத்து 'Wake Me Up' என்பதைத் தட்டவும்."
                        else
                            "Select a bus and tap 'Wake Me Up' to get notified before your destination.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val alarm = state.activeAlarm
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isTamil) "செயலில் உள்ள விழிப்பு மணி" else "Active Alarm",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isTamil) "சேருமிடம்: ${alarm.destinationStopName}" else "Destination: ${alarm.destinationStopName}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val distText = if (alarm.alertDistanceKm < 1.0) "${(alarm.alertDistanceKm * 1000).toInt()}${if (isTamil) " மீ" else "m"}" else "${alarm.alertDistanceKm} ${if (isTamil) "கி.மீ" else "km"}"
                    Text(
                        text = if (isTamil) "எச்சரிக்கை தூரம்: $distText" else "Alert distance: $distText",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = { onTrackActiveAlarm(alarm.busId, alarm.destinationStopName) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isTamil) "பேருந்தைக் காண்க" else "Track Bus")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelWakeAlert() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isTamil) "ரத்து செய்க" else "Cancel Alert")
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsTabContent(
    state: TransitUiState,
    viewModel: TransitViewModel
) {
    val isTamil = state.language == AppLanguage.TAMIL
    val context = androidx.compose.ui.platform.LocalContext.current

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.saveCustomAudioFromUri(context, uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (isTamil) "அமைப்புகள்" else "App Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isTamil) "மொழி, தோற்றம் மற்றும் விழிப்பு மணி அமைப்புகள்" else "Configure Language, Theme & Wake-Up Alarm",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. LANGUAGE SETTINGS CARD (மொழி தேர்வு)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isTamil) "பயன்பாட்டு மொழி" else "App Language",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTamil) "ஆங்கிலம் அல்லது தமிழ் மொழியைத் தேர்ந்தெடுக்கவும்" else "Select English or தமிழ் for navigation & alerts",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // English Option
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (state.language == AppLanguage.ENGLISH) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (state.language == AppLanguage.ENGLISH) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1976D2)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setLanguage(AppLanguage.ENGLISH) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🇬🇧 English", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (state.language == AppLanguage.ENGLISH) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Default", fontSize = 11.sp, color = if (state.language == AppLanguage.ENGLISH) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Tamil Option
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (state.language == AppLanguage.TAMIL) Color(0xFFE0F2F1) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (state.language == AppLanguage.TAMIL) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00796B)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setLanguage(AppLanguage.TAMIL) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🇮🇳 தமிழ் (Tamil)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (state.language == AppLanguage.TAMIL) Color(0xFF00796B) else MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("தமிழ் மொழி", fontSize = 11.sp, color = if (state.language == AppLanguage.TAMIL) Color(0xFF00796B) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 2. THEME MODE SETTINGS CARD (தோற்றம்: வெளிர் / இருண்ட / கணினி)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF3E0),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isTamil) "பயன்பாட்டின் தோற்றம்" else "Theme & Appearance",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTamil) "வெளிர் அல்லது இருண்ட பயன்முறையை தேர்வு செய்க" else "Choose Light, Dark, or System mode",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Light Mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.themeMode == AppThemeMode.LIGHT) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (state.themeMode == AppThemeMode.LIGHT) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFA000)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.LightMode, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (isTamil) "வெளிர்" else "Light", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // Dark Mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.themeMode == AppThemeMode.DARK) Color(0xFF263238) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (state.themeMode == AppThemeMode.DARK) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF90CAF9)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setThemeMode(AppThemeMode.DARK) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.DarkMode, contentDescription = null, tint = Color(0xFF90CAF9), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (isTamil) "இருண்ட" else "Dark", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (state.themeMode == AppThemeMode.DARK) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // System Default
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.themeMode == AppThemeMode.SYSTEM) Color(0xFFE8EAF6) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (state.themeMode == AppThemeMode.SYSTEM) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF3F51B5)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.BrightnessAuto, contentDescription = null, tint = Color(0xFF3F51B5), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (isTamil) "கணினி" else "System", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        // 3. WAKE-UP ALARM RINGTONE SETTINGS (விழிப்பு மணி ஒலி)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEDE7F6),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = Color(0xFF673AB7),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isTamil) "விழிப்பு மணி ஒலி (Ringtone)" else "Wake-Up Alert Ringtone",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTamil) "சேருமிடம் வரும்போது ஒலிக்கும் மணி" else "Alarm tone played when bus reaches your destination",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewModel.availableRingtones.forEach { ringtone ->
                            val isSelected = state.selectedRingtoneId == ringtone.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFF3E5F5) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8E24AA)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setWakeUpRingtone(ringtone.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setWakeUpRingtone(ringtone.id) },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8E24AA))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = if (isTamil) ringtone.nameTa else ringtone.nameEn,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isTamil) ringtone.descriptionTa else ringtone.descriptionEn,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    FilledTonalButton(
                                        onClick = { viewModel.playRingtonePreview(ringtone.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isSelected) Color(0xFF8E24AA) else Color(0xFFEDE7F6),
                                            contentColor = if (isSelected) Color.White else Color(0xFF673AB7)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isTamil) "கேட்க" else "Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    // 3B. CUSTOM LOCAL AUDIO FROM DEVICE STORAGE (தனிப்பயன் உள்ளூர் பாடல்)
                    val isCustomSelected = state.selectedRingtoneId == "CUSTOM_LOCAL"
                    val hasCustomAudio = !state.customRingtoneUri.isNullOrBlank()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isTamil) "தனிப்பயன் பாடல் (Custom Device Audio)" else "Custom Device Ringtone / Song",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTamil)
                                    "உங்கள் மொபைல் சேமிப்பகத்திலிருந்து சொந்த பாடலை அலாரமாக வைக்கலாம்"
                                else
                                    "Pick any MP3 or audio from your phone storage as destination alarm",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustomSelected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isCustomSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2E7D32)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (hasCustomAudio) {
                                    viewModel.setWakeUpRingtone("CUSTOM_LOCAL")
                                } else {
                                    audioPickerLauncher.launch("audio/*")
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isCustomSelected,
                                        onClick = {
                                            if (hasCustomAudio) {
                                                viewModel.setWakeUpRingtone("CUSTOM_LOCAL")
                                            } else {
                                                audioPickerLauncher.launch("audio/*")
                                            }
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2E7D32))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = if (hasCustomAudio) (state.customRingtoneName ?: "Custom Audio File") else (if (isTamil) "போனிலிருந்து பாடல் தேர்வு செய்க" else "Select from Phone Storage"),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (hasCustomAudio) (if (isTamil) "உள்ளூர் ஆடியோ தேர்வு செய்யப்பட்டுள்ளது" else "Custom audio active as wake-up ringtone") else (if (isTamil) "MP3, AAC, WAV கோப்புகள்" else "MP3, AAC, WAV files supported"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (hasCustomAudio) {
                                    FilledTonalButton(
                                        onClick = { viewModel.playRingtonePreview("CUSTOM_LOCAL") },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isCustomSelected) Color(0xFF2E7D32) else Color(0xFFC8E6C9),
                                            contentColor = if (isCustomSelected) Color.White else Color(0xFF1B5E20)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isTamil) "கேட்க" else "Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { audioPickerLauncher.launch("audio/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasCustomAudio) (if (isTamil) "மாற்றுக (Change Song)" else "Change Song") else (if (isTamil) "பாடல் தேர்வு (Choose Audio)" else "Choose Audio"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (hasCustomAudio) {
                                    TextButton(
                                        onClick = { viewModel.clearCustomLocalRingtone() },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isTamil) "நீக்கு" else "Remove", color = Color(0xFFD32F2F), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. CORRIDOR & APP INFO CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (isTamil) "திட்ட விவரங்கள்" else "Corridor & Project Info",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isTamil)
                            "TNSTC ஈரோடு ⇄ பெருந்துறை மாநில நெடுஞ்சாலை 96 (SH-96) நேரடி ஸ்மார்ட் பயணிகள் வழிகாட்டி."
                        else
                            "TNSTC Erode ⇄ Perundurai State Highway 96 (SH-96) Smart Transit Passenger System.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isTamil) "பதிப்பு" else "App Version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v2.4.0 (TNSTC Smart)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                    }
                }
            }
        }
    }
}

