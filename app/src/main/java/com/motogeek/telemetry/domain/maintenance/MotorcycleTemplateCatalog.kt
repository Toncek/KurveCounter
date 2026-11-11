package com.motogeek.telemetry.domain.maintenance

object MotorcycleTemplateCatalog {

    fun getProcedureGuideForTask(
        taskName: String,
        category: String,
        specs: TechnicalSpecsDto?,
        langCode: String = "en"
    ): String {
        val isSk = langCode.equals("sk", ignoreCase = true)
        return when {
            category.equals("Cooling", ignoreCase = true) || taskName.contains("Coolant", ignoreCase = true) || taskName.contains("Chlad", ignoreCase = true) -> {
                val wpTorque = specs?.waterPumpDrainBoltTorqueNm ?: 10.0
                val cylTorque = specs?.cylinderDrainBoltTorqueNm ?: 10.0
                val capL = specs?.coolantCapacityLiters ?: 1.8
                val type = specs?.coolantType?.ifBlank { if (isSk) "etylénglykol 50/50" else "Ethylene Glycol 50/50" } ?: if (isSk) "etylénglykol 50/50" else "Ethylene Glycol 50/50"
                if (isSk) {
                    "Špecifikácia: Objem $capL L ($type) • Výpustná skrutka vodnej pumpy: $wpTorque Nm • Výpustná skrutka valca: $cylTorque Nm.\n" +
                        "Postup: 1. Na úplne studenom motore uvoľnite viečko chladiča. " +
                        "2. Odskrutkujte výpustnú skrutku na vodnej pumpe ($wpTorque Nm) a na valci ($cylTorque Nm) a vypustite kvapalinu. " +
                        "3. Nasaďte nové tesniace podložky a dotiahnite skrutky na predpísaný moment. " +
                        "4. Pomaly nalejte novú kvapalinu, naštartujte na odvzdušnenie a doplňte expanznú nádobku po rysku FULL."
                } else {
                    "Specs: Capacity $capL L ($type) • Water Pump Drain Bolt: $wpTorque Nm • Cylinder Drain Bolt: $cylTorque Nm.\n" +
                        "Procedure: 1. Ensure engine is completely cold and remove radiator cap. " +
                        "2. Remove water pump drain bolt ($wpTorque Nm) and cylinder drain bolt ($cylTorque Nm) to drain old coolant. " +
                        "3. Install new copper/aluminum crush washers and torque drain bolts to spec. " +
                        "4. Fill radiator slowly to the neck, bleed trapped air by idling briefly, and top up expansion reservoir to FULL."
                }
            }
            category.equals("Engine", ignoreCase = true) && (taskName.contains("Oil", ignoreCase = true) || taskName.contains("Olej", ignoreCase = true)) -> {
                val drainTorque = specs?.oilDrainPlugTorqueNm ?: 30.0
                val filterTorque = specs?.oilFilterTorqueNm ?: 17.0
                val capFilter = specs?.oilCapacityLiters ?: 3.0
                val capNoFilter = specs?.oilCapacityNoFilterLiters ?: (capFilter - 0.3).coerceAtLeast(1.0)
                val visc = specs?.oilViscosityAndSpec?.ifBlank { "10W-40 JASO MA2" } ?: "10W-40 JASO MA2"
                if (isSk) {
                    "Špecifikácia: S filtrom $capFilter L (bez filtra ${String.format("%.1f", capNoFilter)} L) • Viskozita: $visc • Výpustná skrutka: $drainTorque Nm • Filter: $filterTorque Nm.\n" +
                        "Postup: 1. Zahrejte motor na 3 min, vypnite a uvoľnite nalievacie viečko. " +
                        "2. Odskrutkujte výpustnú skrutku ($drainTorque Nm) a olejový filter ($filterTorque Nm). " +
                        "3. Potrite tesnenie nového filtra olejom, dotiahnite filter na $filterTorque Nm, dajte novú podložku pod výpustnú skrutku a dotiahnite na $drainTorque Nm. " +
                        "4. Nalejte $capFilter L oleja $visc, nechajte 2 min bežať na voľnobeh a skontrolujte hladinu v priezore/mierkou."
                } else {
                    "Specs: With Filter $capFilter L (Without Filter ${String.format("%.1f", capNoFilter)} L) • Grade: $visc • Drain Plug: $drainTorque Nm • Oil Filter: $filterTorque Nm.\n" +
                        "Procedure: 1. Warm engine for 3 min, shut off, and remove oil filler cap. " +
                        "2. Remove oil pan drain bolt ($drainTorque Nm) and spin-on oil filter ($filterTorque Nm). " +
                        "3. Lightly oil the new filter O-ring, torque filter to $filterTorque Nm, fit a new crush washer on the drain plug, and torque to $drainTorque Nm. " +
                        "4. Pour in $capFilter L of $visc oil, idle for 2 min, check for leaks, and verify sight glass/dipstick level on level ground."
                }
            }
            category.equals("Engine", ignoreCase = true) && (taskName.contains("Valve", ignoreCase = true) || taskName.contains("Ventil", ignoreCase = true)) -> {
                val intake = specs?.valveClearanceIntakeMm?.takeIf { it.isNotBlank() } ?: "0.10–0.20"
                val exhaust = specs?.valveClearanceExhaustMm?.takeIf { it.isNotBlank() } ?: "0.20–0.30"
                if (isSk) {
                    "Špecifikácia (studený motor): Sacie ventily: $intake mm • Výfukové ventily: $exhaust mm.\n" +
                        "Postup: 1. Na studenom motore (<35°C) demontujte nádrž, airbox a veko ventilov. " +
                        "2. Nastavte piest do hornej úvrate (TDC) kompresného zdvihu. " +
                        "3. Zmerajte vôle lístkovými mierkami, zapíšte hodnoty a pri odchýlke vymeňte podložky (shimy)."
                } else {
                    "Specs (Cold Engine): Intake Clearance: $intake mm • Exhaust Clearance: $exhaust mm.\n" +
                        "Procedure: 1. With engine stone cold (<35°C), remove fuel tank, airbox, and valve cover. " +
                        "2. Rotate crankshaft to TDC on compression stroke for each cylinder. " +
                        "3. Measure clearance between cam lobes and buckets/rockers using feeler gauges; record values and reshim if outside spec."
                }
            }
            category.equals("Engine", ignoreCase = true) && (taskName.contains("Air", ignoreCase = true) || taskName.contains("Vzduch", ignoreCase = true)) -> {
                if (isSk) {
                    "Špecifikácia: Kontrola / výmena vzduchového filtra a vyčistenie airboxu.\n" +
                        "Postup: 1. Nadvihnite nádrž a odskrutkujte veko airboxu. " +
                        "2. Skontrolujte znečistenie vložky filtra; papierový vymeňte, športový vyčistite a naimpregnujte. " +
                        "3. Vytite vnútro airboxu a vypustite odkalovaciu hadičku odvetrania kľukovky."
                } else {
                    "Specs: Inspect/replace air filter element and clean airbox housing.\n" +
                        "Procedure: 1. Lift or remove fuel tank and unbolt airbox cover. " +
                        "2. Inspect filter pleats for debris or oil saturation; replace paper element or clean/re-oil reusable element. " +
                        "3. Wipe inside of airbox clean and drain crankcase breather catch tube."
                }
            }
            category.equals("Brakes", ignoreCase = true) || taskName.contains("Brake", ignoreCase = true) || taskName.contains("Brzd", ignoreCase = true) -> {
                val frontCal = specs?.frontCaliperBoltsTorqueNm ?: 35.0
                val rearCal = specs?.rearCaliperBoltsTorqueNm ?: 27.0
                val bleed = specs?.brakeBleedNippleTorqueNm ?: 8.0
                val fluidType = specs?.brakeFluidType?.ifBlank { "DOT 4" } ?: "DOT 4"
                val volMl = specs?.brakeFluidVolumeMl ?: 300
                if (isSk) {
                    "Špecifikácia: Kvapalina $fluidType (~$volMl ml) • Predné strmene: $frontCal Nm • Zadný strmeň: $rearCal Nm • Odvzdušňovacie skrutky: $bleed Nm.\n" +
                        "Postup: 1. Zakryte lakované časti handrou. " +
                        "2. Odsajte starú kvapalinu z nádobky a nalejte novú $fluidType. " +
                        "3. Odvzdušňujte od najvzdialenejšieho strmeňa, kým netečie čistá kvapalina bez bublín; dotiahnite odvzdušňovák na $bleed Nm."
                } else {
                    "Specs: Fluid $fluidType (~$volMl ml) • Front Caliper Bolts: $frontCal Nm • Rear Caliper Bolts: $rearCal Nm • Bleed Nipples: $bleed Nm.\n" +
                        "Procedure: 1. Cover painted surfaces with shop towels. " +
                        "2. Syringe old fluid from master cylinder reservoir and refill with fresh sealed $fluidType. " +
                        "3. Bleed from furthest caliper to closest until fluid runs crystal clear and bubble-free; torque bleed nipples to $bleed Nm and verify firm lever feel."
                }
            }
            category.equals("Tires", ignoreCase = true) || taskName.contains("Tire", ignoreCase = true) || taskName.contains("Axle", ignoreCase = true) || taskName.contains("Pneu", ignoreCase = true) -> {
                val frontPsi = specs?.frontTirePressurePsi ?: 36.0
                val rearPsi = specs?.rearTirePressurePsi ?: 42.0
                val frontBar = String.format("%.2f", frontPsi / 14.5038)
                val rearBar = String.format("%.2f", rearPsi / 14.5038)
                val rearAxle = specs?.rearAxleNutTorqueNm ?: 100.0
                val frontAxle = specs?.frontAxleTorqueNm ?: 65.0
                val pinch = specs?.frontPinchBoltsTorqueNm ?: 20.0
                if (isSk) {
                    "Špecifikácia: Tlak za studena vpredu $frontPsi PSI ($frontBar Bar) / vzadu $rearPsi PSI ($rearBar Bar) • Matica zadnej osi: $rearAxle Nm • Predná os: $frontAxle Nm • Zaisťovacie skrutky: $pinch Nm.\n" +
                        "Postup: 1. Zmerajte tlaky pred jazdou na studených pneumatikách. " +
                        "2. Skontrolujte hĺbku dezénu (min. 1.6 mm / odporúčané 2.0 mm) a prípadné praskliny. " +
                        "3. Pri demontáži kolies dotiahnite zadnú os na $rearAxle Nm a prednú os na $frontAxle Nm (zaisťovacie skrutky $pinch Nm)."
                } else {
                    "Specs: Cold Pressure Front $frontPsi PSI ($frontBar Bar) / Rear $rearPsi PSI ($rearBar Bar) • Rear Axle Nut: $rearAxle Nm • Front Axle: $frontAxle Nm • Pinch Bolts: $pinch Nm.\n" +
                        "Procedure: 1. Check cold tire pressures before riding. " +
                        "2. Inspect tread depth (minimum 1.6 mm / 2.0 mm recommended) and check sidewalls for cracks or punctures. " +
                        "3. Verify axle nut ($rearAxle Nm) and front axle/pinch bolts ($frontAxle Nm / $pinch Nm) when servicing wheels."
                }
            }
            category.equals("Drivetrain", ignoreCase = true) || taskName.contains("Chain", ignoreCase = true) || taskName.contains("Reťaz", ignoreCase = true) -> {
                val slack = specs?.chainSlackMm ?: "25-35"
                val rearAxle = specs?.rearAxleNutTorqueNm ?: 100.0
                if (isSk) {
                    "Špecifikácia: Prieves reťaze: $slack mm • Matica zadnej osi: $rearAxle Nm.\n" +
                        "Postup: 1. Vyčistite reťaz čističom a jemnou kefou. " +
                        "2. Zmerajte prieves reťaze ($slack mm) v strede medzi rozetami na najtesnejšom mieste. " +
                        "3. Pri napínaní uvoľnite os ($rearAxle Nm), nastavte napínaky rovnomerne na oboch stranách, dotiahnite maticu osi na $rearAxle Nm a namažte reťaz."
                } else {
                    "Specs: Chain Free Play / Drive Spec: $slack mm • Rear Axle Nut: $rearAxle Nm.\n" +
                        "Procedure: 1. Clean chain thoroughly with kerosene-based chain cleaner and a soft brush. " +
                        "2. Measure vertical chain slack ($slack mm) midway between sprockets at tightest spot. " +
                        "3. If adjusting, loosen rear axle ($rearAxle Nm), turn left/right adjusters equally to maintain alignment marks, retorque axle to $rearAxle Nm, and apply chain lube to inner rollers."
                }
            }
            category.equals("Ignition", ignoreCase = true) || taskName.contains("Spark", ignoreCase = true) || taskName.contains("Sviečk", ignoreCase = true) -> {
                val model = specs?.sparkPlugModel?.takeIf { it.isNotBlank() } ?: "OEM Iridium/Standard"
                val gap = specs?.sparkPlugGapMm?.takeIf { it.isNotBlank() } ?: "0.8–0.9"
                val torque = specs?.sparkPlugTorqueNm ?: 13.0
                if (isSk) {
                    "Špecifikácia: Model sviečky: $model • Odtrh elektród: $gap mm • Uťahovací moment: $torque Nm.\n" +
                        "Postup: 1. Vyfúkajte okolie cievok stlačeným vzduchom pred demontážou. " +
                        "2. Vymontujte staré sviečky a skontrolujte sfarbenie izolátora. " +
                        "3. Overte medzeru ($gap mm), naskrutkujte nové sviečky rukou a dotiahnite momentovým kľúčom na $torque Nm."
                } else {
                    "Specs: Plug Model: $model • Electrode Gap: $gap mm • Torque: $torque Nm.\n" +
                        "Procedure: 1. Blow compressed air around spark plug wells before removing ignition coils. " +
                        "2. Remove old plugs and inspect insulator color. " +
                        "3. Verify gap ($gap mm), thread new plugs in by hand to prevent cross-threading, and torque to $torque Nm."
                }
            }
            else -> ""
        }
    }

