package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*

@Composable
fun HomeScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()
    val tasks by viewModel.currentServiceLogs.collectAsState()
    val allTasks by produceState(initialValue = emptyList<com.motogeek.telemetry.data.local.MaintenanceTaskEntity>(), activeBike) {
        activeBike?.let {
            viewModel.repository.getMaintenanceTasksForMotorcycleFlow(it.id).collect { t ->
                value = t
            }
        }
    }
    val serviceLogs by viewModel.currentServiceLogs.collectAsState()
    val fuelStats by viewModel.fuelEconomyStats.collectAsState()
    val tcoStats by viewModel.tcoStats.collectAsState()
    val rides by viewModel.currentRideSessions.collectAsState()
    val telemetryState by viewModel.liveTelemetryState.collectAsState()

    // Calculate Maintenance Health
    val evaluatedTasks = remember(activeBike, allTasks, serviceLogs) {
        if (activeBike != null) {
            viewModel.evaluator.evaluate(activeBike!!, allTasks, serviceLogs)
        } else emptyList()
    }

    val overdueCount = evaluatedTasks.count { it.status == TaskStatus.OVERDUE }
    val dueSoonCount = evaluatedTasks.count { it.status == TaskStatus.DUE_SOON }
    val okCount = evaluatedTasks.count { it.status == TaskStatus.OK }
    val totalCount = evaluatedTasks.size

    val healthPercent = if (totalCount > 0) ((okCount.toDouble() / totalCount) * 100).toInt() else 100
    val healthColor = when {
        overdueCount > 0 -> RedlineCrimson
        dueSoonCount > 0 -> WarningGold
        else -> ApexEmeraldGreen
    }
    val healthText = when {
        overdueCount > 0 -> "$overdueCount TASK(S) OVERDUE • SERVICE REQUIRED"
        dueSoonCount > 0 -> "$dueSoonCount TASK(S) DUE SOON • HEALTH: $healthPercent%"
        else -> "SERVICE HEALTH: $healthPercent% • ALL SYSTEMS OK"
    }

    // Peak lean angles across all recorded rides
    val maxLeanLeft = rides.maxOfOrNull { it.maxLeanLeftDeg } ?: 0.0
    val maxLeanRight = rides.maxOfOrNull { it.maxLeanRightDeg } ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonObsidianBg)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Top Telemetry & Bike Status Banner
        ActiveBikeDropdownCard(
            activeBike = activeBike,
            allBikes = allBikes,
            onBikeSelected = { bikeId -> viewModel.selectActiveMotorcycle(bikeId) },
            onAddNewBike = { navController.navigate("bike_info") },
            onSyncOdometer = { newKm -> viewModel.updateActiveBikeOdometer(newKm) },
            showOdometer = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Live Maintenance Health Status Bar (Clickable -> Maintenance)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("maintenance") },
            shape = RoundedCornerShape(8.dp),
            color = healthColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, healthColor.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(healthColor, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = healthText,
                        color = healthColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Maintenance",
                    tint = healthColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 3-Column Live Mini-KPI Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniKpiCard(
                label = "AVG FUEL",
                value = if (fuelStats.avgLitersPer100Km > 0) String.format("%.1f", fuelStats.avgLitersPer100Km) else "—",
                unit = "L/100",
                valueColor = TelemetryIceCyan,
                modifier = Modifier.weight(1f)
            )
            MiniKpiCard(
                label = "PEAK LEAN",
                value = "${maxLeanLeft.toInt()}°/${maxLeanRight.toInt()}°",
                unit = "L/R",
                valueColor = RacingAmberPrimary,
                modifier = Modifier.weight(1f)
            )
            MiniKpiCard(
                label = "COST / KM",
                value = if (tcoStats.costPerKm > 0) String.format("%.2f", tcoStats.costPerKm) else "—",
                unit = "${tcoStats.currency}/KM",
                valueColor = ApexEmeraldGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Asymmetric Action Bento Grid
        // Primary Wide Hero Card: LAUNCH LIVE COCKPIT
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
            border = BorderStroke(1.5.dp, RacingAmberPrimary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIVE TELEMETRY COCKPIT",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (telemetryState.isRiding) "SESSION ACTIVE • RECORDING" else "READY FOR GPS & IMU TRACKING",
                            color = if (telemetryState.isRiding) ApexEmeraldGreen else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = (if (telemetryState.isRiding) ApexEmeraldGreen else RacingAmberPrimary).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (telemetryState.isRiding) ApexEmeraldGreen else RacingAmberPrimary)
                    ) {
                        Text(
                            text = if (telemetryState.isRiding) "100 HZ LIVE" else "STANDBY",
                            color = if (telemetryState.isRiding) ApexEmeraldGreen else RacingAmberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { navController.navigate("cockpit") },
                        colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Launch Cockpit",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LAUNCH COCKPIT",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.startDemoMode()
                            navController.navigate("cockpit")
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "SIMULATE",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Log Row (3 buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickLogButton(
                label = "+ Fuel",
                icon = Icons.Default.LocalGasStation,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("fuel") }

            QuickLogButton(
                label = "+ Service",
                icon = Icons.Default.Handyman,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("maintenance") }

            QuickLogButton(
                label = "+ Part",
                icon = Icons.Default.Build,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("parts") }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Management Grid (4 cards, 2x2)
        Text(
            text = "GARAGE & TELEMETRY MODULES",
            color = Color(0xFF6B7A99),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoGridCard(
                title = "Analytics & TCO",
                subtitle = "Cost & ride insights",
                icon = Icons.Default.Dashboard,
                iconTint = TelemetryIceCyan,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("dashboard") }

            BentoGridCard(
                title = "AI Manual & Backup",
                subtitle = "JSON garage export",
                icon = Icons.Default.Psychology,
                iconTint = RacingAmberPrimary,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("offline_backup") }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoGridCard(
                title = "Reminders & Alerts",
                subtitle = "${overdueCount + dueSoonCount} alerts pending",
                icon = Icons.Default.Notifications,
                iconTint = if (overdueCount > 0) RedlineCrimson else WarningGold,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("reminders") }

            BentoGridCard(
                title = "Specs & Bolt Torques",
                subtitle = "Factory torque tables",
                icon = Icons.Default.TwoWheeler,
                iconTint = ApexEmeraldGreen,
                modifier = Modifier.weight(1f)
            ) { navController.navigate("bike_info") }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun MiniKpiCard(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceDark,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            SegmentedDigitalText(
                text = value,
                unit = unit,
                color = valueColor,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun QuickLogButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = RacingAmberPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun BentoGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = TftSurfaceDark,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(14.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
