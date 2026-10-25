package com.motogeek.telemetry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.data.local.MotoGeekRepository
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.service.LiveTelemetryState
import com.motogeek.telemetry.service.TelemetryForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MotoGeekRepository
    val evaluator = MaintenanceEvaluator()
    val importExportManager: ImportExportManager

    init {
        val db = MotoGeekDatabase.getDatabase(application)
        repository = MotoGeekRepository(db.motoGeekDao())
        importExportManager = ImportExportManager(application, repository)

        viewModelScope.launch {
            // Seed a dummy motorcycle if empty for preview purposes
            repository.getAllMotorcycles().collect { bikes ->
                if (bikes.isEmpty()) {
                    repository.insertMotorcycle(
                        MotorcycleEntity(
                            id = "default_bike",
                            make = "Yamaha",
                            model = "MT-09",
                            year = 2024,
                            engineDisplacementCc = 890,
                            baseOdometerKm = 0.0,
                            accumulatedTrackedKm = 0.0,
                            manualAdjustmentOffsetKm = 0.0,
                            technicalSpecsJson = "{}",
                            isActive = true
                        )
                    )
                }
            }
        }
    }

    val liveTelemetryState: StateFlow<LiveTelemetryState> = TelemetryForegroundService.state
    val activeMotorcycle = repository.getActiveMotorcycleFlow()
    val allRides = repository.getAllRideSessions()

    fun getMaintenanceTasks(motorcycleId: String) = repository.getMaintenanceTasksForMotorcycleFlow(motorcycleId)
    fun getServiceLogs(motorcycleId: String) = repository.getServiceLogsForMotorcycleFlow(motorcycleId)
}
