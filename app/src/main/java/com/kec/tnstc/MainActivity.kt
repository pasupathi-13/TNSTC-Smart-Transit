package com.kec.tnstc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kec.tnstc.data.AppLanguage
import com.kec.tnstc.ui.TransitViewModel
import com.kec.tnstc.ui.theme.TNSTCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val transitViewModel: TransitViewModel = viewModel()
            val state by transitViewModel.uiState.collectAsState()
            val context = LocalContext.current
            val lifecycleOwner = this@MainActivity
            var showGpsPromptDialog by remember { mutableStateOf(false) }

            // 1. Ask for Location Permission on App Launch
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (fineGranted || coarseGranted) {
                    transitViewModel.startUserLocationUpdates()
                }
            }

            // Auto-refresh location whenever app is resumed
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) {
                            transitViewModel.startUserLocationUpdates()
                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            val isGpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                                               lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                            if (isGpsEnabled) {
                                showGpsPromptDialog = false
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    transitViewModel.startUserLocationUpdates()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                // 2. Check if Location / GPS provider is enabled; show in-app popup dialog if off
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val isGpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                                   lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                if (!isGpsEnabled) {
                    showGpsPromptDialog = true
                }
            }

            TNSTCTheme(appThemeMode = state.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(transitViewModel = transitViewModel)

                    // In-app GPS Prompt Dialog (does not force-launch Settings without user click)
                    if (showGpsPromptDialog) {
                        val isTamil = state.language == AppLanguage.TAMIL
                        AlertDialog(
                            onDismissRequest = { showGpsPromptDialog = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = if (isTamil) "இருப்பிடத்தை இயக்கவும் (Turn On GPS)" else "Turn On Device Location",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            },
                            text = {
                                Text(
                                    text = if (isTamil)
                                        "நேரலை பேருந்து கண்காணிப்பு, வரைபடத்தில் நீங்கள் இருக்கும் இடம் மற்றும் 200m சேருமிட விழிப்பு அலாரத்திற்கு உங்கள் போனிலுள்ள GPS இருப்பிட சேவையை இயக்கவும்."
                                    else
                                        "To view your live passenger location on the map and receive the 200m destination arrival alarm, please turn on your device's location service.",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showGpsPromptDialog = false
                                        try {
                                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00897B)
                                    )
                                ) {
                                    Text(if (isTamil) "இயக்கு (Turn On)" else "Turn On")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showGpsPromptDialog = false }
                                ) {
                                    Text(if (isTamil) "பிறகு (Later)" else "Later")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
