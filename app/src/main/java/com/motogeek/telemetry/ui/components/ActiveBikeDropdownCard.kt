package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.theme.*

@Composable
fun ActiveBikeDropdownCard(
    activeBike: MotorcycleEntity?,
    allBikes: List<MotorcycleEntity>,
    onBikeSelected: (String) -> Unit,
    onAddNewBike: () -> Unit,
    onSyncOdometer: ((Double) -> Unit)? = null,
    showOdometer: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var syncInputKm by remember(activeBike) {
        mutableStateOf(activeBike?.totalOdometerKm?.toInt()?.toString() ?: "")
    }

    val categoryBadge = remember(activeBike?.type, activeBike?.model) {
        val raw = "${activeBike?.type} ${activeBike?.model}".lowercase()
        when {
            raw.contains("adv") || raw.contains("adventure") -> "ADV"
            raw.contains("sport") || raw.contains("supersport") || raw.contains("r1") || raw.contains("r6") || raw.contains("cbr") || raw.contains("ninja") -> "SPORT"
            raw.contains("naked") || raw.contains("mt-") || raw.contains("z900") || raw.contains("monster") || raw.contains("duke") -> "NAKED"
            raw.contains("scoot") || raw.contains("vespa") || raw.contains("forza") || raw.contains("xmax") -> "SCOOTER"
            raw.contains("dirt") || raw.contains("enduro") || raw.contains("crf") || raw.contains("wr") -> "DIRT"
            raw.contains("cruis") || raw.contains("harley") || raw.contains("rebel") -> "CRUISER"
            raw.contains("tour") -> "TOURING"
            else -> "MOTO"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left dynamic category badge + bike title & specs
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RacingAmberPrimary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, RacingAmberPrimary.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = categoryBadge,
                            color = RacingAmberPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        val title = activeBike?.displayName?.uppercase() ?: "NO MOTORCYCLE"
                        val subtitle = if (activeBike != null) {
                            "${activeBike.year} • ${activeBike.engineDisplacementCc} CC • ${activeBike.make.uppercase()}"
                        } else {
                            "Select or Add Motorcycle"
                        }

                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                        Text(
                            text = subtitle,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Dropdown trigger indicator
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Motorcycle",
                    tint = RacingAmberPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Odometer pill row
            if (showOdometer && activeBike != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.7f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TftSurfaceElevated,
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ODO ",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            SegmentedDigitalText(
                                text = String.format("%,d", activeBike.totalOdometerKm.toInt()),
                                unit = "KM",
                                color = TelemetryIceCyan,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // One-tap SYNC KM chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RacingAmberPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, RacingAmberPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            syncInputKm = activeBike.totalOdometerKm.toInt().toString()
                            showSyncDialog = true
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Odometer",
                                tint = RacingAmberPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SYNC KM",
                                color = RacingAmberPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(TftSurfaceDark)
        ) {
            allBikes.forEach { bike ->
                val isSelected = bike.id == activeBike?.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = ApexEmeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text = bike.displayName.uppercase(),
                                    color = if (isSelected) RacingAmberPrimary else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "${bike.year} • ${bike.totalOdometerKm.toInt()} km",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    onClick = {
                        onBikeSelected(bike.id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider(color = TitaniumBorder)
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add New Motorcycle",
                        tint = RacingAmberPrimary
                    )
                },
                text = {
                    Text(
                        "+ Add New Motorcycle",
                        color = RacingAmberPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                onClick = {
                    expanded = false
                    onAddNewBike()
                }
            )
        }
    }

    if (showSyncDialog && activeBike != null) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = "Sync Odometer Reading",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter current dash reading for ${activeBike.displayName}:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = syncInputKm,
                        onValueChange = { syncInputKm = it },
                        label = { Text("Odometer (km)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newKm = syncInputKm.toSafeDouble(activeBike.totalOdometerKm)
                        onSyncOdometer?.invoke(newKm)
                        showSyncDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
                ) {
                    Text("Save Odo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
