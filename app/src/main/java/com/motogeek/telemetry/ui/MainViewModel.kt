package com.motogeek.telemetry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.service.LiveTelemetryState
import com.motogeek.telemetry.service.RideSimulatorEngine
import com.motogeek.telemetry.service.TelemetryForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.min

data class FuelEconomyStats(
    val avgLitersPer100Km: Double = 0.0,
    val avgKmPerLiter: Double = 0.0,
    val bestLitersPer100Km: Double = 0.0,
    val totalLiters: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalFuelDistanceKm: Double = 0.0
)

data class TcoStats(
    val purchasePrice: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalPartsCost: Double = 0.0,
    val totalServiceCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val costPerKm: Double = 0.0,
    val currency: String = "€"
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository: MotoGeekRepository
    val evaluator = MaintenanceEvaluator()
    val importExportManager: ImportExportManager

    private var lastLoggedServiceId: String? = null

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding = _showOnboarding.asStateFlow()

    val activeMotorcycle: StateFlow<MotorcycleEntity?>
    val allMotorcycles: StateFlow<List<MotorcycleEntity>>
    val allRides: Flow<List<RideSessionEntity>>

    init {
        val db = MotoGeekDatabase.getDatabase(application)
        repository = MotoGeekRepository(db.motoGeekDao())
        importExportManager = ImportExportManager(application, repository)

        activeMotorcycle = repository.getActiveMotorcycleFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

        allMotorcycles = repository.getAllMotorcycles().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        allRides = repository.getAllRideSessions()

        viewModelScope.launch {
            val bikes = repository.getAllMotorcycles().first()
            if (bikes.isEmpty()) {
                _showOnboarding.value = true
                applyTemplate(MotorcycleTemplateCatalog.universal)
            } else {
                _showOnboarding.value = false
            }
        }
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
    }

    // --- Active Bike Reactive Streams ---
    val currentFuelLogs: StateFlow<List<FuelLogEntity>> = activeMotorcycle.flatMapLatest { bike ->
        if (bike != null) repository.getFuelLogsForMotorcycle(bike.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPartLogs: StateFlow<List<PartLogEntity>> = activeMotorcycle.flatMapLatest { bike ->
        if (bike != null) repository.getPartLogsForMotorcycle(bike.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentServiceLogs: StateFlow<List<ServiceLogEntity>> = activeMotorcycle.flatMapLatest { bike ->
        if (bike != null) repository.getServiceLogsForMotorcycleFlow(bike.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentReminders: StateFlow<List<ReminderEntity>> = activeMotorcycle.flatMapLatest { bike ->
        if (bike != null) repository.getRemindersForMotorcycle(bike.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentAppLogs: StateFlow<List<AppLogEntity>> = activeMotorcycle.flatMapLatest { bike ->
        if (bike != null) repository.getAppLogsForMotorcycle(bike.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentRideSessions: StateFlow<List<RideSessionEntity>> = activeMotorcycle.flatMapLatest { bike ->
        if (bike != null) repository.getRideSessionsForMotorcycle(bike.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Fuel Economy Stats Flow ---
    val fuelEconomyStats: StateFlow<FuelEconomyStats> = currentFuelLogs.map { logs ->
        if (logs.isEmpty()) return@map FuelEconomyStats()

        val totalLiters = logs.sumOf { it.liters }
        val totalCost = logs.sumOf { it.totalCost }
        val sorted = logs.filter { it.odometerKm > 0 }.sortedBy { it.odometerKm }

        if (sorted.size >= 2) {
            val totalDistance = sorted.last().odometerKm - sorted.first().odometerKm
            val consumedLiters = sorted.drop(1).sumOf { it.liters }
            val avgL100 = if (totalDistance > 0 && consumedLiters > 0) {
                ((consumedLiters / totalDistance) * 100.0).takeIf { it.isFinite() } ?: 0.0
            } else 0.0
            val avgKmL = if (avgL100 > 0) (100.0 / avgL100).takeIf { it.isFinite() } ?: 0.0 else 0.0

            val segmentEfficiencies = mutableListOf<Double>()
            for (i in 0 until sorted.size - 1) {
                val d = sorted[i + 1].odometerKm - sorted[i].odometerKm
                val l = sorted[i + 1].liters
                if (d > 0 && l > 0) {
                    val eff = (l / d) * 100.0
                    if (eff.isFinite() && eff > 1.0 && eff < 30.0) {
                        segmentEfficiencies.add(eff)
                    }
                }
            }
            val best = segmentEfficiencies.minOrNull() ?: avgL100

            FuelEconomyStats(
                avgLitersPer100Km = avgL100,
                avgKmPerLiter = avgKmL,
                bestLitersPer100Km = best,
                totalLiters = totalLiters,
                totalFuelCost = totalCost,
                totalFuelDistanceKm = totalDistance
            )
        } else {
            FuelEconomyStats(
                avgLitersPer100Km = 0.0,
                avgKmPerLiter = 0.0,
                bestLitersPer100Km = 0.0,
                totalLiters = totalLiters,
                totalFuelCost = totalCost,
                totalFuelDistanceKm = 0.0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FuelEconomyStats())

    // --- TCO Stats Flow ---
    val tcoStats: StateFlow<TcoStats> = combine(
        activeMotorcycle,
        currentFuelLogs,
        currentPartLogs,
        currentServiceLogs
    ) { bike, fuelLogs, partLogs, serviceLogs ->
        val purchasePrice = bike?.buyingPrice ?: 0.0
        val fuelTotal = fuelLogs.sumOf { it.totalCost }
        val partsTotal = partLogs.sumOf { it.cost }
        val serviceTotal = serviceLogs.sumOf { it.costEur }
        val totalCost = purchasePrice + fuelTotal + partsTotal + serviceTotal
        val totalDistance = bike?.totalOdometerKm ?: 0.0
        val costPerKm = if (totalDistance > 0) {
            (totalCost / totalDistance).takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        TcoStats(
            purchasePrice = purchasePrice,
            totalFuelCost = fuelTotal,
            totalPartsCost = partsTotal,
            totalServiceCost = serviceTotal,
            totalCost = totalCost,
            totalDistanceKm = totalDistance,
            costPerKm = costPerKm,
            currency = bike?.currencySymbol ?: "€"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TcoStats())

    // --- Bike Management ---
    fun selectActiveMotorcycle(id: String) {
        viewModelScope.launch {
            repository.selectActiveMotorcycle(id)
            repository.logActivity(id, "SYSTEM", "Motorcycle Selected", "Switched active bike")
        }
    }

    fun addNewMotorcycle(
        name: String,
        make: String,
        model: String,
        year: Int,
        displacementCc: Int,
        startOdoKm: Double,
        price: Double,
        type: String,
        currencySymbol: String = "€"
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val newBike = MotorcycleEntity(
                id = id,
                name = name.ifBlank { "$make $model".trim() },
                make = make.ifBlank { "Yamaha" },
                model = model.ifBlank { "MT-09" },
                year = if (year > 1900) year else 2024,
                engineDisplacementCc = if (displacementCc > 0) displacementCc else 890,
                baseOdometerKm = startOdoKm,
                accumulatedTrackedKm = 0.0,
                manualAdjustmentOffsetKm = 0.0,
                technicalSpecsJson = "{}",
                buyingPrice = price,
                type = type.ifBlank { "Naked" },
                startDateIso = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                currencySymbol = currencySymbol,
                isActive = true
            )
            repository.clearActiveMotorcycles()
            repository.insertMotorcycle(newBike)
            repository.logActivity(id, "SYSTEM", "Motorcycle Created", "Added ${newBike.displayName}")
        }
    }

    fun deleteMotorcycle(id: String) {
        viewModelScope.launch {
            repository.deleteMotorcycle(id)
            val remaining = repository.getAllMotorcycles().first()
            if (remaining.isNotEmpty()) {
                repository.selectActiveMotorcycle(remaining.first().id)
            }
        }
    }

    fun updateActiveBikeOdometer(newKm: Double) {
        viewModelScope.launch {
            activeMotorcycle.value?.let { bike ->
                repository.updateOdometerManualOffset(
                    bike.id,
                    newKm,
                    bike.baseOdometerKm,
                    bike.accumulatedTrackedKm
                )
                repository.logActivity(
                    bike.id,
                    "SYSTEM",
                    "Odometer Calibrated",
                    "Adjusted odometer to ${newKm.toInt()} km"
                )
            }
        }
    }

    // --- CRUD Actions for Garage Data ---
    fun addFuelLog(fuelLog: FuelLogEntity) {
        viewModelScope.launch {
            repository.insertFuelLog(fuelLog)
            repository.logActivity(
                fuelLog.motorcycleId,
                "FUEL",
                "Fuel Added",
                "${fuelLog.liters} L for ${fuelLog.totalCost} at ${fuelLog.odometerKm.toInt()} km"
            )
        }
    }

    fun updateFuelLog(fuelLog: FuelLogEntity) {
        viewModelScope.launch { repository.updateFuelLog(fuelLog) }
    }

    fun deleteFuelLog(fuelId: Long) {
        viewModelScope.launch {
            repository.deleteFuelLog(fuelId)
        }
    }

    fun addPartLog(partLog: PartLogEntity) {
        viewModelScope.launch {
            repository.insertPartLog(partLog)
            repository.logActivity(
                partLog.motorcycleId,
                "PARTS",
                "Part Installed",
                "${partLog.partName} (${partLog.brand}) at ${partLog.odometerKm.toInt()} km"
            )
        }
    }

    fun updatePartLog(partLog: PartLogEntity) {
        viewModelScope.launch { repository.updatePartLog(partLog) }
    }

    fun deletePartLog(partId: Long) {
        viewModelScope.launch {
            repository.deletePartLog(partId)
        }
    }

    fun addServiceLog(serviceLog: ServiceLogEntity) {
        viewModelScope.launch {
            repository.insertServiceLog(serviceLog)
            repository.logActivity(
                serviceLog.motorcycleId,
                "SERVICE",
                serviceLog.serviceTitle,
                "Serviced at ${serviceLog.odometerAtServiceKm.toInt()} km"
            )
        }
    }

    fun updateServiceLog(serviceLog: ServiceLogEntity) {
        viewModelScope.launch { repository.updateServiceLog(serviceLog) }
    }

    fun deleteServiceLog(logId: String) {
        viewModelScope.launch { repository.deleteServiceLog(logId) }
    }

    fun addReminder(reminder: ReminderEntity) {
        viewModelScope.launch { repository.insertReminder(reminder) }
    }

    fun updateReminder(reminder: ReminderEntity) {
        viewModelScope.launch { repository.updateReminder(reminder) }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch { repository.deleteReminder(reminderId) }
    }

    fun deleteAppLog(logId: String) {
        viewModelScope.launch { repository.deleteAppLog(logId) }
    }

    fun clearAppLogs() {
        viewModelScope.launch {
            activeMotorcycle.value?.let { repository.clearAppLogs(it.id) }
        }
    }

    fun applyTemplate(template: com.motogeek.telemetry.domain.maintenance.MotorcycleBackupDto) {
        viewModelScope.launch {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
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
                MaintenanceTaskEntity(
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

    // --- Simulator & Demo Mode ---
    private val simulatorEngine = RideSimulatorEngine()
    private var simulatorJob: Job? = null
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
            val matchedTask = tasks.firstOrNull {
                it.taskName.contains(categoryKeyword, ignoreCase = true) ||
                it.category.contains(categoryKeyword, ignoreCase = true)
            }

            val taskId = matchedTask?.taskId ?: UUID.randomUUID().toString()
            val logId = UUID.randomUUID().toString()
            val serviceLog = ServiceLogEntity(
                logId = logId,
                motorcycleId = motorcycle.id,
                taskId = taskId,
                serviceTitle = notes,
                performedAtIsoDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                odometerAtServiceKm = motorcycle.totalOdometerKm,
                servicedAtKm = motorcycle.totalOdometerKm,
                costEur = 0.0,
                performedBy = "Self",
                notes = notes
            )
            repository.insertServiceLog(serviceLog)
            lastLoggedServiceId = logId
            repository.logActivity(motorcycle.id, "SERVICE", "Quick Service", notes)
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
