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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.motogeek.telemetry.service.TelemetryForegroundService
import com.motogeek.telemetry.ui.*
import com.motogeek.telemetry.ui.theme.MotoGeekTelemetryTheme

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

                val items = listOf(
                    Triple("cockpit", "Cockpit", Icons.Filled.Home),
                    Triple("history", "History", Icons.Filled.List),
                    Triple("maintenance", "Maintenance", Icons.Filled.Build),
                    Triple("garage", "Garage", Icons.Filled.Settings)
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { (route, label, icon) ->
                                NavigationBarItem(
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                                    onClick = {
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
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController, startDestination = "cockpit", Modifier.padding(innerPadding)) {
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
