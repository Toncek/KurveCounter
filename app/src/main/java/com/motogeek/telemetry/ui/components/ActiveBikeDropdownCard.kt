package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.ui.theme.AccentRedIcon
import com.motogeek.telemetry.ui.theme.ElectricBlueBorder
import com.motogeek.telemetry.ui.theme.NavyCardBg

@Composable
fun ActiveBikeDropdownCard(
    activeBike: MotorcycleEntity?,
    allBikes: List<MotorcycleEntity>,
    onBikeSelected: (String) -> Unit,
    onAddNewBike: () -> Unit,
    showOdometer: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        colors = CardDefaults.cardColors(containerColor = NavyCardBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ElectricBlueBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Placeholder icon for Motorcycle
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_directions),
                        contentDescription = "Bike Icon",
                        tint = AccentRedIcon,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    SegmentedDigitalText(
                        text = activeBike?.name?.uppercase()?.takeIf { it.isNotBlank() }
                            ?: "${activeBike?.make} ${activeBike?.model}".uppercase(),
                        fontSize = 20.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown",
                    tint = AccentRedIcon
                )
            }

            if (showOdometer && activeBike != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = ElectricBlueBorder.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speedometer placeholder
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                        contentDescription = "Odometer Icon",
                        tint = AccentRedIcon,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SegmentedDigitalText(
                        text = "${activeBike.totalOdometerKm.toInt()} KM",
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allBikes.forEach { bike ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = bike.name.takeIf { it.isNotBlank() }
                                ?: "${bike.make} ${bike.model}"
                        )
                    },
                    onClick = {
                        onBikeSelected(bike.id)
                        expanded = false
                    }
                )
            }
            Divider()
            DropdownMenuItem(
                text = { Text("+ Add New Motorbike", color = AccentRedIcon) },
                onClick = {
                    onAddNewBike()
                    expanded = false
                }
            )
        }
    }
}
