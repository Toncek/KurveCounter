package com.motogeek.telemetry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.ServiceTaskDeduplicator
import com.motogeek.telemetry.service.LiveTelemetryState
import com.motogeek.telemetry.service.RideSimulatorEngine
import com.motogeek.telemetry.service.TelemetryForegroundService
import com.motogeek.telemetry.service.TelemetryPreferences
import com.motogeek.telemetry.ui.theme.AppLang
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
    val totalFuelDistanceKm: Double = 0.0,
    val totalFuelLiters: Double = totalLiters,
    val avgPricePerLiter: Double = 0.0,
    val totalFuelSpent: Double = totalFuelCost,
    val trackedSpanKm: Double = totalFuelDistanceKm,
    val fillCount: Int = 0
)

data class TcoStats(
    val purchasePrice: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalPartsCost: Double = 0.0,
    val totalServiceCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val costPerKm: Double = 0.0,
    val currency: String = "€",
    val serviceCost: Double = totalServiceCost,
    val partsCost: Double = totalPartsCost,
    val fuelCost: Double = totalFuelCost,
    val totalOperatingCost: Double = totalFuelCost + totalPartsCost + totalServiceCost,
    val totalInvestment: Double = totalCost,
    val ownedDistanceKm: Double = totalDistanceKm,
    val costPerMonth: Double = 0.0
)

data class RiderAnalyticsSummary(
    val totalRides: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMs: Long = 0L,
    val totalElevationGainM: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedWeightedKmh: Double = 0.0,
    val peakLeanLeftDeg: Double = 0.0,
    val peakLeanRightDeg: Double = 0.0,
    val totalCorners: Int = 0,
    val totalLeftCorners: Int = 0,
    val totalRightCorners: Int = 0,
    val avgCornerScore: Double = 0.0,
    val avgTrailBrakingPct: Double = 0.0,
    val peakLateralG: Double = 0.0,
    val peakBrakingG: Double = 0.0,
    val peakAccelG: Double = 0.0,
    val peakTurnInRateDegPerSec: Double = 0.0,
    val topLeanRide: RideSessionEntity? = null,
    val fastestRide: RideSessionEntity? = null,
    val longestRide: RideSessionEntity? = null
)

