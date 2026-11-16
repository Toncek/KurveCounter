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
                        "3. Vytrite vnútro airboxu a vypustite odkalovaciu hadičku odvetrania kľukovky."
                } else {
                    "Specs: Inspect/replace air filter element and clean airbox housing.\n" +
                        "Procedure: 1. Lift or remove fuel tank and unbolt airbox cover. " +
                        "2. Inspect filter pleats for debris or oil saturation; replace paper element or clean/re-oil reusable element. " +
                        "3. Wipe inside of airbox clean and drain crankcase breather catch tube."
                }
            }
            taskName.contains("Pad", ignoreCase = true) || taskName.contains("platnič", ignoreCase = true) || taskName.contains("doštič", ignoreCase = true) -> {
                val frontCal = specs?.frontCaliperBoltsTorqueNm ?: 35.0
                val rearCal = specs?.rearCaliperBoltsTorqueNm ?: 27.0
                if (isSk) {
                    "Špecifikácia: Predné strmene: $frontCal Nm • Zadný strmeň: $rearCal Nm • Min. hrúbka obloženia: 1.0–1.5 mm.\n" +
                        "Postup: 1. Demontujte skrutky strmeňa ($frontCal Nm vpredu / $rearCal Nm vzadu) a vyberte staré brzdové platničky. " +
                        "2. Vyčistite brzdové piestiky čističom bŕzd a zatlačte ich späť do strmeňa. " +
                        "3. Založte nové doštičky/platničky, dotiahnite skrutky strmeňa na moment a niekoľkokrát stlačte páčku/pedál pred jazdou."
                } else {
                    "Specs: Front Caliper Bolts: $frontCal Nm • Rear Caliper Bolts: $rearCal Nm • Min. Pad Thickness: 1.0–1.5 mm.\n" +
                        "Procedure: 1. Remove caliper mounting bolts ($frontCal Nm front / $rearCal Nm rear) and extract old brake pads. " +
                        "2. Clean exposed caliper pistons with brake cleaner and carefully push them back into caliper bore. " +
                        "3. Install new brake pads, torque caliper bolts to spec, and pump brake lever/pedal several times to restore pressure before riding."
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
            taskName.contains("Fork", ignoreCase = true) || taskName.contains("Vidlic", ignoreCase = true) -> {
                val forkOilMl = specs?.forkOilCapacityPerLegMl ?: 450.0
                val forkOilWt = specs?.forkOilWeight?.takeIf { it.isNotBlank() } ?: "10W"
                val pinch = specs?.frontPinchBoltsTorqueNm ?: 20.0
                val axle = specs?.frontAxleTorqueNm ?: 65.0
                if (isSk) {
                    "Špecifikácia: Objem vidlicového oleja: $forkOilMl ml/klzák • Viskozita: $forkOilWt • Zaisťovacie skrutky (pinch bolts): $pinch Nm • Predná os: $axle Nm.\n" +
                        "Postup: 1. Uvoľnite horné zátky vidlice a skrutky okuliarov pred demontážou tlmičov. " +
                        "2. Vypustite starý vidlicový olej, skontrolujte klzáky a vymeňte guferá a prachovky. " +
                        "3. Nalejte $forkOilMl ml oleja $forkOilWt, odvzdušnite cartridgovú pumpu a dotiahnite prednú os na $axle Nm (zaisťovacie skrutky $pinch Nm)."
                } else {
                    "Specs: Fork Oil Capacity: $forkOilMl ml/leg • Weight: $forkOilWt • Pinch Bolts: $pinch Nm • Front Axle: $axle Nm.\n" +
                        "Procedure: 1. Loosen top fork cap and triple clamp pinch bolts before removing fork legs. " +
                        "2. Drain old suspension oil, inspect stanchions for pitting/chips, and replace oil and dust seals. " +
                        "3. Refill with $forkOilMl ml of $forkOilWt fork oil, pump cartridge to bleed air, and torque axle ($axle Nm) and pinch bolts ($pinch Nm) to spec."
                }
            }
            taskName.contains("Shock", ignoreCase = true) || taskName.contains("Tlmič", ignoreCase = true) -> {
                val rearAxle = specs?.rearAxleNutTorqueNm ?: 100.0
                val shockMountTorque = specs?.torquesNm?.get("Rear Shock Mount") ?: specs?.boltTorquesNm?.get("Rear Shock Mount") ?: 44.0
                if (isSk) {
                    "Špecifikácia: Uchytenie tlmiča: $shockMountTorque Nm • Zadná os: $rearAxle Nm • Odporúčaný priehyb (sag): 30–40 mm.\n" +
                        "Postup: 1. Odľahčite zadné koleso podopretím rámu motocykla. " +
                        "2. Skontrolujte tesnosť piestnice tlmiča a vôľu ložísk prepákovania kyvnej vidlice. " +
                        "3. Skontrolujte a dotiahnite skrutky uchytenia tlmiča na $shockMountTorque Nm a maticu zadnej osi na $rearAxle Nm."
                } else {
                    "Specs: Shock Mount Bolts: $shockMountTorque Nm • Rear Axle Nut: $rearAxle Nm • Recommended Rider Sag: 30–40 mm.\n" +
                        "Procedure: 1. Support bike securely under frame/engine so rear swingarm is fully unloaded. " +
                        "2. Inspect shock shaft for fluid leaks and check swingarm linkage bushings for free play. " +
                        "3. Check and torque shock mount hardware ($shockMountTorque Nm) and rear axle nut ($rearAxle Nm)."
                }
            }
            taskName.contains("Clutch", ignoreCase = true) || taskName.contains("Throttle", ignoreCase = true) ||
                taskName.contains("Spojk", ignoreCase = true) || taskName.contains("Plyn", ignoreCase = true) -> {
                val idle = specs?.idleSpeedRpm?.takeIf { it.isNotBlank() } ?: "1,200–1,400 RPM"
                if (isSk) {
                    "Špecifikácia: Voľnobežné otáčky: $idle • Vôľa páčky spojky: 2–3 mm • Vôľa rukoväte plynu: 2–4 mm.\n" +
                        "Postup: 1. Skontrolujte voľný chod spojkovej páčky (2–3 mm) a rukoväte plynu (2–4 mm). " +
                        "2. Premažte lanká špeciálnym mazivom a preverte plynulý návrat plynu pri plnom vytočení riadidiel v oboch smeroch. " +
                        "3. Pri naštartovanom motore overte stabilný voľnobeh ($idle) bez samovoľného pridávania pri otáčaní riadidlami."
                } else {
                    "Specs: Idle Speed: $idle • Clutch Lever Freeplay: 2–3 mm • Throttle Twistgrip Freeplay: 2–4 mm.\n" +
                        "Procedure: 1. Inspect and measure freeplay on clutch lever (2–3 mm) and throttle twistgrip (2–4 mm). " +
                        "2. Lubricate control cables and verify snappy throttle return without binding from steering lock to lock. " +
                        "3. With engine warm, confirm steady idle ($idle) does not fluctuate when turning handlebars."
                }
            }
            taskName.contains("Steering", ignoreCase = true) || taskName.contains("Krk", ignoreCase = true) -> {
                val stemNut = specs?.torquesNm?.get("Steering Stem Nut") ?: specs?.boltTorquesNm?.get("Steering Stem Nut") ?: 110.0
                val pinch = specs?.frontPinchBoltsTorqueNm ?: 20.0
                if (isSk) {
                    "Špecifikácia: Matica krku riadenia: $stemNut Nm • Zaisťovacie skrutky vidlice: $pinch Nm.\n" +
                        "Postup: 1. Zdvihnite predné koleso do vzduchu a overte hladký pohyb riadidiel bez zadrhávania alebo aretácie v strede. " +
                        "2. Uchopte spodnú časť vidlice a potlačením dopredu a dozadu skontrolujte vôľu v ložiskách riadenia. " +
                        "3. Pri uvoľnení nastavte predpätie ložísk krku, dotiahnite centrálnu maticu na $stemNut Nm a skrutky okuliarov na $pinch Nm."
                } else {
                    "Specs: Steering Stem Nut: $stemNut Nm • Triple Clamp Pinch Bolts: $pinch Nm.\n" +
                        "Procedure: 1. Lift front wheel off ground and sweep steering lock to lock to check for notches, roughness, or center detent. " +
                        "2. Rock bottom of fork legs back and forth to check for radial play or clunking in the head tube. " +
                        "3. Adjust bearing preload if loose, retorque steering stem nut ($stemNut Nm), and torque pinch bolts ($pinch Nm)."
                }
            }
            taskName.contains("Battery", ignoreCase = true) || taskName.contains("Batér", ignoreCase = true) -> {
                val idle = specs?.idleSpeedRpm?.takeIf { it.isNotBlank() } ?: "1,200–1,400 RPM"
                if (isSk) {
                    "Špecifikácia: Pokojové napätie batérie: >12.6 V • Dobíjacie napätie (pri 5 000 RPM): 13.8–14.8 V • Voľnobeh: $idle.\n" +
                        "Postup: 1. Zmerajte pokojové napätie multimetrom (ak je pod 12.4 V, nabite inteligentnou nabíjačkou). " +
                        "2. Očistite póly batérie od oxidácie, pevne dotiahnite káblové oká a ošetrite póly vazelínou. " +
                        "3. Naštartujte motor a overte, že dobíjacie napätie dosiahne 13.8–14.8 V pri zvýšených otáčkach."
                } else {
                    "Specs: Battery Resting Voltage: >12.6 V • Charging Voltage (@ 5,000 RPM): 13.8–14.8 V • Idle: $idle.\n" +
                        "Procedure: 1. Measure resting battery voltage across terminals (>12.6 V = good; <12.4 V indicates recharge needed). " +
                        "2. Clean battery terminals of any corrosion, torque terminal bolts securely, and apply dielectric grease. " +
                        "3. Start engine and verify charging system output voltage rises to 13.8–14.8 V at ~5,000 RPM."
                }
            }
            taskName.contains("Light", ignoreCase = true) || taskName.contains("Svetl", ignoreCase = true) -> {
                val modelName = specs?.motorcycleModel?.takeIf { it.isNotBlank() } ?: "12V System"
                if (isSk) {
                    "Špecifikácia: Elektrický systém: $modelName • Napätie: 12 V • Frekvencia smeroviek: 60–120 bliknutí/min.\n" +
                        "Postup: 1. Skontrolujte funkciu stretávacích, diaľkových a obrysových svetiel. " +
                        "2. Overte rozsvietenie brzdového svetla pri stlačení prednej páčky aj zadného nožného pedála. " +
                        "3. Vyskúšajte smerovky na oboch stranách, výstražné svetlá, klaksón a kontrolky na prístrojovej doske."
                } else {
                    "Specs: Electrical System: $modelName • Operating Voltage: 12V DC • Flash Rate: 60–120 pulses/min.\n" +
                        "Procedure: 1. Check headlights (low and high beams), parking lights, and license plate illumination. " +
                        "2. Verify brake light actuation independently from both front brake lever and rear brake foot pedal. " +
                        "3. Test turn signals left/right, 4-way hazard flashers, horn, and all dashboard warning indicators."
                }
            }
            taskName.contains("Bolt", ignoreCase = true) || taskName.contains("Fastener", ignoreCase = true) ||
                taskName.contains("Torque", ignoreCase = true) || taskName.contains("Skrut", ignoreCase = true) -> {
                val rearAxle = specs?.rearAxleNutTorqueNm ?: 100.0
                val frontAxle = specs?.frontAxleTorqueNm ?: 65.0
                val frontCal = specs?.frontCaliperBoltsTorqueNm ?: 35.0
                val rearCal = specs?.rearCaliperBoltsTorqueNm ?: 27.0
                val pinch = specs?.frontPinchBoltsTorqueNm ?: 20.0
                if (isSk) {
                    "Špecifikácia: Predná os: $frontAxle Nm • Zaisťovacie skrutky: $pinch Nm • Zadná os: $rearAxle Nm • Predné strmene: $frontCal Nm • Zadný strmeň: $rearCal Nm.\n" +
                        "Postup: 1. Skontrolujte vizuálne a mechanicky všetky kľúčové skrutkové spoje na ráme, podvozku a riadidlách. " +
                        "2. Momentovým kľúčom overte predpísané momenty: osi kolies ($frontAxle / $rearAxle Nm), strmene ($frontCal / $rearCal Nm) a pinch bolts ($pinch Nm). " +
                        "3. Vymeňte poškodené spojovacie prvky a na kritické spoje aplikujte odporúčaný zaisťovač závitov (Loctite)."
                } else {
                    "Specs: Front Axle: $frontAxle Nm • Pinch Bolts: $pinch Nm • Rear Axle Nut: $rearAxle Nm • Front Calipers: $frontCal Nm • Rear Caliper: $rearCal Nm.\n" +
                        "Procedure: 1. Visually and mechanically check critical chassis, suspension, engine mount, and handlebar fasteners. " +
                        "2. Use a calibrated torque wrench to verify specs: axles ($frontAxle Nm front / $rearAxle Nm rear), calipers ($frontCal Nm front / $rearCal Nm rear), and pinch bolts ($pinch Nm). " +
                        "3. Replace any damaged or corroded hardware and apply threadlocker where specified by service manual."
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
