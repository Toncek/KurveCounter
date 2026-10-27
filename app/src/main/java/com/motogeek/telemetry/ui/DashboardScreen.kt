package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()
    val tcoStats by viewModel.tcoStats.collectAsState()
    val fuelStats by viewModel.fuelEconomyStats.collectAsState()
    val rides by viewModel.currentRideSessions.collectAsState()

    // Aggregated telemetry stats
    val totalRidesCount = rides.size
    val totalTrackedDistanceKm = rides.sumOf { it.distanceKm }
    val totalLeftCorners = rides.sumOf { it.leftCornersCount }
    val totalRightCorners = rides.sumOf { it.rightCornersCount }
    val allTimePeakLeanLeft = rides.maxOfOrNull { it.maxLeanLeftDeg } ?: 0.0
    val allTimePeakLeanRight = rides.maxOfOrNull { it.maxLeanRightDeg } ?: 0.0
    val allTimePeakBrakingG = rides.maxOfOrNull { it.maxBrakingG } ?: 0.0
    val allTimePeakLateralG = rides.maxOfOrNull { it.maxLateralG } ?: 0.0
    val avgTrailBrakingScore = if (rides.isNotEmpty()) {
        (rides.sumOf { it.trailBrakingScorePct } / rides.size).takeIf { it.isFinite() } ?: 0.0
    } else 0.0

    val dominantStyle = remember(rides) {
        if (rides.isEmpty()) "TOURER"
        else {
            rides.groupBy { it.ridingStyleBadge }
                .maxByOrNull { it.value.size }?.key ?: "TECHNICIAN"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics & TCO Dashboard",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("home")
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RacingAmberPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CarbonObsidianBg)
            )
        },
        containerColor = CarbonObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            ActiveBikeDropdownCard(
                activeBike = activeBike,
                allBikes = allBikes,
                onBikeSelected = { viewModel.selectActiveMotorcycle(it) },
                onAddNewBike = { navController.navigate("bike_info") },
                onSyncOdometer = { viewModel.updateActiveBikeOdometer(it) },
                showOdometer = true
            )

            if (activeBike == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No motorcycle selected.", color = Color.Gray)
                }
                return@Scaffold
            }

            // 1. Financial & Total Cost of Ownership (TCO) Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TOTAL COST OF OWNERSHIP (TCO)",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("ALL-IN TOTAL", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            SegmentedDigitalText(
                                text = String.format("%,d", tcoStats.totalCost.toInt()),
                                unit = tcoStats.currency,
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("COST PER KM", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            SegmentedDigitalText(
                                text = String.format("%.2f", tcoStats.costPerKm),
                                unit = "${tcoStats.currency}/KM",
                                color = RacingAmberPrimary,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Visual Horizontal Stacked Bar
                    val operatingSpend = tcoStats.totalFuelCost + tcoStats.totalServiceCost + tcoStats.totalPartsCost
                    val baseTotal = if (operatingSpend > 0) operatingSpend else 1.0
                    val fuelWeight = (tcoStats.totalFuelCost / baseTotal).toFloat().coerceIn(0f, 1f)
                    val serviceWeight = (tcoStats.totalServiceCost / baseTotal).toFloat().coerceIn(0f, 1f)
                    val partsWeight = (tcoStats.totalPartsCost / baseTotal).toFloat().coerceIn(0f, 1f)

                    if (operatingSpend > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(TftSurfaceElevated, RoundedCornerShape(6.dp))
                        ) {
                            if (fuelWeight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(fuelWeight.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(TelemetryIceCyan, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                )
                            }
                            if (serviceWeight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(serviceWeight.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(ApexEmeraldGreen)
                                )
                            }
                            if (partsWeight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(partsWeight.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(RacingAmberPrimary, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(TitaniumBorder, RoundedCornerShape(4.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // TCO Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TcoLegendItem("Fuel", "${tcoStats.totalFuelCost.toInt()} ${tcoStats.currency}", TelemetryIceCyan)
                        TcoLegendItem("Service", "${tcoStats.totalServiceCost.toInt()} ${tcoStats.currency}", ApexEmeraldGreen)
                        TcoLegendItem("Parts", "${tcoStats.totalPartsCost.toInt()} ${tcoStats.currency}", RacingAmberPrimary)
                        TcoLegendItem("Bike Price", "${tcoStats.purchasePrice.toInt()} ${tcoStats.currency}", Color.LightGray)
                    }
                }
            }

            // 2. Rider Telemetry Performance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RIDER TELEMETRY PERFORMANCE",
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        // Riding Style Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RacingAmberPrimary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, RacingAmberPrimary)
                        ) {
                            Text(
                                text = dominantStyle,
                                color = RacingAmberPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4-Column Grid of Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill("PEAK LEAN L/R", "${allTimePeakLeanLeft.toInt()}° / ${allTimePeakLeanRight.toInt()}°", RacingAmberPrimary, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill("CORNERS L/R", "$totalLeftCorners L / $totalRightCorners R", TelemetryIceCyan, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill("PEAK BRAKING", String.format("%.2f G", allTimePeakBrakingG), RedlineCrimson, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill("PEAK LATERAL", String.format("%.2f G", allTimePeakLateralG), ApexEmeraldGreen, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill("TRACKED DISTANCE", "${String.format("%.1f", totalTrackedDistanceKm)} KM", Color.White, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill("TOTAL RIDES", "$totalRidesCount SESSIONS", TelemetryIceCyan, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { navController.navigate("history") },
                        colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Route, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Ride History & Trajectory Heatmaps", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TcoLegendItem(label: String, amount: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(amount, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(3.dp))
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