    fun buildStandardMaintenanceTasks(specs: TechnicalSpecsDto?, langCode: String = "en"): List<MaintenanceTaskDto> {
        val isSk = langCode.equals("sk", ignoreCase = true)
        return listOf(
            MaintenanceTaskDto(
                taskId = "STD_COOLANT",
                category = "Cooling",
                taskName = if (isSk) "Chladiaca kvapalina" else "Coolant Replacement",
                actionType = if (isSk) "Vymeniť" else "Replace",
                intervalKm = 30000.0,
                intervalMonths = 36,
                warningThresholdKm = 1000.0,
                warningThresholdDays = 30,
                notes = getProcedureGuideForTask("Coolant", "Cooling", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_ENGINE_OIL",
                category = "Engine",
                taskName = if (isSk) "Motorový olej a filter" else "Engine Oil & Filter",
                actionType = if (isSk) "Vymeniť" else "Replace",
                firstBreakInAtKm = 1000.0,
                intervalKm = 10000.0,
                intervalMonths = 12,
                warningThresholdKm = 500.0,
                warningThresholdDays = 30,
                notes = getProcedureGuideForTask("Engine Oil", "Engine", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_BRAKE_FLUID",
                category = "Brakes",
                taskName = if (isSk) "Brzdová kvapalina" else "Brake Fluid Flush",
                actionType = if (isSk) "Vymeniť" else "Replace",
                intervalKm = 20000.0,
                intervalMonths = 24,
                warningThresholdKm = 1000.0,
                warningThresholdDays = 30,
                notes = getProcedureGuideForTask("Brake Fluid", "Brakes", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_TIRES_AXLES",
                category = "Tires",
                taskName = if (isSk) "Pneumatiky, tlaky a osi kolies" else "Tires, Pressures & Axles",
                actionType = if (isSk) "Skontrolovať" else "Inspect",
                intervalKm = 2000.0,
                intervalMonths = 1,
                warningThresholdKm = 250.0,
                warningThresholdDays = 7,
                notes = getProcedureGuideForTask("Tires & Axles", "Tires", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_CHAIN_LUBE",
                category = "Drivetrain",
                taskName = if (isSk) "Čistenie, mazanie a napnutie reťaze" else "Chain Clean, Lube & Slack",
                actionType = if (isSk) "Vyčistiť/Namazať" else "Clean/Lube",
                intervalKm = 800.0,
                warningThresholdKm = 150.0,
                notes = getProcedureGuideForTask("Chain & Sprockets", "Drivetrain", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_VALVE_CLEARANCE",
                category = "Engine",
                taskName = if (isSk) "Kontrola vôle ventilov" else "Valve Clearance Check",
                actionType = if (isSk) "Skontrolovať" else "Inspect",
                intervalKm = 24000.0,
                warningThresholdKm = 1500.0,
                notes = getProcedureGuideForTask("Valve Clearance", "Engine", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_SPARK_PLUGS",
                category = "Ignition",
                taskName = if (isSk) "Zapaľovacie sviečky" else "Spark Plugs",
                actionType = if (isSk) "Vymeniť" else "Replace",
                intervalKm = 20000.0,
                warningThresholdKm = 1000.0,
                notes = getProcedureGuideForTask("Spark Plugs", "Ignition", specs, langCode)
            ),
            MaintenanceTaskDto(
                taskId = "STD_AIR_FILTER",
                category = "Engine",
                taskName = if (isSk) "Vzduchový filter" else "Air Filter Element",
                actionType = if (isSk) "Vymeniť" else "Replace",
                intervalKm = 15000.0,
                warningThresholdKm = 1000.0,
                notes = getProcedureGuideForTask("Air Filter", "Engine", specs, langCode)
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
            oilCapacityNoFilterLiters = 2.8,
            oilViscosityAndSpec = "10W-40 Yamalube JASO MA2",
            coolantCapacityLiters = 1.9,
            coolantType = "Ethylene Glycol 50/50",
            brakeFluidType = "DOT 4",
            brakeFluidVolumeMl = 300,
            valveClearanceIntakeMm = "0.11-0.20",
            valveClearanceExhaustMm = "0.26-0.30",
            sparkPlugModel = "NGK LMAR9A-9",
            sparkPlugGapMm = "0.8-0.9",
            frontTireSize = "120/70 ZR17",
            rearTireSize = "180/55 ZR17",
            frontTirePressurePsi = 36.0,
            rearTirePressurePsi = 42.0,
            chainSlackMm = "25-35",
            rearAxleNutTorqueNm = 105.0,
            frontAxleTorqueNm = 65.0,
            frontPinchBoltsTorqueNm = 23.0,
            oilDrainPlugTorqueNm = 43.0,
            oilFilterTorqueNm = 17.0,
            frontCaliperBoltsTorqueNm = 35.0,
            rearCaliperBoltsTorqueNm = 27.0,
            brakeBleedNippleTorqueNm = 6.0,
            sparkPlugTorqueNm = 13.0,
            waterPumpDrainBoltTorqueNm = 10.0,
            cylinderDrainBoltTorqueNm = 10.0
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