object RiderAnalyticsCalculator {
    fun computeFuelEconomyStats(logs: List<FuelLogEntity>): FuelEconomyStats {
        if (logs.isEmpty()) return FuelEconomyStats()

        val totalLiters = logs.filter { it.liters.isFinite() && it.liters > 0.0 }.sumOf { it.liters }
        val totalSpent = logs.filter { it.totalCost.isFinite() && it.totalCost > 0.0 }.sumOf { it.totalCost }
        val avgPricePerLiter = if (totalLiters > 0.0 && totalSpent > 0.0) {
            (totalSpent / totalLiters).takeIf { it.isFinite() } ?: 0.0
        } else {
            val validPrices = logs.filter { it.pricePerLiter.isFinite() && it.pricePerLiter > 0.0 }
            if (validPrices.isNotEmpty()) validPrices.map { it.pricePerLiter }.average().takeIf { it.isFinite() } ?: 0.0 else 0.0
        }

        val sorted = logs
            .filter { it.odometerKm.isFinite() && it.odometerKm > 0.0 && it.liters.isFinite() && it.liters > 0.0 }
            .sortedBy { it.odometerKm }

        if (sorted.size < 2) {
            return FuelEconomyStats(
                avgLitersPer100Km = 0.0,
                avgKmPerLiter = 0.0,
                bestLitersPer100Km = 0.0,
                totalLiters = totalLiters,
                totalFuelCost = totalSpent,
                totalFuelDistanceKm = 0.0,
                totalFuelLiters = totalLiters,
                avgPricePerLiter = avgPricePerLiter,
                totalFuelSpent = totalSpent,
                trackedSpanKm = 0.0,
                fillCount = logs.size
            )
        }

        val totalOdoSpan = (sorted.last().odometerKm - sorted.first().odometerKm).coerceAtLeast(0.0)
        val fullIndices = sorted.indices.filter { sorted[it].isFullTank }

        var avgL100 = 0.0
        val segmentEfficiencies = mutableListOf<Double>()

        if (fullIndices.size >= 2) {
            var sumSegmentDist = 0.0
            var sumSegmentLiters = 0.0

            for (k in 0 until fullIndices.size - 1) {
                val startIdx = fullIndices[k]
                val endIdx = fullIndices[k + 1]
                val segDist = sorted[endIdx].odometerKm - sorted[startIdx].odometerKm
                val segLiters = (startIdx + 1..endIdx).sumOf { sorted[it].liters }

                if (segDist > 0.0 && segLiters > 0.0) {
                    val eff = (segLiters / segDist) * 100.0
                    if (eff.isFinite() && eff > 1.0 && eff < 30.0) {
                        segmentEfficiencies.add(eff)
                    }
                    sumSegmentDist += segDist
                    sumSegmentLiters += segLiters
                }
            }

            if (sumSegmentDist > 0.0 && sumSegmentLiters > 0.0) {
                avgL100 = ((sumSegmentLiters / sumSegmentDist) * 100.0).takeIf { it.isFinite() } ?: 0.0
            }
        }

        // Fallback if < 2 full tanks or no segments could be calculated:
        if (avgL100 <= 0.0 && totalOdoSpan > 0.0) {
            val consumedLiters = sorted.drop(1).sumOf { it.liters }
            if (consumedLiters > 0.0) {
                avgL100 = ((consumedLiters / totalOdoSpan) * 100.0).takeIf { it.isFinite() } ?: 0.0
            }

            for (i in 0 until sorted.size - 1) {
                val d = sorted[i + 1].odometerKm - sorted[i].odometerKm
                val l = sorted[i + 1].liters
                if (d > 0.0 && l > 0.0) {
                    val eff = (l / d) * 100.0
                    if (eff.isFinite() && eff > 1.0 && eff < 30.0) {
                        segmentEfficiencies.add(eff)
                    }
                }
            }
        }

        val bestL100 = if (segmentEfficiencies.isNotEmpty()) {
            val minEff = segmentEfficiencies.minOrNull() ?: avgL100
            if (avgL100 > 0.0) kotlin.math.min(avgL100, minEff) else minEff
        } else avgL100

        val avgKmL = if (avgL100 > 0.0) (100.0 / avgL100).takeIf { it.isFinite() } ?: 0.0 else 0.0

        return FuelEconomyStats(
            avgLitersPer100Km = avgL100,
            avgKmPerLiter = avgKmL,
            bestLitersPer100Km = bestL100,
            totalLiters = totalLiters,
            totalFuelCost = totalSpent,
            totalFuelDistanceKm = totalOdoSpan,
            totalFuelLiters = totalLiters,
            avgPricePerLiter = avgPricePerLiter,
            totalFuelSpent = totalSpent,
            trackedSpanKm = totalOdoSpan,
            fillCount = logs.size
        )
    }

