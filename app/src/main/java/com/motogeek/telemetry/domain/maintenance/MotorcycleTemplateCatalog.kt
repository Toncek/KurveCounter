package com.motogeek.telemetry.domain.maintenance

object MotorcycleTemplateCatalog {

    fun getProcedureGuideForTask(taskName: String, category: String, specs: TechnicalSpecsDto?): String {
        return when (category) {
            "Cooling" -> "Sú dva výpustné ventily/skrutky: jeden je vpredu na vodnej pumpe (${specs?.waterPumpDrainBoltTorqueNm ?: 5.0} Nm) a druhý zboku na valci (${specs?.cylinderDrainBoltTorqueNm ?: 9.0} Nm). Potrebujete ${specs?.coolantCapacityLiters ?: 1.5} L chladiacej kvapaliny (${specs?.coolantType?.ifBlank { "etylénglykol 50/50" } ?: "etylénglykol 50/50"}). Postup: 1. Na studenom motore uvoľnite viečko chladiča. 2. Odskrutkujte predný výpustný ventil na vodnej pumpe (5 Nm) a bočný výpustný ventil na valci (9 Nm). 3. Po vypustení nasaďte nové tesniace podložky, dotiahnite na predpísaný moment, nalejte novú kvapalinu a odvzdušnite systém."
            "Engine" -> if (taskName.contains("Oil", ignoreCase = true)) {
                "Výmena motorového oleja a filtra: Výpustná skrutka oleja (${specs?.oilDrainPlugTorqueNm ?: 30.0} Nm). Olejový filter (${specs?.oilFilterTorqueNm ?: 17.0} Nm). Objem oleja s filtrom: ${specs?.oilCapacityLiters ?: 3.0} L, bez filtra: ${specs?.oilCapacityNoFilterLiters ?: 2.7} L. Viskozita: ${specs?.oilViscosityAndSpec?.ifBlank { "10W-40" } ?: "10W-40"}. Postup: Vypustite starý olej, vymeňte tesniacu podložku výpustnej skrutky, dotiahnite, vymeňte filter a nalejte predpísaný objem oleja. Skontrolujte hladinu po zahriatí."
            } else if (taskName.contains("Valve", ignoreCase = true)) {
                "Kontrola vôle ventilov: Skontrolujte sacie (${specs?.valveClearanceIntakeMm.takeIf { !it.isNullOrBlank() } ?: "N/A"} mm) a výfukové (${specs?.valveClearanceExhaustMm.takeIf { !it.isNullOrBlank() } ?: "N/A"} mm) ventily. Postup: Demontujte veko ventilov na studenom motore, nastavte piest do hornej úvrate a zmerajte vôle lístkovými mierkami."
            } else if (taskName.contains("Air", ignoreCase = true)) {
                "Vzduchový filter: Skontrolujte alebo vymeňte vzduchový filter. Vyčistite airbox od nečistôt."
            } else ""
            "Brakes" -> "Servis bŕzd a výmena brzdovej kvapaliny: Predné strmene (${specs?.frontCaliperBoltsTorqueNm ?: 35.0} Nm), zadný strmeň (${specs?.rearCaliperBoltsTorqueNm ?: 27.0} Nm), odvzdušňovacie skrutky (${specs?.brakeBleedNippleTorqueNm ?: 8.0} Nm). Kvapalina: ${specs?.brakeFluidType ?: "DOT 4"}, objem cca ${specs?.brakeFluidVolumeMl ?: 300} ml. Postup: Odsajte starú kvapalinu z nádobky, nalejte novú a postupne odvzdušnite systém od najvzdialenejšieho strmeňa, kým nepotečie čistá kvapalina bez bublín."
            "Tires" -> "Pneumatiky, tlaky a osi kolies: Predný tlak (${specs?.frontTirePressurePsi ?: 36.0} PSI / 2.5 Bar), zadný tlak (${specs?.rearTirePressurePsi ?: 42.0} PSI / 2.9 Bar). Matica zadnej osi (${specs?.rearAxleNutTorqueNm ?: 100.0} Nm), predná os (${specs?.frontAxleTorqueNm ?: 65.0} Nm), zaisťovacie skrutky prednej osi (${specs?.frontPinchBoltsTorqueNm ?: 20.0} Nm). Minimálna hĺbka dezénu 1.6 mm. Skontrolujte stav pneumatík a prípadné poškodenia."
            "Drivetrain" -> "Údržba a napnutie reťaze: Prieves reťaze (${specs?.chainSlackMm ?: "25-35"} mm). Postup: Očistite reťaz vhodným čističom, namažte sprejom na reťaz a skontrolujte rovnomernosť napnutia a opotrebenie roziet."
            "Ignition" -> "Zapaľovacie sviečky: Odporúčaný model: ${specs?.sparkPlugModel.takeIf { !it.isNullOrBlank() } ?: "N/A"}. Medzera: ${specs?.sparkPlugGapMm.takeIf { !it.isNullOrBlank() } ?: "N/A"} mm. Uťahovací moment: ${specs?.sparkPlugTorqueNm ?: 13.0} Nm. Skontrolujte stav izolátora a elektród."
            else -> ""
        }
    }

