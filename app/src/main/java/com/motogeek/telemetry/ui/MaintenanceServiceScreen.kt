package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.ServiceLogEntity
import com.motogeek.telemetry.data.local.ServiceChecklistItem
import com.motogeek.telemetry.ui.theme.AccentRedIcon
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed
import com.motogeek.telemetry.ui.theme.ElectricBlueBorder
import com.motogeek.telemetry.ui.theme.NavyCardBg
import com.motogeek.telemetry.ui.theme.NeonCyanText
import com.motogeek.telemetry.ui.theme.TrackGreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaintenanceServiceScreen(viewModel: MainViewModel, navController: NavController) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("LAST SERVICE", "PREVIOUS SERVICES")
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    val serviceLogs by produceState(initialValue = emptyList<ServiceLogEntity>(), activeBike) {
        activeBike?.let { bike ->
            viewModel.repository.getServiceLogsForMotorcycleFlow(bike.id).collect {
                value = it
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance Service", color = Color.White) },
                actions = {
                    TextButton(onClick = { /* Add */ }) {
                        Text("+ ADD", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = CrimsonHeaderRed,
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(serviceLogs) { log ->
                    ServiceLogCard(log)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceLogCard(log: ServiceLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyCardBg),
        border = BorderStroke(1.dp, ElectricBlueBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(log.serviceTitle, color = Color.White, fontSize = 18.sp)
                    Text(log.locationOrShop, color = Color.Gray, fontSize = 14.sp)
                }
                Text("${log.costEur.toInt()}฿", color = AccentRedIcon, fontSize = 24.sp) // Replace with currency symbol mapping later
            }

            Spacer(modifier = Modifier.height(16.dp))

            val checklist = try {
                Json.decodeFromString<List<ServiceChecklistItem>>(log.checklistItemsJson)
            } catch (e: Exception) {
                emptyList()
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                checklist.forEach { item ->
                    val isChecked = item.status == "CHECKED"
                    val color = if (isChecked) TrackGreen else AccentRedIcon
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        color = Color.Transparent
                    ) {
                        Text(
                            text = item.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = color,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Serviced at: ${log.odometerAtServiceKm.toInt()} km", color = TrackGreen, fontSize = 12.sp)
                Text("Limit: ${log.limitKm.toInt()} km", color = NeonCyanText, fontSize = 12.sp)
                Text("End: ${log.endKm.toInt()} km", color = AccentRedIcon, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Serviced on: ${log.performedAtIsoDate}", color = NeonCyanText, fontSize = 14.sp)
                Text("Rode: 100%", color = NeonCyanText, fontSize = 14.sp) // Needs calculation based on current odometer
            }
            LinearProgressIndicator(
                progress = 1f,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = CrimsonHeaderRed
            )
        }
    }
}
