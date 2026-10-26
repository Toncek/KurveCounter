package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.ui.theme.AccentRedIcon
import com.motogeek.telemetry.ui.theme.DrawerSurfaceBg

@Composable
fun MotoGeekDrawerSheet(
    activeBike: MotorcycleEntity?,
    allBikes: List<MotorcycleEntity>,
    onBikeSelected: (String) -> Unit,
    onAddNewBike: () -> Unit,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = DrawerSurfaceBg,
        drawerContentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "MYBIKES.APP",
                color = AccentRedIcon,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ActiveBikeDropdownCard(
                activeBike = activeBike,
                allBikes = allBikes,
                onBikeSelected = onBikeSelected,
                onAddNewBike = onAddNewBike,
                showOdometer = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Section
            DrawerItem("Home", "home", android.R.drawable.ic_menu_agenda, currentRoute, onNavigate)
            DrawerItem("Dashboard", "dashboard", android.R.drawable.ic_menu_sort_by_size, currentRoute, onNavigate)
            DrawerItem("Track Ride", "cockpit", android.R.drawable.ic_menu_mylocation, currentRoute, onNavigate)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            SectionHeader("Manage Bike")
            DrawerItem("Bike Information", "bike_info", android.R.drawable.ic_menu_info_details, currentRoute, onNavigate)
            DrawerItem("Fuel", "fuel", android.R.drawable.ic_menu_view, currentRoute, onNavigate)
            DrawerItem("Parts", "parts", android.R.drawable.ic_menu_manage, currentRoute, onNavigate)
            DrawerItem("Maintenance Service", "maintenance", android.R.drawable.ic_menu_manage, currentRoute, onNavigate)
            DrawerItem("Rides", "history", android.R.drawable.ic_menu_mapmode, currentRoute, onNavigate)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            SectionHeader("Manage App")
            DrawerItem("App Settings", "settings", android.R.drawable.ic_menu_preferences, currentRoute, onNavigate)
            DrawerItem("Reminders", "reminders", android.R.drawable.ic_lock_idle_alarm, currentRoute, onNavigate)
            DrawerItem("Logs", "logs", android.R.drawable.ic_menu_recent_history, currentRoute, onNavigate)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            SectionHeader("Exports")
            DrawerItem("Bike Data Export", "export", android.R.drawable.ic_menu_save, currentRoute, onNavigate)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            SectionHeader("Backup & Restore")
            DrawerItem("Offline Backup", "offline_backup", android.R.drawable.ic_menu_save, currentRoute, onNavigate)
            DrawerItem("Cloud Backup", "cloud_backup", android.R.drawable.ic_menu_share, currentRoute, onNavigate)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            SectionHeader("More")
            DrawerItem("About", "about", android.R.drawable.ic_menu_info_details, currentRoute, onNavigate)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.Gray,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
    )
}

@Composable
fun DrawerItem(label: String, route: String, iconRes: Int, currentRoute: String?, onNavigate: (String) -> Unit) {
    val selected = currentRoute == route
    NavigationDrawerItem(
        label = { Text(label) },
        icon = {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (selected) AccentRedIcon else Color.White
            )
        },
        selected = selected,
        onClick = { onNavigate(route) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFF1B2234),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = AccentRedIcon,
            unselectedTextColor = Color.White
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
