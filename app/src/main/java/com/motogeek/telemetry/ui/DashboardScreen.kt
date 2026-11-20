package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()
    val tcoStats by viewModel.tcoStats.collectAsState()
    val fuelStats by viewModel.fuelEconomyStats.collectAsState()
    val riderAnalytics by viewModel.riderAnalytics.collectAsState()
    val rides by viewModel.currentRideSessions.collectAsState()

    val dominantStyle = remember(rides) {
        if (rides.isEmpty()) "TOURER"
        else {
            rides.groupBy { it.ridingStyleBadge }
                .maxByOrNull { it.value.size }?.key ?: "TECHNICIAN"
        }
    }

    val localizedStyleBadge = when (dominantStyle.uppercase()) {
        "TOURER" -> tr("TOURER", "TURISTA")
        "TECHNICIAN" -> tr("TECHNICIAN", "TECHNIK")
        "RACER" -> tr("RACER", "PRETEKÁR")
        else -> dominantStyle
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("Analytics & TCO Dashboard", "Prehľad analytiky a TCO"),
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "Späť"),
                            tint = RacingAmberPrimary
                        )
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
                onAddNewBike = { viewModel.startNewBikeTutorial() },
                onSyncOdometer = { viewModel.updateActiveBikeOdometer(it) },
                showOdometer = true
            )

            if (activeBike == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("No motorcycle selected.", "Nie je vybraný žiadny motocykel."),
                        color = Color.Gray
                    )
                }
                return@Scaffold
            }

            // =========================================================================
            // 1. Enhanced TCO Financial Card
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = tr("TOTAL COST OF OWNERSHIP (TCO)", "CELKOVÉ NÁKLADY VLASTNÍCTVA (TCO)"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 1: Total Spent & Cost per Km
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = tr("TOTAL SPENT", "CELKOVO VYNALOŽENÉ"),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            SegmentedDigitalText(
                                text = String.format(Locale.US, "%,d", tcoStats.totalCost.toInt()),
                                unit = tcoStats.currency,
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("COST PER KM", "NÁKLADY NA KM"),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            SegmentedDigitalText(
                                text = String.format(Locale.US, "%.2f", tcoStats.costPerKm),
                                unit = "${tcoStats.currency}/KM",
                                color = RacingAmberPrimary,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Cost / Month & Owned Distance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = tr("COST / MONTH", "NÁKLADY / MESIAC"),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            SegmentedDigitalText(
                                text = String.format(Locale.US, "%.1f", tcoStats.costPerMonth),
                                unit = "${tcoStats.currency}/mo",
                                color = TelemetryIceCyan,
                                fontSize = 16.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("OWNED DISTANCE", "NAJAZDENÉ VLASTNÍKOM"),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            SegmentedDigitalText(
                                text = String.format(Locale.US, "%,d", tcoStats.ownedDistanceKm.toInt()),
                                unit = "KM",
                                color = ApexEmeraldGreen,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Visual Horizontal Stacked Bar for Operating Spend
                    val operatingSpend = tcoStats.totalOperatingCost
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // TCO Legend with percentages
                    val fuelPct = if (operatingSpend > 0) ((tcoStats.totalFuelCost / operatingSpend) * 100).toInt() else 0
                    val servicePct = if (operatingSpend > 0) ((tcoStats.totalServiceCost / operatingSpend) * 100).toInt() else 0
                    val partsPct = if (operatingSpend > 0) ((tcoStats.totalPartsCost / operatingSpend) * 100).toInt() else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TcoLegendItem(
                            label = tr("Fuel", "Palivo"),
                            amount = "${tcoStats.totalFuelCost.toInt()} ${tcoStats.currency}",
                            subtitle = if (operatingSpend > 0) "$fuelPct%" else null,
                            color = TelemetryIceCyan
                        )
                        TcoLegendItem(
                            label = tr("Service", "Servis"),
                            amount = "${tcoStats.totalServiceCost.toInt()} ${tcoStats.currency}",
                            subtitle = if (operatingSpend > 0) "$servicePct%" else null,
                            color = ApexEmeraldGreen
                        )
                        TcoLegendItem(
                            label = tr("Parts", "Diely"),
                            amount = "${tcoStats.totalPartsCost.toInt()} ${tcoStats.currency}",
                            subtitle = if (operatingSpend > 0) "$partsPct%" else null,
                            color = RacingAmberPrimary
                        )
                        TcoLegendItem(
                            label = tr("Bike Price", "Kúpna cena"),
                            amount = "${tcoStats.purchasePrice.toInt()} ${tcoStats.currency}",
                            subtitle = null,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // =========================================================================
            // 2. Fuel Economy Card
            // =========================================================================
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
                            text = tr("FUEL ECONOMY & CONSUMPTION", "SPOTREBA A NÁKLADY NA PALIVO"),
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TelemetryIceCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${fuelStats.fillCount} ${tr("FILLS", "TANKOVANÍ")}",
                                color = TelemetryIceCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (fuelStats.fillCount < 2 || fuelStats.avgLitersPer100Km <= 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TftSurfaceElevated,
                            border = BorderStroke(1.dp, TitaniumBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = tr(
                                        "Log at least 2 full tank fill-ups to calculate real fuel consumption.",
                                        "Zaznamenajte aspoň 2 tankovania do plna pre presný výpočet spotreby."
                                    ),
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${tr("Total Liters", "Celkovo litrov")}: ${String.format(Locale.US, "%.1f", fuelStats.totalLiters)} L",
                                        color = TelemetryIceCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${tr("Total Spent", "Celkovo suma")}: ${String.format(Locale.US, "%.0f", fuelStats.totalFuelCost)} ${tcoStats.currency}",
                                        color = RacingAmberPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // Metrics Row 1: Avg & Best L/100km
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricPill(
                                label = tr("AVG CONSUMPTION", "PRIEMERNÁ SPOTREBA"),
                                value = "${String.format(Locale.US, "%.2f", fuelStats.avgLitersPer100Km)} L/100",
                                color = TelemetryIceCyan,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MetricPill(
                                label = tr("BEST CONSUMPTION", "NAJLEPŠIA SPOTREBA"),
                                value = "${String.format(Locale.US, "%.2f", fuelStats.bestLitersPer100Km)} L/100",
                                color = ApexEmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metrics Row 2: Efficiency (km/L) & Avg Price / L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricPill(
                                label = tr("EFFICIENCY", "EFEKTIVITA"),
                                value = "${String.format(Locale.US, "%.1f", fuelStats.avgKmPerLiter)} KM/L",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MetricPill(
                                label = tr("AVG PRICE / L", "PRIEM. CENA / L"),
                                value = "${String.format(Locale.US, "%.3f", fuelStats.avgPricePerLiter)} ${tcoStats.currency}",
                                color = RacingAmberPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metrics Row 3: Total Liters, Tracked Span & Fills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricPill(
                                label = tr("TOTAL LITERS", "CELKOVO LITROV"),
                                value = "${String.format(Locale.US, "%.1f", fuelStats.totalLiters)} L",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MetricPill(
                                label = tr("TRACKED SPAN", "SLEDOVANÝ ÚSEK"),
                                value = "${fuelStats.trackedSpanKm.toInt()} KM",
                                color = TelemetryIceCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 3. Rider Telemetry Performance Card
            // =========================================================================
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
                            text = tr("RIDER TELEMETRY PERFORMANCE", "TELEMETRIA A VÝKON JAZDCA"),
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
                                text = localizedStyleBadge,
                                color = RacingAmberPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val saddleHours = riderAnalytics.totalDurationMs / (1000 * 60 * 60)
                    val saddleMins = (riderAnalytics.totalDurationMs / (1000 * 60)) % 60
                    val saddleTimeFormatted = "${saddleHours}h ${saddleMins}m"

                    // Grid Row 1: Distance & Saddle Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(
                            label = tr("DISTANCE", "VZDIALENOSŤ"),
                            value = "${String.format(Locale.US, "%.1f", riderAnalytics.totalDistanceKm)} KM",
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill(
                            label = tr("SADDLE TIME", "ČAS V SEDLE"),
                            value = saddleTimeFormatted,
                            color = TelemetryIceCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid Row 2: Avg Speed & Max Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(
                            label = tr("AVG SPEED", "PRIEM. RÝCHLOSŤ"),
                            value = "${riderAnalytics.avgSpeedWeightedKmh.toInt()} KM/H",
                            color = ApexEmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill(
                            label = tr("MAX SPEED", "MAX. RÝCHLOSŤ"),
                            value = "${riderAnalytics.maxSpeedKmh.toInt()} KM/H",
                            color = RedlineCrimson,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid Row 3: Elevation Gain & Peak Lean L/R
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(
                            label = tr("ELEVATION GAIN", "PREVÝŠENIE"),
                            value = "${riderAnalytics.totalElevationGainM.toInt()} M",
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill(
                            label = tr("PEAK LEAN L/R", "MAX. NÁKLON Ľ/P"),
                            value = "${riderAnalytics.peakLeanLeftDeg.toInt()}° / ${riderAnalytics.peakLeanRightDeg.toInt()}°",
                            color = RacingAmberPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid Row 4: Corners L/R & Corner Score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(
                            label = tr("CORNERS L/R", "ZÁKRUTY Ľ/P"),
                            value = "${riderAnalytics.totalLeftCorners} L / ${riderAnalytics.totalRightCorners} R",
                            color = TelemetryIceCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill(
                            label = tr("AVG CORNER SCORE", "PRIEM. SKÓRE ZÁKRUT"),
                            value = "${riderAnalytics.avgCornerScore.toInt()} / 100",
                            color = ApexEmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Corner Symmetry Visual Bar
                    val sumCorners = riderAnalytics.totalLeftCorners + riderAnalytics.totalRightCorners
                    val leftRatio = if (sumCorners > 0) {
                        (riderAnalytics.totalLeftCorners.toFloat() / sumCorners).coerceIn(0.05f, 0.95f)
                    } else 0.5f
                    val leftPct = if (sumCorners > 0) ((riderAnalytics.totalLeftCorners.toDouble() / sumCorners) * 100).toInt() else 50
                    val rightPct = 100 - leftPct

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TftSurfaceElevated, RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, TitaniumBorder), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tr("CORNER SYMMETRY", "SYMETRIA ZÁKRUT"),
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${riderAnalytics.totalLeftCorners} Ľ ($leftPct%) • ${riderAnalytics.totalRightCorners} P ($rightPct%)",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(CarbonObsidianBg, RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(leftRatio)
                                    .fillMaxHeight()
                                    .background(TelemetryIceCyan, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f - leftRatio)
                                    .fillMaxHeight()
                                    .background(RacingAmberPrimary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid Row 5: Trail Braking & Max Turn-In Rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(
                            label = tr("TRAIL BRAKING", "TRAIL BRAKING"),
                            value = "${riderAnalytics.avgTrailBrakingPct.toInt()}%",
                            color = TelemetryIceCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricPill(
                            label = tr("MAX TURN-IN", "MAX. NÁJAZD"),
                            value = "${riderAnalytics.peakTurnInRateDegPerSec.toInt()}°/s",
                            color = RacingAmberPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid Row 6: 3 Acceleration / G Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(
                            label = tr("PEAK BRAKING", "MAX. BRZDENIE"),
                            value = String.format(Locale.US, "%.2f G", riderAnalytics.peakBrakingG),
                            color = RedlineCrimson,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricPill(
                            label = tr("PEAK LATERAL", "MAX. BOČNÉ G"),
                            value = String.format(Locale.US, "%.2f G", riderAnalytics.peakLateralG),
                            color = ApexEmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricPill(
                            label = tr("PEAK ACCEL", "MAX. ZRÝCHLENIE"),
                            value = String.format(Locale.US, "%.2f G", riderAnalytics.peakAccelG),
                            color = RacingAmberPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Total Rides Session Count
                    MetricPill(
                        label = tr("TOTAL RIDES", "CELKOVO JÁZD"),
                        value = "${riderAnalytics.totalRides} ${tr("SESSIONS", "JÁZD")}",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { navController.navigate("history") },
                        colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = RacingAmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("View Ride History & Trajectory Heatmaps", "Zobraziť históriu jázd a mapu trajektórií"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // =========================================================================
            // 4. Top Rides / Personal Bests Card
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = tr("PERSONAL BESTS & TOP RIDES", "OSOBNÉ REKORDY A NAJLEPŠIE JAZDY"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (riderAnalytics.totalRides == 0) {
                        Text(
                            text = tr("No recorded rides yet", "Zatiaľ žiadne zaznamenané jazdy"),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    } else {
                        // 1. Deepest Lean Ride
                        riderAnalytics.topLeanRide?.let { ride ->
                            val peakLean = kotlin.math.max(ride.maxLeanLeftDeg, ride.maxLeanRightDeg)
                            PersonalBestRideRow(
                                title = tr("Deepest Lean", "Najhlbší náklon"),
                                mainMetric = "${peakLean.toInt()}°",
                                subtitle = "${ride.name ?: "${tr("Ride", "Jazda")} #${ride.rideId}"} • ${String.format(Locale.US, "%.1f", ride.distanceKm)} km",
                                accentColor = RacingAmberPrimary,
                                onClick = { navController.navigate("history?rideId=${ride.rideId}") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 2. Fastest Ride
                        riderAnalytics.fastestRide?.let { ride ->
                            PersonalBestRideRow(
                                title = tr("Fastest Ride", "Najrýchlejšia jazda"),
                                mainMetric = "${ride.maxSpeedKmh.toInt()} km/h",
                                subtitle = "${ride.name ?: "${tr("Ride", "Jazda")} #${ride.rideId}"} • ${String.format(Locale.US, "%.1f", ride.distanceKm)} km",
                                accentColor = RedlineCrimson,
                                onClick = { navController.navigate("history?rideId=${ride.rideId}") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 3. Longest Ride
                        riderAnalytics.longestRide?.let { ride ->
                            val rideMins = ((ride.endTimeEpochMs - ride.startTimeEpochMs) / (1000 * 60)).coerceAtLeast(0)
                            PersonalBestRideRow(
                                title = tr("Longest Ride", "Najdlhšia jazda"),
                                mainMetric = "${String.format(Locale.US, "%.1f", ride.distanceKm)} km",
                                subtitle = "${ride.name ?: "${tr("Ride", "Jazda")} #${ride.rideId}"} • ${rideMins} min",
                                accentColor = TelemetryIceCyan,
                                onClick = { navController.navigate("history?rideId=${ride.rideId}") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PersonalBestRideRow(
    title: String,
    mainMetric: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mainMetric,
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TcoLegendItem(
    label: String,
    amount: String,
    subtitle: String?,
    color: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = color.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}