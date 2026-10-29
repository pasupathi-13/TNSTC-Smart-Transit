package com.kec.tnstc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kec.tnstc.data.AppLanguage
import com.kec.tnstc.ui.*

@Composable
fun MainNavigation(transitViewModel: TransitViewModel = viewModel()) {
    val state by transitViewModel.uiState.collectAsState()
    val backStack = remember { mutableStateListOf<ScreenRoute>(HomeScreenRoute) }
    val currentScreen = backStack.lastOrNull() ?: HomeScreenRoute

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.size - 1)
    }

    when (val screen = currentScreen) {
        is HomeScreenRoute -> {
            HomeScreen(
                viewModel = transitViewModel,
                onFindBusesClicked = { fromId, fromName, toId, toName ->
                    backStack.add(
                        AvailableBusesRoute(
                            fromStopId = fromId,
                            fromStopName = fromName,
                            toStopId = toId,
                            toStopName = toName
                        )
                    )
                },
                onTrackActiveAlarm = { busId, destName ->
                    val bus = transitViewModel.uiState.value.selectedBus
                    if (bus != null) {
                        backStack.add(
                            BusTrackingRoute(
                                busId = bus.busId,
                                busNumber = bus.busNumber,
                                fromStopName = transitViewModel.uiState.value.selectedBoardingStop?.name ?: "Boarding Stop",
                                toStopName = destName,
                                etaMinutes = bus.etaMinutes,
                                distanceKm = bus.distanceKm,
                                availableSeats = bus.availableSeats,
                                totalSeats = bus.totalSeats,
                                crowdStatus = bus.crowdStatus
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        is AvailableBusesRoute -> {
            BusSelectionScreen(
                viewModel = transitViewModel,
                fromStopName = screen.fromStopName,
                toStopName = screen.toStopName,
                onBackClicked = {
                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                },
                onBusSelected = { bus ->
                    transitViewModel.selectBusForTracking(bus)
                    backStack.add(
                        BusTrackingRoute(
                            busId = bus.busId,
                            busNumber = bus.busNumber,
                            fromStopName = screen.fromStopName,
                            toStopName = screen.toStopName,
                            etaMinutes = bus.etaMinutes,
                            distanceKm = bus.distanceKm,
                            availableSeats = bus.availableSeats,
                            totalSeats = bus.totalSeats,
                            crowdStatus = bus.crowdStatus
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        is BusTrackingRoute -> {
            BusTrackingScreen(
                viewModel = transitViewModel,
                busId = screen.busId,
                busNumber = screen.busNumber,
                fromStopName = screen.fromStopName,
                toStopName = screen.toStopName,
                etaMinutes = screen.etaMinutes,
                distanceKm = screen.distanceKm,
                availableSeats = screen.availableSeats,
                totalSeats = screen.totalSeats,
                crowdStatus = screen.crowdStatus,
                onBackClicked = {
                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // High-Priority Destination Proximity Alarm Dialog (Global across all screens)
    if (state.alarmTriggered) {
        val destName = state.selectedDestinationStop?.name
            ?: state.activeAlarm?.destinationStopName
            ?: "Destination Stop"
        val distKm = state.activeAlarm?.alertDistanceKm ?: 0.2
        DestinationAlarmDialog(
            destStopName = destName,
            distanceKm = distKm,
            isTamil = state.language == AppLanguage.TAMIL,
            onDismiss = { transitViewModel.dismissAlarmTriggeredDialog() }
        )
    }
}
