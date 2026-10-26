package com.motogeek.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.AccentRedIcon
import com.motogeek.telemetry.ui.theme.ElectricBlueBorder
import com.motogeek.telemetry.ui.theme.NavyCardBg
import com.motogeek.telemetry.ui.theme.NeonCyanText
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    val time = LocalTime.now()
    val greeting = when (time.hour) {
        in 0..11 -> "Good Morning"
        in 12..17 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(text = greeting, color = AccentRedIcon, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = activeBike?.name?.takeIf { it.isNotBlank() } ?: "${activeBike?.make} ${activeBike?.model}", color = NeonCyanText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SegmentedDigitalText(text = time.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 18.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        // Odometer Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Update Odometer */ },
            colors = CardDefaults.cardColors(containerColor = NavyCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlueBorder),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                    contentDescription = "Odometer",
                    tint = AccentRedIcon,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                SegmentedDigitalText(
                    text = "${activeBike?.totalOdometerKm?.toInt() ?: 0} KM",
                    fontSize = 32.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shortcuts
        Text(text = "Shortcuts", color = Color.Gray, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShortcutCard("Add Fuel", android.R.drawable.ic_menu_view, Modifier.weight(1f)) { navController.navigate("fuel") }
                ShortcutCard("Add Parts", android.R.drawable.ic_menu_manage, Modifier.weight(1f)) { navController.navigate("parts") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShortcutCard("Add Maintenance", android.R.drawable.ic_menu_manage, Modifier.weight(1f)) { navController.navigate("maintenance") }
                ShortcutCard("Track Rides", android.R.drawable.ic_menu_mylocation, Modifier.weight(1f)) { navController.navigate("cockpit") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShortcutCard("Dashboard", android.R.drawable.ic_menu_sort_by_size, Modifier.weight(1f)) { navController.navigate("dashboard") }
                ShortcutCard("Backup & Restore", android.R.drawable.ic_menu_save, Modifier.weight(1f)) { navController.navigate("offline_backup") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShortcutCard("Reminders", android.R.drawable.ic_lock_idle_alarm, Modifier.weight(1f)) { navController.navigate("reminders") }
                ShortcutCard("Settings", android.R.drawable.ic_menu_preferences, Modifier.weight(1f)) { navController.navigate("settings") }
            }
        }
    }
}

@Composable
fun ShortcutCard(label: String, iconRes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavyCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlueBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = AccentRedIcon,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, color = Color.White, fontSize = 12.sp)
        }
    }
}
