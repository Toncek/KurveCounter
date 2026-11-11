package com.motogeek.telemetry.domain.maintenance

import com.motogeek.telemetry.data.local.AppLogEntity
import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.FuelLogEntity
import com.motogeek.telemetry.data.local.PartLogEntity
import com.motogeek.telemetry.data.local.ReminderEntity
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryPreferencesDto(
    val autoStartAbove5Kmh: Boolean = true,
    val startOnBoot: Boolean = false,
    val manualLeanOffsetDeg: Double = 0.0,
    val appLanguageCode: String = "en"
)

@Serializable
data class RideBackupDto(
    val session: RideSessionEntity,
    val corners: List<CornerEventEntity> = emptyList(),
    val trackPoints: List<TelemetryTrackPointEntity> = emptyList()
)

@Serializable
data class GarageBackupPayload(
    val schemaVersion: Int = 2,
    val exportDateIso: String,
    val motorcycles: List<MotorcycleBackupDto>,
    val preferences: TelemetryPreferencesDto? = null
)

@Serializable
data class MotorcycleInfoV2Dto(
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val engineDisplacementCc: Int = 0
)

@Serializable
data class MotorcycleBackupDto(
    val motorcycle: MotorcycleDto,
    val technicalSpecs: TechnicalSpecsDto? = null,
    val maintenanceSchedule: List<MaintenanceTaskDto> = emptyList(),
    val serviceLogs: List<ServiceLogDto> = emptyList(),
    val fuelLogs: List<FuelLogEntity> = emptyList(),
    val partLogs: List<PartLogEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val rides: List<RideBackupDto> = emptyList(),
    val appLogs: List<AppLogEntity> = emptyList()
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
    val isActive: Boolean,
    val name: String = "",
    val type: String = "MotorCycle",
    val startDateIso: String = "2025-01-01",
    val buyingPrice: Double = 0.0,
    val currencySymbol: String = "€"
)

@Serializable
data class TechnicalSpecsDto(
    val oilCapacityLiters: Double? = null,
    val oilCapacityNoFilterLiters: Double? = null,
    val oilTotalDryCapacityLiters: Double? = null,
    val oilViscosityAndSpec: String = "",
    val coolantCapacityLiters: Double? = null,
    val forkOilCapacityPerLegMl: Double? = null,
    val forkOilWeight: String = "",
    val brakeFluidType: String = "DOT 4",
    val valveClearanceIntakeMm: String = "",
    val valveClearanceExhaustMm: String = "",
    val sparkPlugModel: String = "",
    val sparkPlugGapMm: String = "",
    val idleSpeedRpm: String = "",
    val frontTirePressurePsi: Double? = null,
    val rearTirePressurePsi: Double? = null,
    val chainSlackMm: String? = null,
    val rearAxleNutTorqueNm: Double? = null,
    val oilDrainPlugTorqueNm: Double? = null,
    val waterPumpDrainBoltTorqueNm: Double? = null,
    val cylinderDrainBoltTorqueNm: Double? = null,
    val oilFilterTorqueNm: Double? = null,
    val brakeBleedNippleTorqueNm: Double? = null,
    val rearCaliperBoltsTorqueNm: Double? = null,
    val frontAxleTorqueNm: Double? = null,
    val frontPinchBoltsTorqueNm: Double? = null,
    val sparkPlugTorqueNm: Double? = null,
    val coolantType: String = "",
    val brakeFluidVolumeMl: Int? = null,
    val frontTireSize: String = "",
    val rearTireSize: String = "",
    val frontCaliperBoltsTorqueNm: Double? = null,
    val motorcycleMake: String? = null,
    val motorcycleModel: String? = null,
    val boltTorquesNm: Map<String, Double>? = null,
    val torquesNm: Map<String, Double>? = null
) {
    fun toConsolidatedTorqueMap(): Map<String, Double> {
        val map = linkedMapOf<String, Double>()
        rearAxleNutTorqueNm?.takeIf { it > 0 }?.let { map["Rear Axle Nut"] = it }
        frontAxleTorqueNm?.takeIf { it > 0 }?.let { map["Front Axle"] = it }
        frontPinchBoltsTorqueNm?.takeIf { it > 0 }?.let { map["Front Axle Pinch Bolts"] = it }
        oilDrainPlugTorqueNm?.takeIf { it > 0 }?.let { map["Oil Drain Plug"] = it }
        oilFilterTorqueNm?.takeIf { it > 0 }?.let { map["Oil Filter"] = it }
        frontCaliperBoltsTorqueNm?.takeIf { it > 0 }?.let { map["Front Brake Caliper Bolts"] = it }
        rearCaliperBoltsTorqueNm?.takeIf { it > 0 }?.let { map["Rear Brake Caliper Bolts"] = it }
        brakeBleedNippleTorqueNm?.takeIf { it > 0 }?.let { map["Brake Bleed Nipple"] = it }
        sparkPlugTorqueNm?.takeIf { it > 0 }?.let { map["Spark Plugs"] = it }
        waterPumpDrainBoltTorqueNm?.takeIf { it > 0 }?.let { map["Water Pump Drain Bolt"] = it }
        cylinderDrainBoltTorqueNm?.takeIf { it > 0 }?.let { map["Cylinder Coolant Drain Bolt"] = it }
        torquesNm?.forEach { (k, v) -> if (v.isFinite() && v > 0) map[k] = v }
        boltTorquesNm?.forEach { (k, v) -> if (v.isFinite() && v > 0) map[k] = v }
        return map
    }
}

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
    val notes: String,
    val serviceTitle: String = "Periodic Maintenance",
    val locationOrShop: String = "Home Garage",
    val servicedAtKm: Double = 0.0,
    val limitKm: Double = 5000.0,
    val endKm: Double = 5000.0,
    val checklistItemsJson: String = "[]"
)