    private fun parseIsoToEpochMs(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        return try {
            java.time.LocalDate.parse(clean).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                java.time.LocalDate.parse(clean, fmt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd")
                    java.time.LocalDate.parse(clean, fmt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    try {
                        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
                        java.time.LocalDate.parse(clean, fmt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        try {
                            java.time.Instant.parse(clean).toEpochMilli()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
    }

    fun computeTcoStats(
        bike: MotorcycleEntity?,
        fuelLogs: List<FuelLogEntity>,
        serviceLogs: List<ServiceLogEntity>,
        partLogs: List<PartLogEntity>,
        rides: List<RideSessionEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): TcoStats {
        val purchasePrice = bike?.buyingPrice?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val fuelTotal = fuelLogs.filter { it.totalCost.isFinite() && it.totalCost > 0.0 }.sumOf { it.totalCost }
        val partsTotal = partLogs.filter { it.cost.isFinite() && it.cost > 0.0 }.sumOf { it.cost }
        val serviceTotal = serviceLogs.filter { it.costEur.isFinite() && it.costEur > 0.0 }.sumOf { it.costEur }
        val operatingCost = fuelTotal + partsTotal + serviceTotal
        val totalCost = purchasePrice + operatingCost

        // Calculate owner-ridden distance
        var ownerDistance = if (bike != null) {
            (bike.totalOdometerKm - bike.baseOdometerKm).coerceAtLeast(0.0)
        } else 0.0

        if (ownerDistance < 1.0 && bike != null && bike.accumulatedTrackedKm >= 1.0) {
            ownerDistance = bike.accumulatedTrackedKm
        }

        if (ownerDistance < 1.0) {
            val validFuelOdo = fuelLogs.map { it.odometerKm }.filter { it.isFinite() && it > 0.0 }
            if (validFuelOdo.size >= 2) {
                val span = (validFuelOdo.maxOrNull()!! - validFuelOdo.minOrNull()!!).coerceAtLeast(0.0)
                if (span >= 1.0) ownerDistance = span
            }
        }

        if (ownerDistance < 1.0) {
            val rideDistSum = rides.filter { it.distanceKm.isFinite() && it.distanceKm > 0.0 }.sumOf { it.distanceKm }
            if (rideDistSum >= 1.0) ownerDistance = rideDistSum
        }

        val costPerKm = if (ownerDistance >= 1.0 && operatingCost > 0.0) {
            (operatingCost / ownerDistance).takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        // Find earliest timestamp
        val candidateTimestamps = mutableListOf<Long>()
        bike?.startDateIso?.let { parseIsoToEpochMs(it)?.let { ms -> if (ms in 1_000_000_000_000L..nowMs) candidateTimestamps.add(ms) } }
        fuelLogs.forEach { parseIsoToEpochMs(it.dateIso)?.let { ms -> if (ms in 1_000_000_000_000L..nowMs) candidateTimestamps.add(ms) } }
        serviceLogs.forEach { parseIsoToEpochMs(it.performedAtIsoDate)?.let { ms -> if (ms in 1_000_000_000_000L..nowMs) candidateTimestamps.add(ms) } }
        partLogs.forEach { parseIsoToEpochMs(it.dateIso)?.let { ms -> if (ms in 1_000_000_000_000L..nowMs) candidateTimestamps.add(ms) } }
        rides.forEach { if (it.startTimeEpochMs in 1_000_000_000_000L..nowMs) candidateTimestamps.add(it.startTimeEpochMs) }

        val earliestMs = candidateTimestamps.minOrNull() ?: nowMs
        val elapsedMs = (nowMs - earliestMs).coerceAtLeast(0L)
        val elapsedDays = elapsedMs / (1000L * 60 * 60 * 24)
        val elapsedMonths = (elapsedDays / 30.4375).coerceAtLeast(1.0)

        val costPerMonth = if (operatingCost > 0.0) {
            (operatingCost / elapsedMonths).takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        val totalDist = bike?.totalOdometerKm?.takeIf { it.isFinite() && it > 0.0 } ?: ownerDistance

        return TcoStats(
            purchasePrice = purchasePrice,
            totalFuelCost = fuelTotal,
            totalPartsCost = partsTotal,
            totalServiceCost = serviceTotal,
            totalCost = totalCost,
            totalDistanceKm = totalDist,
            costPerKm = costPerKm,
            currency = bike?.currencySymbol?.ifBlank { "€" } ?: "€",
            serviceCost = serviceTotal,
            partsCost = partsTotal,
            fuelCost = fuelTotal,
            totalOperatingCost = operatingCost,
            totalInvestment = totalCost,
            ownedDistanceKm = ownerDistance,
            costPerMonth = costPerMonth
        )
    }

    fun computeRiderAnalytics(rides: List<RideSessionEntity>): RiderAnalyticsSummary {
        if (rides.isEmpty()) return RiderAnalyticsSummary()

        val totalRides = rides.size
        val totalDistanceKm = rides.filter { it.distanceKm.isFinite() && it.distanceKm > 0.0 }.sumOf { it.distanceKm }
        val totalDurationMs = rides.map { (it.endTimeEpochMs - it.startTimeEpochMs).coerceAtLeast(0L) }.sum()
        val totalElevationGainM = rides.filter { it.elevationGainMeters.isFinite() && it.elevationGainMeters > 0.0 }.sumOf { it.elevationGainMeters }

        val maxSpeedKmh = rides.map { it.maxSpeedKmh }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0

        val validSpeedRides = rides.filter { it.distanceKm.isFinite() && it.distanceKm > 0.0 && it.avgSpeedKmh.isFinite() && it.avgSpeedKmh > 0.0 }
        val totalDistForSpeed = validSpeedRides.sumOf { it.distanceKm }
        val avgSpeedWeightedKmh = if (totalDistForSpeed > 0.0) {
            (validSpeedRides.sumOf { it.distanceKm * it.avgSpeedKmh } / totalDistForSpeed).takeIf { it.isFinite() } ?: 0.0
        } else {
            val speedList = rides.filter { it.avgSpeedKmh.isFinite() && it.avgSpeedKmh > 0.0 }.map { it.avgSpeedKmh }
            if (speedList.isNotEmpty()) speedList.average().takeIf { it.isFinite() } ?: 0.0 else 0.0
        }

        val peakLeanLeftDeg = rides.map { it.maxLeanLeftDeg }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0
        val peakLeanRightDeg = rides.map { it.maxLeanRightDeg }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0

        val totalLeftCorners = rides.sumOf { it.leftCornersCount.coerceAtLeast(0) }
        val totalRightCorners = rides.sumOf { it.rightCornersCount.coerceAtLeast(0) }
        var totalCorners = rides.sumOf { it.totalCornersCount.coerceAtLeast(0) }
        if (totalCorners == 0 && (totalLeftCorners + totalRightCorners) > 0) {
            totalCorners = totalLeftCorners + totalRightCorners
        }

        val scoredRides = rides.filter { it.avgCornerScore.isFinite() && it.avgCornerScore > 0.0 }
        val avgCornerScore = if (scoredRides.isNotEmpty()) {
            scoredRides.map { it.avgCornerScore }.average().takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        val trailRides = rides.filter { it.trailBrakingScorePct.isFinite() && it.trailBrakingScorePct > 0.0 }
        val avgTrailBrakingPct = if (trailRides.isNotEmpty()) {
            trailRides.map { it.trailBrakingScorePct }.average().takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        val peakLateralG = rides.map { it.maxLateralG }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0
        val peakBrakingG = rides.map { it.maxBrakingG }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0
        val peakAccelG = rides.map { it.maxAccelG }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0
        val peakTurnInRateDegPerSec = rides.map { it.maxTurnInRateDegPerSec }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0

        val topLeanRide = rides
            .filter { (it.maxLeanLeftDeg.isFinite() && it.maxLeanLeftDeg > 0.0) || (it.maxLeanRightDeg.isFinite() && it.maxLeanRightDeg > 0.0) }
            .maxByOrNull { kotlin.math.max(if (it.maxLeanLeftDeg.isFinite()) it.maxLeanLeftDeg else 0.0, if (it.maxLeanRightDeg.isFinite()) it.maxLeanRightDeg else 0.0) }

        val fastestRide = rides
            .filter { it.maxSpeedKmh.isFinite() && it.maxSpeedKmh > 0.0 }
            .maxByOrNull { it.maxSpeedKmh }

        val longestRide = rides
            .filter { it.distanceKm.isFinite() && it.distanceKm > 0.0 }
            .maxByOrNull { it.distanceKm }

        return RiderAnalyticsSummary(
            totalRides = totalRides,
            totalDistanceKm = totalDistanceKm,
            totalDurationMs = totalDurationMs,
            totalElevationGainM = totalElevationGainM,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedWeightedKmh = avgSpeedWeightedKmh,
            peakLeanLeftDeg = peakLeanLeftDeg,
            peakLeanRightDeg = peakLeanRightDeg,
            totalCorners = totalCorners,
            totalLeftCorners = totalLeftCorners,
            totalRightCorners = totalRightCorners,
            avgCornerScore = avgCornerScore,
            avgTrailBrakingPct = avgTrailBrakingPct,
            peakLateralG = peakLateralG,
            peakBrakingG = peakBrakingG,
            peakAccelG = peakAccelG,
            peakTurnInRateDegPerSec = peakTurnInRateDegPerSec,
            topLeanRide = topLeanRide,
            fastestRide = fastestRide,
            longestRide = longestRide
        )
    }
}

fun computeFuelEconomyStats(logs: List<FuelLogEntity>): FuelEconomyStats =
    RiderAnalyticsCalculator.computeFuelEconomyStats(logs)

fun computeTcoStats(
    bike: MotorcycleEntity?,
    fuelLogs: List<FuelLogEntity>,
    serviceLogs: List<ServiceLogEntity>,
    partLogs: List<PartLogEntity>,
    rides: List<RideSessionEntity>,
    nowMs: Long = System.currentTimeMillis()
): TcoStats = RiderAnalyticsCalculator.computeTcoStats(bike, fuelLogs, serviceLogs, partLogs, rides, nowMs)

fun computeRiderAnalytics(rides: List<RideSessionEntity>): RiderAnalyticsSummary =
    RiderAnalyticsCalculator.computeRiderAnalytics(rides)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository: MotoGeekRepository
    val evaluator = MaintenanceEvaluator()
    val importExportManager: ImportExportManager
    val googleAuthManager = com.motogeek.telemetry.domain.backup.GoogleAuthManager(application)
    val googleDriveBackupManager: com.motogeek.telemetry.domain.backup.GoogleDriveBackupManager

    private var lastLoggedServiceId: String? = null

    private val telemetryPrefs = TelemetryPreferences(application)
    private val _appLanguage = MutableStateFlow(AppLang.fromCode(telemetryPrefs.appLanguageCode))
    val appLanguage: StateFlow<AppLang> = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: AppLang) {
        telemetryPrefs.appLanguageCode = lang.code
        _appLanguage.value = lang
    }

    fun toggleAppLanguage() {
        setAppLanguage(_appLanguage.value.next())
    }

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding = _showOnboarding.asStateFlow()

    val activeMotorcycle: StateFlow<MotorcycleEntity?>
    val allMotorcycles: StateFlow<List<MotorcycleEntity>>
    val allRides: Flow<List<RideSessionEntity>>

    init {
        val db = MotoGeekDatabase.getDatabase(application)
        repository = MotoGeekRepository(db.motoGeekDao())
        importExportManager = ImportExportManager(application, repository)
        googleDriveBackupManager = com.motogeek.telemetry.domain.backup.GoogleDriveBackupManager(application, importExportManager)

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
                bikes.forEach {
                    repository.deduplicateMaintenanceTasksForBike(it.id)
                }
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
        RiderAnalyticsCalculator.computeFuelEconomyStats(logs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FuelEconomyStats())

    // --- TCO Stats Flow ---
    val tcoStats: StateFlow<TcoStats> = combine(
        activeMotorcycle,
        currentFuelLogs,
        currentPartLogs,
        currentServiceLogs,
        currentRideSessions
    ) { bike, fuelLogs, partLogs, serviceLogs, rides ->
        RiderAnalyticsCalculator.computeTcoStats(bike, fuelLogs, serviceLogs, partLogs, rides)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TcoStats())

    // --- Rider Analytics Flow ---
    val riderAnalytics: StateFlow<RiderAnalyticsSummary> = currentRideSessions.map { rides ->
        RiderAnalyticsCalculator.computeRiderAnalytics(rides)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RiderAnalyticsSummary())

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

    private suspend fun syncOdometerMonotonicallyIfNeeded(motorcycleId: String, loggedOdoKm: Double) {
        if (!loggedOdoKm.isFinite() || loggedOdoKm <= 0.0) return
        val bike = repository.getMotorcycleById(motorcycleId) ?: activeMotorcycle.value
        if (bike != null && bike.id == motorcycleId && loggedOdoKm > bike.totalOdometerKm) {
            repository.updateOdometerManualOffset(
                bike.id,
                loggedOdoKm,
                bike.baseOdometerKm,
                bike.accumulatedTrackedKm
            )
        }
    }

    // --- CRUD Actions for Garage Data ---
    fun addFuelLog(fuelLog: FuelLogEntity) {
        viewModelScope.launch {
            repository.insertFuelLog(fuelLog)
            syncOdometerMonotonicallyIfNeeded(fuelLog.motorcycleId, fuelLog.odometerKm)
            repository.logActivity(
                fuelLog.motorcycleId,
                "FUEL",
                "Fuel Added",
                "${fuelLog.liters} L for ${fuelLog.totalCost} at ${fuelLog.odometerKm.toInt()} km"
            )
        }
    }

    fun updateFuelLog(fuelLog: FuelLogEntity) {
        viewModelScope.launch {
            repository.updateFuelLog(fuelLog)
            syncOdometerMonotonicallyIfNeeded(fuelLog.motorcycleId, fuelLog.odometerKm)
        }
    }

    fun deleteFuelLog(fuelId: Long) {
        viewModelScope.launch {
            repository.deleteFuelLog(fuelId)
        }
    }

    fun addPartLog(partLog: PartLogEntity) {
        viewModelScope.launch {
            repository.insertPartLog(partLog)
            syncOdometerMonotonicallyIfNeeded(partLog.motorcycleId, partLog.odometerKm)
            repository.logActivity(
                partLog.motorcycleId,
                "PARTS",
                "Part Installed",
                "${partLog.partName} (${partLog.brand}) at ${partLog.odometerKm.toInt()} km"
            )
        }
    }

    fun updatePartLog(partLog: PartLogEntity) {
        viewModelScope.launch {
            repository.updatePartLog(partLog)
            syncOdometerMonotonicallyIfNeeded(partLog.motorcycleId, partLog.odometerKm)
        }
    }

    fun deletePartLog(partId: Long) {
        viewModelScope.launch {
            repository.deletePartLog(partId)
        }
    }

    fun addServiceLog(serviceLog: ServiceLogEntity) {
        viewModelScope.launch {
            val existingTasks = repository.getMaintenanceTasksForMotorcycle(serviceLog.motorcycleId)
            val remappedLog = ServiceTaskDeduplicator.remapServiceLog(serviceLog, existingTasks)
            repository.insertServiceLog(remappedLog)
            repository.deduplicateMaintenanceTasksForBike(serviceLog.motorcycleId)
            syncOdometerMonotonicallyIfNeeded(remappedLog.motorcycleId, remappedLog.odometerAtServiceKm)
            repository.logActivity(
                remappedLog.motorcycleId,
                "SERVICE",
                remappedLog.serviceTitle,
                "Serviced at ${remappedLog.odometerAtServiceKm.toInt()} km"
            )
        }
    }

    fun updateServiceLog(serviceLog: ServiceLogEntity) {
        viewModelScope.launch {
            val existingTasks = repository.getMaintenanceTasksForMotorcycle(serviceLog.motorcycleId)
            val remappedLog = ServiceTaskDeduplicator.remapServiceLog(serviceLog, existingTasks)
            repository.updateServiceLog(remappedLog)
            repository.deduplicateMaintenanceTasksForBike(serviceLog.motorcycleId)
            syncOdometerMonotonicallyIfNeeded(remappedLog.motorcycleId, remappedLog.odometerAtServiceKm)
        }
    }

    fun deleteServiceLog(logId: String) {
        viewModelScope.launch { repository.deleteServiceLog(logId) }
    }

    fun upsertMaintenanceTask(task: MaintenanceTaskEntity) {
        viewModelScope.launch {
            repository.insertMaintenanceTask(task)
            repository.deduplicateMaintenanceTasksForBike(task.motorcycleId)
        }
    }

    fun deleteMaintenanceTask(taskId: String, motorcycleId: String) {
        viewModelScope.launch {
            repository.deleteMaintenanceTask(taskId, motorcycleId)
        }
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

    fun applyTemplate(
        template: com.motogeek.telemetry.domain.maintenance.MotorcycleBackupDto,
        applyToActiveBike: Boolean = false
    ) {
        viewModelScope.launch {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
            val currentActive = activeMotorcycle.value
            val targetBikeId: String

            if (applyToActiveBike && currentActive != null) {
                targetBikeId = currentActive.id
                val updatedSpecsJson = template.technicalSpecs?.let { json.encodeToString(it) } ?: currentActive.technicalSpecsJson
                repository.updateMotorcycle(
                    currentActive.copy(
                        technicalSpecsJson = updatedSpecsJson
                    )
                )
                repository.logActivity(
                    targetBikeId,
                    "SYSTEM",
                    "Template Specs Applied",
                    "Applied ${template.motorcycle.make} ${template.motorcycle.model} specs & schedule"
                )
            } else {
                targetBikeId = UUID.randomUUID().toString()
                repository.clearActiveMotorcycles()
                val bikeEntity = MotorcycleEntity(
                    id = targetBikeId,
                    name = template.motorcycle.name.ifBlank { "${template.motorcycle.make} ${template.motorcycle.model}".trim() },
                    make = template.motorcycle.make,
                    model = template.motorcycle.model,
                    year = template.motorcycle.year,
                    engineDisplacementCc = template.motorcycle.engineDisplacementCc,
                    baseOdometerKm = template.motorcycle.baseOdometerKm,
                    accumulatedTrackedKm = template.motorcycle.accumulatedTrackedKm,
                    manualAdjustmentOffsetKm = template.motorcycle.manualAdjustmentOffsetKm,
                    technicalSpecsJson = template.technicalSpecs?.let { json.encodeToString(it) } ?: "{}",
                    startDateIso = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                    isActive = true
                )
                repository.insertMotorcycle(bikeEntity)
                repository.logActivity(
                    targetBikeId,
                    "SYSTEM",
                    "Template Bike Added",
                    "Added ${bikeEntity.displayName} from factory catalog"
                )
            }

            val taskEntities = template.maintenanceSchedule.map { task ->
                MaintenanceTaskEntity(
                    taskId = task.taskId,
                    motorcycleId = targetBikeId,
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
            repository.deduplicateMaintenanceTasksForBike(targetBikeId)
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