    fun buildStandardMaintenanceTasks(specs: TechnicalSpecsDto?): List<MaintenanceTaskDto> {
        return listOf(
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Cooling",
                taskName = "Coolant",
                actionType = "Replace",
                intervalMonths = 36,
                notes = getProcedureGuideForTask("Coolant", "Cooling", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Engine",
                taskName = "Engine Oil",
                actionType = "Replace",
                firstBreakInAtKm = 1000.0,
                intervalKm = 10000.0,
                intervalMonths = 12,
                notes = getProcedureGuideForTask("Engine Oil", "Engine", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Brakes",
                taskName = "Brake Fluid",
                actionType = "Replace",
                intervalMonths = 24,
                notes = getProcedureGuideForTask("Brake Fluid", "Brakes", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Tires",
                taskName = "Tires & Axles",
                actionType = "Inspect",
                intervalMonths = 1,
                notes = getProcedureGuideForTask("Tires & Axles", "Tires", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Drivetrain",
                taskName = "Chain & Sprockets",
                actionType = "Clean/Lube",
                intervalKm = 500.0,
                notes = getProcedureGuideForTask("Chain & Sprockets", "Drivetrain", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Engine",
                taskName = "Valve Clearance",
                actionType = "Inspect",
                intervalKm = 24000.0,
                notes = getProcedureGuideForTask("Valve Clearance", "Engine", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Ignition",
                taskName = "Spark Plugs",
                actionType = "Replace",
                intervalKm = 24000.0,
                notes = getProcedureGuideForTask("Spark Plugs", "Ignition", specs)
            ),
            MaintenanceTaskDto(
                taskId = java.util.UUID.randomUUID().toString(),
                category = "Engine",
                taskName = "Air Filter",
                actionType = "Replace",
                intervalKm = 12000.0,
                notes = getProcedureGuideForTask("Air Filter", "Engine", specs)
            )
        )
    }


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
        maintenanceSchedule = buildStandardMaintenanceTasks(TechnicalSpecsDto(
            oilCapacityLiters = 3.2,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 105.0,
            oilDrainPlugTorqueNm = 43.0,
            frontCaliperBoltsTorqueNm = 35.0
        )),
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
        maintenanceSchedule = buildStandardMaintenanceTasks(TechnicalSpecsDto(
            oilCapacityLiters = 2.6,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 98.0,
            oilDrainPlugTorqueNm = 30.0,
            frontCaliperBoltsTorqueNm = 30.0
        )),
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
        maintenanceSchedule = buildStandardMaintenanceTasks(TechnicalSpecsDto(
            oilCapacityLiters = 4.0,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "Shaft Drive",
            rearAxleNutTorqueNm = 60.0,
            oilDrainPlugTorqueNm = 42.0,
            frontCaliperBoltsTorqueNm = 38.0
        )),
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
        maintenanceSchedule = buildStandardMaintenanceTasks(TechnicalSpecsDto(
            oilCapacityLiters = 2.8,
            frontTirePressurePsi = 34.0,
            rearTirePressurePsi = 40.0,
            chainSlackMm = "2-5 (Swingarm)",
            rearAxleNutTorqueNm = 90.0,
            oilDrainPlugTorqueNm = 20.0,
            frontCaliperBoltsTorqueNm = 45.0
        )),
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
        maintenanceSchedule = buildStandardMaintenanceTasks(TechnicalSpecsDto(
            oilCapacityLiters = 3.6,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 108.0,
            oilDrainPlugTorqueNm = 29.0,
            frontCaliperBoltsTorqueNm = 34.0
        )),
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
        maintenanceSchedule = buildStandardMaintenanceTasks(TechnicalSpecsDto(
            oilCapacityLiters = 3.0,
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 100.0,
            oilDrainPlugTorqueNm = 30.0,
            frontCaliperBoltsTorqueNm = 35.0
        )),
        serviceLogs = emptyList()
    )

    val allTemplates = listOf(mt09, cb650r, r1250gs, ktm890, z900, universal)
}
