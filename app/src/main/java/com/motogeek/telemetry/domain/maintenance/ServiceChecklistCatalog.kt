package com.motogeek.telemetry.domain.maintenance

import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.ServiceChecklistItem

data class ServiceSubOption(
    val key: String,
    val labelEn: String,
    val labelSk: String,
    val completesReplacementTask: Boolean = false,
    val completesServiceTask: Boolean = false,
    val completesInspectionTask: Boolean = false
) {
    fun label(langCode: String): String =
        if (langCode.equals("sk", ignoreCase = true)) labelSk else labelEn
}

data class GeneralWorkshopCheckTemplate(
    val key: String,
    val nameEn: String,
    val nameSk: String,
    val category: String,
    val actionType: String
) {
    fun name(langCode: String): String =
        if (langCode.equals("sk", ignoreCase = true)) nameSk else nameEn
}

object ServiceChecklistCatalog {

    val standardGeneralChecks: List<GeneralWorkshopCheckTemplate> = listOf(
        GeneralWorkshopCheckTemplate("GEN_ENGINE_OIL", "Engine Oil", "Motorový olej", "Engine", "Replace"),
        GeneralWorkshopCheckTemplate("GEN_OIL_FILTER", "Engine Oil Filter", "Olejový filter", "Engine", "Replace"),
        GeneralWorkshopCheckTemplate("GEN_AIR_FILTER", "Air Filter", "Vzduchový filter", "Engine", "Replace"),
        GeneralWorkshopCheckTemplate("GEN_SPARK_PLUGS", "Spark Plugs", "Zapaľovacie sviečky", "Ignition", "Replace"),
        GeneralWorkshopCheckTemplate("GEN_CHAIN", "Drive Chain & Sprockets", "Hnacia reťaz a rozety", "Drivetrain", "Clean/Lube"),
        GeneralWorkshopCheckTemplate("GEN_BRAKE_PADS", "Brake Pads Front/Rear", "Brzdové platničky (P/Z)", "Brakes", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_BRAKE_FLUID", "Brake Fluid", "Brzdová kvapalina", "Brakes", "Replace"),
        GeneralWorkshopCheckTemplate("GEN_COOLANT", "Coolant / Radiator", "Chladiaca kvapalina / chladič", "Cooling", "Replace"),
        GeneralWorkshopCheckTemplate("GEN_TIRES", "Tire Pressure & Tread", "Tlak a dezén pneumatík", "Tires", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_CLUTCH_THROTTLE", "Clutch & Throttle Freeplay", "Vôľa spojky a plynu", "Controls", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_STEERING", "Steering Head Bearings", "Ložiská krku riadenia", "Chassis", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_FORKS", "Front Fork Seals & Oil", "Guferá a olej prednej vidlice", "Suspension", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_REAR_SHOCK", "Rear Shock Absorber", "Zadný tlmič pruženia", "Suspension", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_BATTERY", "Battery & Charging System", "Batéria a dobíjanie", "Electrical", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_LIGHTS", "Lights & Indicators", "Svetlá a smerovky", "Electrical", "Inspect"),
        GeneralWorkshopCheckTemplate("GEN_BOLTS", "Bolts & Fasteners Torque", "Dotiahnutie skrutiek a spojov", "Chassis", "Inspect")
    )

    fun remainingGeneralChecks(existingTasks: List<MaintenanceTaskEntity>): List<GeneralWorkshopCheckTemplate> {
        return standardGeneralChecks.filter { template ->
            existingTasks.none { task -> doesGeneralTemplateOverlapTask(template, task) }
        }
    }

    private fun doesGeneralTemplateOverlapTask(
        template: GeneralWorkshopCheckTemplate,
        task: MaintenanceTaskEntity
    ): Boolean {
        val id = task.taskId.uppercase()
        val name = task.taskName.lowercase()
        val cat = task.category.lowercase()
        return when (template.key) {
            "GEN_ENGINE_OIL" -> id == "STD_ENGINE_OIL" ||
                ((name.contains("oil") || name.contains("olej")) && !name.contains("fork") && !name.contains("vidlic"))
            "GEN_OIL_FILTER" -> id == "STD_ENGINE_OIL" ||
                (name.contains("oil filter") || name.contains("olejový filter") ||
                    ((name.contains("oil") || name.contains("olej")) && name.contains("filter")))
            "GEN_AIR_FILTER" -> id == "STD_AIR_FILTER" ||
                name.contains("air filter") || name.contains("vzduchový filter") || name.contains("air")
            "GEN_SPARK_PLUGS" -> id == "STD_SPARK_PLUGS" ||
                cat == "ignition" || name.contains("spark") || name.contains("sviečk")
            "GEN_CHAIN" -> id == "STD_CHAIN_LUBE" ||
                cat == "drivetrain" || name.contains("chain") || name.contains("reťaz")
            "GEN_BRAKE_PADS" -> name.contains("pad") || name.contains("platnič") || name.contains("doštič")
            "GEN_BRAKE_FLUID" -> id == "STD_BRAKE_FLUID" ||
                (name.contains("brake") && name.contains("fluid")) ||
                name.contains("brzdová kvapalina") || name == "brzdy" || cat == "brakes"
            "GEN_COOLANT" -> id == "STD_COOLANT" ||
                cat == "cooling" || name.contains("coolant") || name.contains("water") ||
                name.contains("chlad") || name.contains("voda")
            "GEN_TIRES" -> id == "STD_TIRES_AXLES" ||
                cat == "tires" || name.contains("tire") || name.contains("tyre") || name.contains("pneu")
            "GEN_CLUTCH_THROTTLE" -> name.contains("clutch") || name.contains("throttle") ||
                name.contains("spojk") || name.contains("plyn")
            "GEN_STEERING" -> name.contains("steering") || name.contains("krk riadenia")
            "GEN_FORKS" -> name.contains("fork") || name.contains("vidlic")
            "GEN_REAR_SHOCK" -> name.contains("shock") || name.contains("tlmič")
            "GEN_BATTERY" -> name.contains("battery") || name.contains("batér")
            "GEN_LIGHTS" -> name.contains("light") || name.contains("svetl")
            "GEN_BOLTS" -> name.contains("bolt") || name.contains("torque") || name.contains("skrut")
            else -> false
        }
    }

    fun getSubOptionsFor(
        taskId: String?,
        taskName: String,
        category: String
    ): List<ServiceSubOption> {
        val id = taskId?.uppercase().orEmpty()
        val n = taskName.lowercase()
        val c = category.lowercase()

        return when {
            // Standalone Oil Filter item (not combined Engine Oil & Filter)
            (id == "GEN_OIL_FILTER" || ((n.contains("oil filter") || n.contains("olejový filter")) && !n.contains("engine") && !n.contains("motorový"))) -> listOf(
                ServiceSubOption("OIL_FILTER_CHECKED", "Filter Checked", "Skontrolovaný", completesInspectionTask = true),
                ServiceSubOption("OIL_FILTER_CHANGED", "Filter Changed", "Filter vymenený", completesReplacementTask = true)
            )
            // Engine Oil & Filter (or Engine Oil)
            id == "STD_ENGINE_OIL" || id == "GEN_ENGINE_OIL" ||
                ((n.contains("oil") || n.contains("olej")) && !n.contains("fork") && !n.contains("vidlic")) -> listOf(
                ServiceSubOption("OIL_CHECKED", "Checked Level", "Skontrolovaná hladina", completesInspectionTask = true),
                ServiceSubOption("OIL_CHANGED", "Oil Changed", "Olej vymenený", completesReplacementTask = true),
                ServiceSubOption("OIL_FILTER_CHANGED", "Filter Changed", "Filter vymenený", completesServiceTask = true)
            )
            // Coolant / Water
            id == "STD_COOLANT" || id == "GEN_COOLANT" || c == "cooling" ||
                n.contains("coolant") || n.contains("water") || n.contains("chlad") || n.contains("voda") -> listOf(
                ServiceSubOption("COOLANT_CHECKED", "Water / Coolant Checked", "Hladina skontrolovaná", completesInspectionTask = true),
                ServiceSubOption("COOLANT_CHANGED", "Water / Coolant Changed", "Kvapalina vymenená", completesReplacementTask = true),
                ServiceSubOption("COOLANT_BLED", "System Bled", "Odvzdušnené", completesServiceTask = true)
            )
            // Air Filter
            id == "STD_AIR_FILTER" || id == "GEN_AIR_FILTER" || n.contains("air") || n.contains("vzduch") -> listOf(
                ServiceSubOption("AIR_CHECKED", "Checked", "Skontrolovaný", completesInspectionTask = true),
                ServiceSubOption("AIR_CLEANED", "Cleaned / Re-oiled", "Vyčistený / naimpregnovaný", completesServiceTask = true),
                ServiceSubOption("AIR_CHANGED", "Filter Changed", "Filter vymenený", completesReplacementTask = true)
            )
            // Spark Plugs
            id == "STD_SPARK_PLUGS" || id == "GEN_SPARK_PLUGS" || c == "ignition" ||
                n.contains("spark") || n.contains("sviečk") -> listOf(
                ServiceSubOption("PLUG_CHECKED", "Checked / Gap OK", "Skontrolované / odtrh", completesInspectionTask = true),
                ServiceSubOption("PLUG_CHANGED", "Plugs Changed", "Sviečky vymenené", completesReplacementTask = true)
            )
            // Drive Chain & Sprockets
            id == "STD_CHAIN_LUBE" || id == "GEN_CHAIN" || c == "drivetrain" ||
                n.contains("chain") || n.contains("reťaz") -> listOf(
                ServiceSubOption("CHAIN_CHECKED", "Slack Checked", "Vôľa skontrolovaná", completesInspectionTask = true),
                ServiceSubOption("CHAIN_CLEANED_LUBED", "Cleaned & Lubed", "Vyčistená a namazaná", completesServiceTask = true),
                ServiceSubOption("CHAIN_ADJUSTED", "Tension Adjusted", "Napnutie nastavené", completesServiceTask = true),
                ServiceSubOption("CHAIN_REPLACED", "Chain / Sprockets Changed", "Sada vymenená", completesReplacementTask = true)
            )
            // Brake Fluid specifically
            id == "STD_BRAKE_FLUID" || id == "GEN_BRAKE_FLUID" ||
                ((n.contains("fluid") || n.contains("kvapal")) && (n.contains("brake") || n.contains("brzd"))) -> listOf(
                ServiceSubOption("BRAKE_FLUID_CHECKED", "Level Checked", "Hladina skontrolovaná", completesInspectionTask = true),
                ServiceSubOption("BRAKE_FLUID_CHANGED", "Fluid Changed", "Kvapalina vymenená", completesReplacementTask = true),
                ServiceSubOption("BRAKE_BLED", "Brakes Bled", "Odvzdušnené", completesServiceTask = true)
            )
            // Brake Pads Front/Rear
            id == "GEN_BRAKE_PADS" || n.contains("pad") || n.contains("platnič") || n.contains("doštič") -> listOf(
                ServiceSubOption("FRONT_PADS_CHECKED", "Front Checked", "Predné skontrolované", completesInspectionTask = true),
                ServiceSubOption("FRONT_PADS_CHANGED", "Front Changed", "Predné vymenené", completesReplacementTask = true),
                ServiceSubOption("REAR_PADS_CHECKED", "Rear Checked", "Zadné skontrolované", completesInspectionTask = true),
                ServiceSubOption("REAR_PADS_CHANGED", "Rear Changed", "Zadné vymenené", completesReplacementTask = true)
            )
            // Generic Brakes task
            c == "brakes" || n.contains("brake") || n.contains("brzd") -> listOf(
                ServiceSubOption("BRAKES_CHECKED", "Checked", "Skontrolované", completesInspectionTask = true),
                ServiceSubOption("BRAKE_PADS_CHANGED", "Pads Changed", "Platničky vymenené", completesReplacementTask = true),
                ServiceSubOption("BRAKE_FLUID_CHANGED", "Fluid Changed / Bled", "Kvapalina vymenená", completesReplacementTask = true)
            )
            // Tires & Axles
            id == "STD_TIRES_AXLES" || id == "GEN_TIRES" || c == "tires" ||
                n.contains("tire") || n.contains("tyre") || n.contains("pneu") || n.contains("axle") -> listOf(
                ServiceSubOption("TIRE_PRESSURE_CHECKED", "Pressure Checked", "Tlak skontrolovaný", completesInspectionTask = true),
                ServiceSubOption("TIRE_TREAD_CHECKED", "Tread & Axles Checked", "Dezén a osi OK", completesInspectionTask = true),
                ServiceSubOption("TIRE_FRONT_CHANGED", "Front Tire Changed", "Predná pneu vymenená", completesReplacementTask = true),
                ServiceSubOption("TIRE_REAR_CHANGED", "Rear Tire Changed", "Zadná pneu vymenená", completesReplacementTask = true)
            )
            // Valve Clearance
            id == "STD_VALVE_CLEARANCE" || n.contains("valve") || n.contains("ventil") -> listOf(
                ServiceSubOption("VALVES_CHECKED", "Clearance Measured", "Vôle zmerané", completesInspectionTask = true),
                ServiceSubOption("VALVES_ADJUSTED", "Shims Adjusted", "Podložky nastavené", completesServiceTask = true, completesReplacementTask = true)
            )
            // Front Forks & Suspension Oil
            id == "GEN_FORKS" || n.contains("fork") || n.contains("vidlic") -> listOf(
                ServiceSubOption("FORKS_CHECKED", "Seals Checked", "Guferá skontrolované", completesInspectionTask = true),
                ServiceSubOption("FORKS_OIL_SEALS_CHANGED", "Oil & Seals Changed", "Olej a guferá vymenené", completesReplacementTask = true)
            )
            // Rear Shock
            id == "GEN_REAR_SHOCK" || n.contains("shock") || n.contains("tlmič") -> listOf(
                ServiceSubOption("SHOCK_CHECKED", "Checked", "Skontrolovaný", completesInspectionTask = true),
                ServiceSubOption("SHOCK_SERVICED", "Adjusted / Rebuilt", "Nastavený / Repasovaný", completesServiceTask = true, completesReplacementTask = true)
            )
            // Clutch & Throttle
            id == "GEN_CLUTCH_THROTTLE" || n.contains("clutch") || n.contains("throttle") ||
                n.contains("spojk") || n.contains("plyn") -> listOf(
                ServiceSubOption("CONTROLS_CHECKED", "Freeplay Checked", "Vôľa skontrolovaná", completesInspectionTask = true),
                ServiceSubOption("CONTROLS_LUBED_ADJ", "Adjusted & Lubed", "Nastavené a namazané", completesServiceTask = true),
                ServiceSubOption("CONTROLS_CHANGED", "Cable / Clutch Changed", "Lanko / spojka vymenená", completesReplacementTask = true)
            )
            // Steering Head Bearings
            id == "GEN_STEERING" || n.contains("steering") || n.contains("krk riadenia") -> listOf(
                ServiceSubOption("STEERING_CHECKED", "Play Checked", "Vôľa skontrolovaná", completesInspectionTask = true),
                ServiceSubOption("STEERING_GREASED", "Adjusted / Greased", "Nastavené / namazané", completesServiceTask = true),
                ServiceSubOption("STEERING_CHANGED", "Bearings Changed", "Ložiská vymenené", completesReplacementTask = true)
            )
            // Battery & Charging
            id == "GEN_BATTERY" || n.contains("battery") || n.contains("batér") -> listOf(
                ServiceSubOption("BATTERY_CHECKED", "Voltage Checked", "Napätie skontrolované", completesInspectionTask = true),
                ServiceSubOption("BATTERY_CHARGED", "Charged", "Dobitá", completesServiceTask = true),
                ServiceSubOption("BATTERY_CHANGED", "Battery Changed", "Batéria vymenená", completesReplacementTask = true)
            )
            // Lights & Indicators
            id == "GEN_LIGHTS" || n.contains("light") || n.contains("svetl") -> listOf(
                ServiceSubOption("LIGHTS_CHECKED", "Checked", "Skontrolované", completesInspectionTask = true),
                ServiceSubOption("LIGHTS_CHANGED", "Bulb / Unit Changed", "Žiarovka vymenená", completesReplacementTask = true)
            )
            // Bolts & Fasteners Torque
            id == "GEN_BOLTS" || n.contains("bolt") || n.contains("fastener") || n.contains("torque") || n.contains("skrut") -> listOf(
                ServiceSubOption("BOLTS_CHECKED", "Torques Checked", "Momenty skontrolované", completesInspectionTask = true),
                ServiceSubOption("BOLTS_RETORQUED", "Re-torqued / Tightened", "Dotiahnuté", completesServiceTask = true)
            )
            // Fallback for custom AI/manual tasks
            else -> listOf(
                ServiceSubOption("GEN_CHECKED", "Checked", "Skontrolované", completesInspectionTask = true),
                ServiceSubOption("GEN_SERVICED", "Serviced / Adjusted", "Vykonané / nastavené", completesServiceTask = true),
                ServiceSubOption("GEN_CHANGED", "Changed / Replaced", "Vymenené", completesReplacementTask = true)
            )
        }
    }

    fun evaluateCompletesTask(
        actionType: String,
        selectedKeys: Set<String>,
        options: List<ServiceSubOption>
    ): Boolean {
        if (selectedKeys.isEmpty()) return false
        val activeOptions = options.filter { it.key in selectedKeys }
        if (activeOptions.any { it.completesReplacementTask }) return true

        val action = actionType.lowercase()
        val isInspectTask = action.contains("inspect") || action.contains("check") ||
            action.contains("measure") || action.contains("skontrol") || action.contains("kontrola")
        val isServiceTask = action.contains("clean") || action.contains("lube") ||
            action.contains("adjust") || action.contains("bleed") ||
            action.contains("vyčist") || action.contains("namaz") || action.contains("nastav")

        return when {
            isInspectTask -> activeOptions.any {
                it.completesInspectionTask || it.completesServiceTask || it.completesReplacementTask
            }
            isServiceTask -> activeOptions.any {
                it.completesServiceTask || it.completesReplacementTask
            }
            else -> false // Replacement tasks require an explicit replacement option (or explicit Done toggle)
        }
    }

    fun defaultCompletionKeysForTask(
        actionType: String,
        options: List<ServiceSubOption>
    ): Set<String> {
        if (options.isEmpty()) return emptySet()
        val action = actionType.lowercase()
        val isInspectTask = action.contains("inspect") || action.contains("check") ||
            action.contains("measure") || action.contains("skontrol") || action.contains("kontrola")
        val isServiceTask = action.contains("clean") || action.contains("lube") ||
            action.contains("adjust") || action.contains("bleed") ||
            action.contains("vyčist") || action.contains("namaz") || action.contains("nastav")

        return when {
            isInspectTask -> options.filter { it.completesInspectionTask }.map { it.key }.ifEmpty {
                listOf(options.first().key)
            }.toSet()
            isServiceTask -> options.filter { it.completesServiceTask }.map { it.key }.ifEmpty {
                listOf(options.last().key)
            }.toSet()
            else -> {
                val keys = mutableSetOf<String>()
                options.firstOrNull { it.completesReplacementTask }?.let { keys.add(it.key) }
                // For combined Engine Oil & Filter, also pre-tick Oil Filter Changed when user clicks main Done checkbox
                options.firstOrNull { it.key == "OIL_FILTER_CHANGED" }?.let { keys.add(it.key) }
                if (keys.isEmpty()) keys.add(options.last().key)
                keys
            }
        }
    }

    fun itemMatchesAndCompletesTask(item: ServiceChecklistItem, task: MaintenanceTaskEntity): Boolean {
        if (!item.completesTask) return false
        if (!item.taskId.isNullOrBlank()) {
            return item.taskId == task.taskId
        }
        // Fallback match for standard checklist items without explicit taskId
        val matchedTemplate = standardGeneralChecks.firstOrNull {
            it.nameEn.equals(item.name, ignoreCase = true) ||
                it.nameSk.equals(item.name, ignoreCase = true)
        }
        if (matchedTemplate != null && doesGeneralTemplateOverlapTask(matchedTemplate, task)) {
            return true
        }
        return item.name.equals(task.taskName, ignoreCase = true)
    }

    fun localizeSubAction(key: String, langCode: String): String {
        val isSk = langCode.equals("sk", ignoreCase = true)
        val match = allKnownSubOptionsByKey[key]
        return if (match != null) {
            if (isSk) match.labelSk else match.labelEn
        } else {
            key.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    fun localizeItemName(
        item: ServiceChecklistItem,
        tasks: List<MaintenanceTaskEntity>,
        langCode: String
    ): String {
        val isSk = langCode.equals("sk", ignoreCase = true)
        if (!item.taskId.isNullOrBlank()) {
            when (item.taskId) {
                "STD_COOLANT" -> return if (isSk) "Chladiaca kvapalina" else "Coolant Replacement"
                "STD_ENGINE_OIL" -> return if (isSk) "Motorový olej a filter" else "Engine Oil & Filter"
                "STD_BRAKE_FLUID" -> return if (isSk) "Brzdová kvapalina" else "Brake Fluid Flush"
                "STD_TIRES_AXLES" -> return if (isSk) "Pneumatiky, tlaky a osi kolies" else "Tires, Pressures & Axles"
                "STD_CHAIN_LUBE" -> return if (isSk) "Čistenie, mazanie a napnutie reťaze" else "Chain Clean, Lube & Slack"
                "STD_VALVE_CLEARANCE" -> return if (isSk) "Kontrola vôle ventilov" else "Valve Clearance Check"
                "STD_SPARK_PLUGS" -> return if (isSk) "Zapaľovacie sviečky" else "Spark Plugs"
                "STD_AIR_FILTER" -> return if (isSk) "Vzduchový filter" else "Air Filter Element"
            }
            tasks.firstOrNull { it.taskId == item.taskId }?.let { return it.taskName }
        }
        standardGeneralChecks.firstOrNull {
            it.key == item.taskId ||
                it.nameEn.equals(item.name, ignoreCase = true) ||
                it.nameSk.equals(item.name, ignoreCase = true)
        }?.let { return it.name(langCode) }
        return item.name
    }

    fun localizeStatus(status: String, completesTask: Boolean, langCode: String): String {
        val isSk = langCode.equals("sk", ignoreCase = true)
        return when (status.uppercase()) {
            "DONE" -> if (completesTask) {
                if (isSk) "HOTOVO" else "DONE"
            } else {
                if (isSk) "VYKONANÉ" else "SERVICED"
            }
            "CHECKED" -> if (isSk) "SKONTROLOVANÉ" else "CHECKED"
            "WARNING" -> if (isSk) "POZOR" else "WARNING"
            "SKIPPED" -> if (isSk) "PRESKOČENÉ" else "SKIPPED"
            else -> status
        }
    }

    private val allKnownSubOptionsByKey: Map<String, ServiceSubOption> by lazy {
        val map = linkedMapOf<String, ServiceSubOption>()
        standardGeneralChecks.forEach { tpl ->
            getSubOptionsFor(tpl.key, tpl.nameEn, tpl.category).forEach { opt ->
                map[opt.key] = opt
            }
        }
        listOf(
            "STD_COOLANT", "STD_ENGINE_OIL", "STD_BRAKE_FLUID", "STD_TIRES_AXLES",
            "STD_CHAIN_LUBE", "STD_VALVE_CLEARANCE", "STD_SPARK_PLUGS", "STD_AIR_FILTER",
            "CUSTOM_VALVES", "CUSTOM_BRAKES", "CUSTOM_OTHER"
        ).forEach { id ->
            val dummyName = when (id) {
                "CUSTOM_VALVES" -> "Valve Clearance"
                "CUSTOM_BRAKES" -> "Brakes"
                else -> id
            }
            getSubOptionsFor(id, dummyName, "").forEach { opt ->
                map[opt.key] = opt
            }
        }
        map
    }
}
