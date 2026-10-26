package com.motogeek.telemetry.domain.maintenance

object MotorcycleTemplateCatalog {

    val mt09 = MotorcycleBackupDto(
        motorcycle = MotorcycleDto(
            id = "template_mt09",
            make = "Yamaha",
            model = "MT-09 / Tracer 9",
            year = 2024,
            engineDisplacementCc = 890,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = true
        ),
        technicalSpecs = TechnicalSpecsDto(
            oilCapacityLiters = 3.2,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 105.0,
            oilDrainPlugTorqueNm = 43.0,
            frontCaliperBoltsTorqueNm = 35.0
        ),
        maintenanceSchedule = listOf(
            MaintenanceTaskDto(taskId = "mt09_oil", category = "Engine", taskName = "Engine Oil", actionType = "Replace", firstBreakInAtKm = 1000.0, intervalKm = 10000.0, intervalMonths = 12, notes = "10W-40"),
            MaintenanceTaskDto(taskId = "mt09_chain", category = "Drivetrain", taskName = "Chain & Sprockets", actionType = "Clean/Lube", intervalKm = 500.0, notes = "Lube chain"),
            MaintenanceTaskDto(taskId = "mt09_valves", category = "Engine", taskName = "Valve Clearance", actionType = "Inspect", intervalKm = 40000.0)
        ),
        serviceLogs = emptyList()
    )

    val cb650r = MotorcycleBackupDto(
        motorcycle = MotorcycleDto(
            id = "template_cb650r",
            make = "Honda",
            model = "CB650R / CBR650R",
            year = 2024,
            engineDisplacementCc = 649,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = true
        ),
        technicalSpecs = TechnicalSpecsDto(
            oilCapacityLiters = 2.6,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 98.0,
            oilDrainPlugTorqueNm = 30.0,
            frontCaliperBoltsTorqueNm = 30.0
        ),
        maintenanceSchedule = listOf(
            MaintenanceTaskDto(taskId = "cb650r_oil", category = "Engine", taskName = "Engine Oil", actionType = "Replace", firstBreakInAtKm = 1000.0, intervalKm = 12000.0, intervalMonths = 12, notes = "10W-30"),
            MaintenanceTaskDto(taskId = "cb650r_chain", category = "Drivetrain", taskName = "Chain & Sprockets", actionType = "Clean/Lube", intervalKm = 500.0)
        ),
        serviceLogs = emptyList()
    )

    val r1250gs = MotorcycleBackupDto(
        motorcycle = MotorcycleDto(
            id = "template_r1250gs",
            make = "BMW",
            model = "R 1250 GS",
            year = 2023,
            engineDisplacementCc = 1254,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = true
        ),
        technicalSpecs = TechnicalSpecsDto(
            oilCapacityLiters = 4.0,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "Shaft Drive",
            rearAxleNutTorqueNm = 60.0,
            oilDrainPlugTorqueNm = 42.0,
            frontCaliperBoltsTorqueNm = 38.0
        ),
        maintenanceSchedule = listOf(
            MaintenanceTaskDto(taskId = "gs_oil", category = "Engine", taskName = "Engine Oil", actionType = "Replace", firstBreakInAtKm = 1000.0, intervalKm = 10000.0, intervalMonths = 12, notes = "5W-40"),
            MaintenanceTaskDto(taskId = "gs_shaft", category = "Drivetrain", taskName = "Shaft Drive Oil", actionType = "Replace", intervalKm = 20000.0, intervalMonths = 24)
        ),
        serviceLogs = emptyList()
    )

    val ktm890 = MotorcycleBackupDto(
        motorcycle = MotorcycleDto(
            id = "template_ktm890",
            make = "KTM",
            model = "890 Duke / Adventure",
            year = 2024,
            engineDisplacementCc = 889,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = true
        ),
        technicalSpecs = TechnicalSpecsDto(
            oilCapacityLiters = 2.8,
            frontTirePressurePsi = 34.0,
            rearTirePressurePsi = 40.0,
            chainSlackMm = "2-5 (Swingarm)",
            rearAxleNutTorqueNm = 90.0,
            oilDrainPlugTorqueNm = 20.0,
            frontCaliperBoltsTorqueNm = 45.0
        ),
        maintenanceSchedule = listOf(
            MaintenanceTaskDto(taskId = "ktm_oil", category = "Engine", taskName = "Engine Oil", actionType = "Replace", firstBreakInAtKm = 1000.0, intervalKm = 15000.0, intervalMonths = 12, notes = "10W-50"),
            MaintenanceTaskDto(taskId = "ktm_chain", category = "Drivetrain", taskName = "Chain", actionType = "Clean/Lube", intervalKm = 500.0)
        ),
        serviceLogs = emptyList()
    )

    val z900 = MotorcycleBackupDto(
        motorcycle = MotorcycleDto(
            id = "template_z900",
            make = "Kawasaki",
            model = "Z900 / Ninja 650",
            year = 2024,
            engineDisplacementCc = 948,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = true
        ),
        technicalSpecs = TechnicalSpecsDto(
            oilCapacityLiters = 3.6,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 108.0,
            oilDrainPlugTorqueNm = 29.0,
            frontCaliperBoltsTorqueNm = 34.0
        ),
        maintenanceSchedule = listOf(
            MaintenanceTaskDto(taskId = "z900_oil", category = "Engine", taskName = "Engine Oil", actionType = "Replace", firstBreakInAtKm = 1000.0, intervalKm = 12000.0, intervalMonths = 12, notes = "10W-40"),
            MaintenanceTaskDto(taskId = "z900_chain", category = "Drivetrain", taskName = "Chain", actionType = "Clean/Lube", intervalKm = 500.0)
        ),
        serviceLogs = emptyList()
    )

    val universal = MotorcycleBackupDto(
        motorcycle = MotorcycleDto(
            id = "template_universal",
            make = "Universal",
            model = "Street Motorcycle",
            year = 2024,
            engineDisplacementCc = 600,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = true
        ),
        technicalSpecs = TechnicalSpecsDto(
            oilCapacityLiters = 3.0,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 100.0,
            oilDrainPlugTorqueNm = 30.0,
            frontCaliperBoltsTorqueNm = 35.0
        ),
        maintenanceSchedule = listOf(
            MaintenanceTaskDto(taskId = "uni_oil", category = "Engine", taskName = "Engine Oil", actionType = "Replace", firstBreakInAtKm = 1000.0, intervalKm = 10000.0, intervalMonths = 12),
            MaintenanceTaskDto(taskId = "uni_chain", category = "Drivetrain", taskName = "Chain", actionType = "Clean/Lube", intervalKm = 500.0),
            MaintenanceTaskDto(taskId = "uni_valves", category = "Engine", taskName = "Valve Clearance", actionType = "Inspect", intervalKm = 24000.0),
            MaintenanceTaskDto(taskId = "uni_plugs", category = "Ignition", taskName = "Spark Plugs", actionType = "Replace", intervalKm = 24000.0),
            MaintenanceTaskDto(taskId = "uni_air", category = "Engine", taskName = "Air Filter", actionType = "Replace", intervalKm = 12000.0),
            MaintenanceTaskDto(taskId = "uni_brake", category = "Brakes", taskName = "Brake Fluid", actionType = "Replace", intervalMonths = 24),
            MaintenanceTaskDto(taskId = "uni_coolant", category = "Cooling", taskName = "Coolant", actionType = "Replace", intervalMonths = 36)
        ),
        serviceLogs = emptyList()
    )

    val allTemplates = listOf(mt09, cb650r, r1250gs, ktm890, z900, universal)
}
