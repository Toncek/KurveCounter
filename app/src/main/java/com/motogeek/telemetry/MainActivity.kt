package com.motogeek.telemetry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.motogeek.telemetry.service.TelemetryForegroundService
import com.motogeek.telemetry.ui.*
import com.motogeek.telemetry.ui.theme.MotoGeekTelemetryTheme
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed
import com.motogeek.telemetry.ui.components.MotoGeekDrawerSheet
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission responses if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        setContent {
            MotoGeekTelemetryTheme {
                val navController = rememberNavController()
                val showOnboarding by viewModel.showOnboarding.collectAsState()
                val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)
                val allBikes by viewModel.repository.getAllMotorcycles().collectAsState(initial = emptyList())

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                if (showOnboarding) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissOnboarding() },
                        title = { Text("Welcome to MotoGeek Telemetry") },
                        text = { Text("We have seeded your garage with a Universal Motorcycle Template.\n\nGo to the Garage tab to Edit your bike details, apply one of the 6 Built-In Templates, or paste a custom JSON schedule.") },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissOnboarding() }) {
                                Text("Let's Ride!")
                            }
                        }
                    )
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        MotoGeekDrawerSheet(
                            activeBike = activeBike,
                            allBikes = allBikes,
                            onBikeSelected = { bikeId ->
                                scope.launch { viewModel.repository.selectActiveMotorcycle(bikeId) }
                            },
                            onAddNewBike = {
                                navController.navigate("garage")
                            },
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                scope.launch { drawerState.close() }
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        topBar = {
                            @OptIn(ExperimentalMaterial3Api::class)
                            TopAppBar(
                                title = { Text("MotoGeek", color = Color.White) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = CrimsonHeaderRed
                                )
                            )
                        }
                    ) { innerPadding ->
                        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
                            composable("home") { HomeScreen(viewModel, navController) }
                            composable("cockpit") {
                                CockpitScreen(
                                    viewModel = viewModel,
                                    onStartRide = { startRideService() },
                                    onStopRide = { stopRideService() }
                                )
                            }
                            composable("history") { TelemetryHistoryScreen(viewModel) }
                            composable("maintenance") { MaintenanceScreen(viewModel) }
                            composable("garage") { GarageSettingsScreen(viewModel) }
                            composable("dashboard") { Text("Dashboard Placeholder") }
                            composable("bike_info") { BikeInfoScreen(viewModel, navController) }
                            composable("fuel") { FuelScreen(viewModel, navController) }
                            composable("parts") { PartsScreen(viewModel, navController) }
                            composable("reminders") { RemindersScreen(viewModel, navController) }
                            composable("logs") { LogsScreen(viewModel, navController) }
                            composable("offline_backup") { OfflineBackupScreen(viewModel, navController) }
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun startRideService() {
        val intent = Intent(this, TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_START_RIDE
            putExtra(TelemetryForegroundService.EXTRA_MOTORCYCLE_ID, "default_bike") // Demo usage
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRideService() {
        val intent = Intent(this, TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_STOP_RIDE
        }
        startService(intent)
    }
}
