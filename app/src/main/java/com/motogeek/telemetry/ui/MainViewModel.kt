package com.motogeek.telemetry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.data.local.MotoGeekRepository
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.data.local.ServiceLogEntity
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.service.LiveTelemetryState
import com.motogeek.telemetry.service.TelemetryForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.motogeek.telemetry.service.RideSimulatorEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository: MotoGeekRepository
    val evaluator = MaintenanceEvaluator()
    val importExportManager: ImportExportManager

    private var lastLoggedServiceId: String? = null

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding = _showOnboarding.asStateFlow()

    init {
        val db = MotoGeekDatabase.getDatabase(application)
        repository = MotoGeekRepository(db.motoGeekDao())
        importExportManager = ImportExportManager(application, repository)

        viewModelScope.launch {
            try {
                val bikes = repository.getAllMotorcycles().first()
                if (bikes.isEmpty()) {
                    _showOnboarding.value = true
                    // Seed the Universal Template by default
                    applyTemplate(MotorcycleTemplateCatalog.universal)
                } else {
                    _showOnboarding.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
    }

    fun applyTemplate(template: com.motogeek.telemetry.domain.maintenance.MotorcycleBackupDto) {
        viewModelScope.launch {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

            // Delete all previous active ones safely without infinite loop
            val bikes = repository.getAllMotorcycles().first()
            bikes.forEach { repository.deleteMotorcycle(it.id) }

            val bikeEntity = MotorcycleEntity(
                id = UUID.randomUUID().toString(),
                make = template.motorcycle.make,
                model = template.motorcycle.model,
                year = template.motorcycle.year,
                engineDisplacementCc = template.motorcycle.engineDisplacementCc,
                baseOdometerKm = template.motorcycle.baseOdometerKm,
                accumulatedTrackedKm = template.motorcycle.accumulatedTrackedKm,
                manualAdjustmentOffsetKm = template.motorcycle.manualAdjustmentOffsetKm,
                technicalSpecsJson = template.technicalSpecs?.let { json.encodeToString(it) } ?: "{}",
                isActive = true
            )
            repository.insertMotorcycle(bikeEntity)

            val taskEntities = template.maintenanceSchedule.map { task ->
                com.motogeek.telemetry.data.local.MaintenanceTaskEntity(
                    taskId = task.taskId,
                    motorcycleId = bikeEntity.id,
                    category = task.category,
                    taskName = task.taskName,
                    actionType = task.actionType,
                    firstBreakInAtKm = task.firstBreakInAtKm,
                    intervalKm = task.intervalKm,
                    intervalMonths = task.intervalMonths,
                    warningThresholdKm = task.warningThresholdKm,
                    warningThresholdDays = task.warningThresholdDays,
                    oemPartNumbersJson = json.encodeToString(task.oemPartNumbers),
                    notes = task.notes
                )
            }
            repository.insertMaintenanceTasks(taskEntities)
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
    fun deleteRideSession(rideId: Long) {
        viewModelScope.launch {
            repository.deleteRideSession(rideId)
        }
    }

    fun updateRideSessionDetails(rideId: Long, name: String?, notes: String?) {
        viewModelScope.launch {
            repository.updateRideSessionDetails(rideId, name, notes)
        }
    }

    fun getCornerEventsForRide(rideId: Long) = repository.getCornerEventsForRide(rideId)
    fun getTelemetryTrackPointsForRide(rideId: Long) = repository.getTelemetryTrackPointsForRide(rideId)

    fun getMaintenanceTasks(motorcycleId: String) = repository.getMaintenanceTasksForMotorcycleFlow(motorcycleId)
    fun getServiceLogs(motorcycleId: String) = repository.getServiceLogsForMotorcycleFlow(motorcycleId)

    fun logQuickServiceByCategory(motorcycle: MotorcycleEntity, categoryKeyword: String, notes: String) {
        viewModelScope.launch {
            val tasks = repository.getMaintenanceTasksForMotorcycle(motorcycle.id)
            val matchedTask = tasks.firstOrNull { it.taskName.contains(categoryKeyword, ignoreCase = true) || it.category.contains(categoryKeyword, ignoreCase = true) }

            val taskId = matchedTask?.taskId ?: UUID.randomUUID().toString()

            val logId = UUID.randomUUID().toString()
            val serviceLog = ServiceLogEntity(
                logId = logId,
                motorcycleId = motorcycle.id,
                taskId = taskId,
                performedAtIsoDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                odometerAtServiceKm = motorcycle.totalOdometerKm,
                costEur = 0.0,
                performedBy = "Owner",
                notes = notes
            )
            repository.insertServiceLog(serviceLog)
            lastLoggedServiceId = logId
        }
    }

    fun undoLastQuickService() {
        viewModelScope.launch {
            lastLoggedServiceId?.let { logId ->
                repository.deleteServiceLog(logId)
                lastLoggedServiceId = null
            }
        }
    }
}
