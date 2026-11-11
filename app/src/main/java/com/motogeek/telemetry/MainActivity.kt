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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.motogeek.telemetry.service.TelemetryForegroundService
import com.motogeek.telemetry.ui.*
import com.motogeek.telemetry.ui.theme.AppLang
import com.motogeek.telemetry.ui.theme.LocalAppLang
import com.motogeek.telemetry.ui.theme.MotoGeekTelemetryTheme
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed
import com.motogeek.telemetry.ui.theme.tr
import com.motogeek.telemetry.service.TelemetryPreferences
import com.motogeek.telemetry.ui.components.MotoGeekDrawerSheet
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            checkAndStartMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()
        checkAndStartMonitoring()

        setContent {
            val appLang by viewModel.appLanguage.collectAsState()
            CompositionLocalProvider(LocalAppLang provides appLang) {
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
                            title = { Text(tr("Welcome to MotoGeek Telemetry", "Vitajte v MotoGeek Telemetry")) },
                            text = {
                                Text(
                                    tr(
                                        "We have seeded your garage with a Universal Motorcycle Template.\n\nGo to the Garage tab to Edit your bike details, apply one of the 6 Built-In Templates, or paste a custom JSON schedule.",
                                        "Vašu garáž sme predvyplnili univerzálnou šablónou motocykla.\n\nV sekcii Garáž môžete upraviť údaje o motorke, použiť jednu zo 6 vstavaných šablón alebo vložiť vlastný JSON servisný plán."
                                    )
                                )
                            },
                            confirmButton = {
                                Button(onClick = { viewModel.dismissOnboarding() }) {
                                    Text(tr("Let's Ride!", "Poďme jazdiť!"))
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
                                    navController.navigate("bike_info")
                                },
                                onSyncOdometer = { newKm ->
                                    viewModel.updateActiveBikeOdometer(newKm)
                                },
                                currentLanguage = appLang,
                                onLanguageToggle = { viewModel.toggleAppLanguage() },
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
                                if (currentRoute == "home" || currentRoute == null || currentRoute == "cockpit" || currentRoute == "history") {
                                    @OptIn(ExperimentalMaterial3Api::class)
                                    TopAppBar(
                                        title = {
                                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Text(
                                                    text = "KURVE",
                                                    color = com.motogeek.telemetry.ui.theme.RacingAmberPrimary,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    letterSpacing = 1.sp
                                                )
                                                val subTitle = when (currentRoute) {
                                                    "cockpit" -> tr(" // COCKPIT", " // KOKPIT")
                                                    "history" -> tr(" // HISTORY", " // HISTÓRIA")
                                                    else -> " // TFT"
                                                }
                                                Text(
                                                    text = subTitle,
                                                    color = Color.White,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                        },
                                        navigationIcon = {
                                            if (currentRoute == "cockpit" || currentRoute == "history") {
                                                IconButton(onClick = { navController.navigate("home") { popUpTo("home") { inclusive = false } } }) {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = com.motogeek.telemetry.ui.theme.RacingAmberPrimary)
                                                }
                                            } else {
                                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = com.motogeek.telemetry.ui.theme.RacingAmberPrimary)
                                                }
                                            }
                                        },
                                        actions = {
                                            TextButton(onClick = { viewModel.toggleAppLanguage() }) {
                                                Text(
                                                    text = if (appLang == AppLang.EN) "EN ▸ SK" else "SK ▸ EN",
                                                    color = com.motogeek.telemetry.ui.theme.TelemetryIceCyan,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = com.motogeek.telemetry.ui.theme.CarbonObsidianBg
                                        )
                                    )
                                }
                            },
                            bottomBar = {
                                val bottomNavRoutes = listOf(
                                    Triple("home", tr("Home", "Domov"), Icons.Default.Home),
                                    Triple("cockpit", tr("Cockpit", "Kokpit"), Icons.Default.Speed),
                                    Triple("maintenance", tr("Service", "Servis"), Icons.Default.Build),
                                    Triple("bike_info", tr("Bike/Specs", "Motorka"), Icons.Default.TwoWheeler),
                                    Triple("dashboard", tr("Analytics", "Analýza"), Icons.Default.Analytics)
                                )
                            NavigationBar(
                                containerColor = com.motogeek.telemetry.ui.theme.TftSurfaceDark,
                                contentColor = Color.White
                            ) {
                                bottomNavRoutes.forEach { (route, label, icon) ->
                                    val selected = currentRoute == route || (route == "bike_info" && currentRoute == "garage")
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label, fontSize = 10.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = com.motogeek.telemetry.ui.theme.RacingAmberPrimary,
                                            selectedTextColor = com.motogeek.telemetry.ui.theme.RacingAmberPrimary,
                                            indicatorColor = com.motogeek.telemetry.ui.theme.RacingAmberPrimary.copy(alpha = 0.15f),
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
                            composable("home") { HomeScreen(viewModel, navController) }
                            composable("cockpit") {
                                CockpitScreen(
                                    viewModel = viewModel,
                                    onStartRide = { startRideService(activeBike?.id) },
                                    onStopRide = { stopRideService() }
                                )
                            }
                            composable("history") { TelemetryHistoryScreen(viewModel) }
                            composable("maintenance") { MaintenanceServiceScreen(viewModel, navController) }
                            composable("garage") { BikeInfoScreen(viewModel, navController) }
                            composable("settings") { OfflineBackupScreen(viewModel, navController) }
                            composable("dashboard") { DashboardScreen(viewModel, navController) }
                            composable("export") { BikeDataExportScreen(viewModel, navController) }
                            composable("about") { AboutAndDiagnosticsScreen(viewModel, navController) }
                            composable("bike_info") { BikeInfoScreen(viewModel, navController) }
                            composable("fuel") { FuelScreen(viewModel, navController) }
                            composable("parts") { PartsScreen(viewModel, navController) }
                            composable("reminders") { RemindersScreen(viewModel, navController) }
                            composable("logs") { LogsScreen(viewModel, navController) }
                            composable("offline_backup") { OfflineBackupScreen(viewModel, navController) }
                            composable("cloud_backup") { OfflineBackupScreen(viewModel, navController) }
                        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun checkAndStartMonitoring() {
        val prefs = TelemetryPreferences(this)
        if (prefs.autoStartAbove5Kmh) {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val intent = Intent(this, TelemetryForegroundService::class.java).apply {
                    action = TelemetryForegroundService.ACTION_START_MONITORING
                }
                ContextCompat.startForegroundService(this, intent)
            }
        }
    }

    private fun startRideService(bikeId: String? = null) {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            requestRequiredPermissions()
            return
        }

        val intent = Intent(this, TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_START_RIDE
            putExtra(TelemetryForegroundService.EXTRA_MOTORCYCLE_ID, bikeId ?: "default_bike")
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
