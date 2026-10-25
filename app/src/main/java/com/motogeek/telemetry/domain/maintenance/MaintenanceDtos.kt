package com.motogeek.telemetry.domain.maintenance

import kotlinx.serialization.Serializable

@Serializable
data class GarageBackupPayload(
    val exportDateIso: String,
    val motorcycles: List<MotorcycleBackupDto>
)

@Serializable
data class MotorcycleBackupDto(
    val motorcycle: MotorcycleDto,
    val technicalSpecs: TechnicalSpecsDto?,
    val maintenanceSchedule: List<MaintenanceTaskDto>,
    val serviceLogs: List<ServiceLogDto>
)

@Serializable
data class MotorcycleDto(
    val id: String,
    val make: String,
    val model: String,
    val year: Int,
    val engineDisplacementCc: Int,
    val baseOdometerKm: Double,
    val accumulatedTrackedKm: Double,
    val manualAdjustmentOffsetKm: Double,
    val isActive: Boolean
)

@Serializable
data class TechnicalSpecsDto(
    val oilCapacityLiters: Double?,
    val frontTirePressurePsi: Double?,
    val rearTirePressurePsi: Double?,
    val chainSlackMm: String?,
    val rearAxleNutTorqueNm: Double?,
    val oilDrainPlugTorqueNm: Double?,
    val frontCaliperBoltsTorqueNm: Double?
)

@Serializable
data class MaintenanceTaskDto(
    val taskId: String,
    val category: String,
    val taskName: String,
    val actionType: String,
    val firstBreakInAtKm: Double? = null,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val warningThresholdKm: Double? = null,
    val warningThresholdDays: Int? = null,
    val oemPartNumbers: List<String> = emptyList(),
    val notes: String = ""
)

@Serializable
data class ServiceLogDto(
    val logId: String,
    val taskId: String,
    val performedAtIsoDate: String,
    val odometerAtServiceKm: Double,
    val costEur: Double,
    val performedBy: String,
    val notes: String
)
