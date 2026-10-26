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
import com.motogeek.telemetry.service.RideSimulatorEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

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

    private val simulatorEngine = RideSimulatorEngine()
    private var simulatorJob: Job? = null

    // We combine the real state and the simulated state
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    val liveTelemetryState: StateFlow<LiveTelemetryState> = combine(
        TelemetryForegroundService.state,
        simulatorEngine.simState,
        _isDemoMode
    ) { realState, simState, demoMode ->
        if (demoMode) simState else realState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LiveTelemetryState()
    )

    fun startDemoMode() {
        _isDemoMode.value = true
        simulatorJob?.cancel()
        simulatorJob = viewModelScope.launch {
            simulatorEngine.startSimulation()
        }
    }

    fun stopDemoMode() {
        simulatorJob?.cancel()
        simulatorEngine.stopSimulation()
        _isDemoMode.value = false
    }
    val activeMotorcycle = repository.getActiveMotorcycleFlow()
    val allRides = repository.getAllRideSessions()

    fun pauseRide() {
        val intent = android.content.Intent(getApplication(), TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_PAUSE_RIDE
        }
        getApplication<Application>().startService(intent)
    }

    fun resumeRide() {
        val intent = android.content.Intent(getApplication(), TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_RESUME_RIDE
        }
        getApplication<Application>().startService(intent)
    }

    fun resetPeaks() {
        val intent = android.content.Intent(getApplication(), TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_RESET_PEAKS
        }
        getApplication<Application>().startService(intent)
    }

    fun getMaintenanceTasks(motorcycleId: String) = repository.getMaintenanceTasksForMotorcycleFlow(motorcycleId)
    fun getServiceLogs(motorcycleId: String) = repository.getServiceLogsForMotorcycleFlow(motorcycleId)
}
