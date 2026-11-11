package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.ui.theme.*

@Composable
fun MotoGeekDrawerSheet(
    activeBike: MotorcycleEntity?,
    allBikes: List<MotorcycleEntity>,
    onBikeSelected: (String) -> Unit,
    onAddNewBike: () -> Unit,
    onSyncOdometer: ((Double) -> Unit)? = null,
    currentLanguage: AppLang = AppLang.EN,
    onLanguageToggle: (() -> Unit)? = null,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = CarbonObsidianBg,
        drawerContentColor = Color.White,
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ApexCarbon TFT Header + Language Switcher
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(28.dp)
                            .background(RacingAmberPrimary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "KURVE // TELEMETRY",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = tr("APEX CARBON TFT DASHBOARD", "PRÍSTROJOVÝ PANEL APEX CARBON"),
                            color = RacingAmberPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
                if (onLanguageToggle != null) {
                    OutlinedButton(
                        onClick = onLanguageToggle,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (currentLanguage == AppLang.EN) "EN | SK" else "SK | EN",
                            color = TelemetryIceCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Redesigned Active Bike Dropdown
            ActiveBikeDropdownCard(
                activeBike = activeBike,
                allBikes = allBikes,
                onBikeSelected = onBikeSelected,
                onAddNewBike = onAddNewBike,
                onSyncOdometer = onSyncOdometer,
                showOdometer = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 1: COCKPIT & ANALYTICS
            DrawerSectionHeader(tr("COCKPIT & ANALYTICS", "KOKPIT A ANALYTIKA"))
            DrawerTftItem(tr("Home Hub", "Domov"), "home", Icons.Default.Home, currentRoute, onNavigate)
            DrawerTftItem(tr("Analytics Dashboard", "Prehľad a náklady (TCO)"), "dashboard", Icons.Default.Dashboard, currentRoute, onNavigate)
            DrawerTftItem(tr("Live Ride Cockpit", "Živý kokpit jazdy"), "cockpit", Icons.Default.Speed, currentRoute, onNavigate)
            DrawerTftItem(tr("Ride History & Heatmaps", "História jázd a mapy"), "history", Icons.Default.Route, currentRoute, onNavigate)

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // SECTION 2: GARAGE & SERVICE
            DrawerSectionHeader(tr("GARAGE & SERVICE", "GARÁŽ A SERVIS"))
            DrawerTftItem(tr("Bike Profile & Torques", "Profil motorky a momenty"), "bike_info", Icons.Default.TwoWheeler, currentRoute, onNavigate)
            DrawerTftItem(tr("Maintenance Hub (3-Tab)", "Servis a údržba (3 karty)"), "maintenance", Icons.Default.Build, currentRoute, onNavigate)
            DrawerTftItem(tr("Fuel & Consumption", "Tankovanie a spotreba"), "fuel", Icons.Default.LocalGasStation, currentRoute, onNavigate)
            DrawerTftItem(tr("Parts & Mods", "Diely a úpravy"), "parts", Icons.Default.Handyman, currentRoute, onNavigate)
            DrawerTftItem(tr("Service Reminders", "Pripomienky"), "reminders", Icons.Default.Notifications, currentRoute, onNavigate)

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // SECTION 3: DATA & BACKUP
            DrawerSectionHeader(tr("DATA & BACKUP", "DÁTA A ZÁLOHOVANIE"))
            DrawerTftItem(tr("Digital Service Book & Export", "Servisná knižka a export"), "export", Icons.Default.FileDownload, currentRoute, onNavigate)
            DrawerTftItem(tr("JSON & Cloud Backup", "JSON a Cloud záloha"), "offline_backup", Icons.Default.CloudUpload, currentRoute, onNavigate)
            DrawerTftItem(tr("AI Manual Extractor & Settings", "AI manuál a šablóny"), "settings", Icons.Default.Psychology, currentRoute, onNavigate)

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // SECTION 4: SYSTEM
            DrawerSectionHeader(tr("SYSTEM", "SYSTÉM"))
            DrawerTftItem(tr("Activity Timeline (Logs)", "História aktivít (Logy)"), "logs", Icons.Default.Article, currentRoute, onNavigate)
            DrawerTftItem(tr("Sensor Diagnostics & About", "Diagnostika senzorov a info"), "about", Icons.Default.Info, currentRoute, onNavigate)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF6B7A99),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerTftItem(
    label: String,
    route: String,
    icon: ImageVector,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val selected = currentRoute == route
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) RacingAmberPrimary else Color(0xFF8E9BB0),
                modifier = Modifier.size(20.dp)
            )
        },
        selected = selected,
        onClick = { onNavigate(route) },
        shape = RoundedCornerShape(8.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = TftSurfaceElevated,
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = RacingAmberPrimary,
            unselectedTextColor = Color(0xFFD6DBE5)
        ),
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
