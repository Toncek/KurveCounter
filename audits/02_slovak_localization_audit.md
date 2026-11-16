# KurveCounter — Audit of Missing Slovak (`sk`) Translations & Localization Gaps

**Audit Date**: November 16, 2026  
**Repository**: `/home/toncek/work/projects/KurveCounter`  
**Localization Mechanism**: In-app Compose `LocalAppLang` + `tr(en, sk)` ([`AppLanguage.kt`](../app/src/main/java/com/motogeek/telemetry/ui/theme/AppLanguage.kt)), backed by `TelemetryPreferences.appLanguageCode`.

---

## 0. Current Localization Coverage Summary

| Layer / File | `tr(...)` / `AppLang` Status | Notes |
|---|---|---|
| `MainActivity.kt` | **Mostly Done** | 10 `tr()` calls; missing `"Back"`, `"Menu"`, `" // TFT"`, branding. |
| `MotoGeekDrawerSheet.kt` | **Mostly Done** | 18 `tr()` calls; simplify `"Maintenance Hub (3-Tab)"` jargon. |
| `MaintenanceServiceScreen.kt` | **Completed (100% EN/SK)** | Upgraded in current pass across all 3 tabs, dialogs, checklists, sub-actions, repair guides, and snackbars. |
| `ServiceChecklistCatalog.kt` & `MotorcycleTemplateCatalog.kt` | **Completed (100% EN/SK)** | Procedure guides (incl. extended guides) and sub-action options localized EN/SK. |
| `HomeScreen.kt` | **Partial (~65%)** | Missing KPI labels/units (`AVG FUEL`, `PEAK LEAN`, `COST / KM`), `"100 HZ LIVE"`, contentDescriptions, Slovak pluralization. |
| `BikeInfoScreen.kt` | **Near Zero (~2%)** | 50+ hardcoded English labels, spec pills, search, AI prompt UI, template titles, and 3 dialogs. |
| `OfflineBackupScreen.kt` | **Near Zero (~3%)** | Top bar title translated; 30+ body/cloud strings, error snackbars, and restore dialog hardcoded English. |
| `CockpitScreen.kt` | **0% Translated** | 100% hardcoded English (20+ HUD, mode, button, status, chart labels). |
| `DashboardScreen.kt` | **0% Translated** | 100% hardcoded English (20+ TCO, style badge, KPI labels). |
| `TelemetryHistoryScreen.kt` | **0% Translated** | 100% hardcoded English (25+ ride list, podium, asymmetry, dialogs). |
| `FuelScreen.kt` | **0% Translated** | 100% hardcoded English (27+ labels, stats, snackbars, fill-up dialog). |
| `PartsScreen.kt` | **0% Translated** | 100% hardcoded English (25+ labels, snackbars, add/edit part dialog, defaults). |
| `RemindersScreen.kt` | **0% Translated** | 100% hardcoded English (22+ alerts, badges, target strings, add dialog). |
| `BikeDataExportScreen.kt` | **0% Translated** | 100% hardcoded English (14+ export buttons, session subtitles, errors). |
| `LogsScreen.kt` | **0% Translated** | 100% hardcoded English (15+ category chips, log details, search/clear). |
| `AboutAndDiagnosticsScreen.kt` | **0% Translated** | 100% hardcoded English (25+ sensor names, status badges, app metadata). |
| `ActiveBikeDropdownCard.kt` | **0% Translated** | 100% hardcoded English (11+ dropdown, sync button & dialog). |
| `RideTrajectoryHeatmapCanvas.kt` & `SynchronizedTelemetryChart.kt` | **0% Translated** | 100% hardcoded English canvas overlays, legend chips, scrub HUD. |
| `RideSocialCardGenerator.kt` | **0% Translated** | 6 rendered card metrics + riding style badges hardcoded English. |
| Non-UI Services / Workers / Domain / Res | **0% Translated** | Notifications, channels, Drive errors, HTML/CSV exports, activity logs, torque map keys, Android `strings.xml`. |

---

## 1. Android System & Structural Localization Gaps

1. **Missing `res/values-sk/strings.xml` and `res/xml/locales_config.xml`**:
   - `app/src/main/res/values/strings.xml` only defines `<string name="app_name">MotoGeekTelemetry</string>`.
   - Missing Android 13+ per-app language manifest/XML config (`locales_config.xml`) and Slovak resource strings for system dialogs/labels.
2. **Slovak Pluralization & Grammar**:
   - Current strings use `"$count ÚKON(Y)"` / `"$count záznam(ov)"`. Slovak requires proper 3-form declension (`1 úkon`, `2–4 úkony`, `5+ úkonov`; `1 deň`, `2–4 dni`, `5+ dní`).
3. **TopBar Language Toggle Inconsistency**:
   - `EN ▸ SK` toggle only exists on `MainActivity`'s top bar (`home`, `cockpit`, `history`), `MotoGeekDrawerSheet`, `MaintenanceServiceScreen`, and `OfflineBackupScreen`. Missing from `BikeInfoScreen`, `DashboardScreen`, `FuelScreen`, `PartsScreen`, `RemindersScreen`, `BikeDataExportScreen`, `LogsScreen`, and `AboutAndDiagnosticsScreen`.

---

## 2. UI Screens & Components Pending Slovak Translation

### 2.1 `CockpitScreen.kt` (0% Translated)
- **Buttons & Toggles**: `"Reset Peaks"` (`"Vynulovať maximá"`), `"PAUSE RIDE"`/`"PAUSE"` (`"POZASTAVIŤ JAZDU"`/`"POZASTAVIŤ"`), `"RESUME RIDE"`/`"RESUME"` (`"POKRAČOVAŤ"`), `"START RIDE"` (`"ZAČAŤ JAZDU"`), `"STOP RIDE"`/`"STOP"` (`"UKONČIŤ JAZDU"`/`"ZASTAVIŤ"`), `"STOP DEMO"` (`"ZASTAVIŤ DEMO"`), `"CALIBRATE 0° (SET ZERO)"` (`"KALIBROVAŤ 0° (VYNULOVANIE)"`), `"Try Demo Ride (Simulator)"` (`"Demo jazda (Simulátor)"`), `"Auto-Start > 5 km/h"` (`"Auto-štart > 5 km/h"`), `"Run on Phone Boot"` (`"Spustiť po štarte mobilu"`).
- **Layout & Metrics**: `"Cockpit"` (`"Kokpit"`), `CIRCUIT` (`"OKRUH"`), `LAB` (`"LAB"`), `"AUTO-PAUSED"` (`"AUTOMATICKY POZASTAVENÉ"`), `"Corners"` (`"Zákruty"`), `"Avg Speed"` (`"Priem. rýchlosť"`), `"Timer"` (`"Čas jazdy"`), `"Turn-In °/s"` (`"Nájazd °/s"`), `"Corner Score"` (`"Skóre zákruty"`), `"Lean Angle (°)"` (`"Uhol náklonu (°)"`), `"Longitudinal G"` (`"Pozdĺžne preťaženie (G)"`), `"Lateral G"` (`"Priečne preťaženie (G)"`), raw `calibrationStatus` strings (`"LOCKED"`, `"CALIBRATING"`, `"UNLOCKED"`).

### 2.2 `DashboardScreen.kt` (0% Translated)
- `"Analytics & TCO Dashboard"` (`"Analytika a TCO"`), `"No motorcycle selected."` (`"Nie je vybraný žiadny motocykel."`), `"TOTAL COST OF OWNERSHIP (TCO)"` (`"CELKOVÉ NÁKLADY NA VLASTNÍCTVO (TCO)"`), `"ALL-IN TOTAL"` (`"CELKOVÉ NÁKLADY"`), `"COST PER KM"` (`"NÁKLADY NA KM"`), legend (`"Palivo"`, `"Servis"`, `"Diely"`, `"Kúpna cena"`), `"RIDER TELEMETRY PERFORMANCE"` (`"TELEMETRICKÝ VÝKON JAZDCA"`), riding styles (`"TOURER"`, `"TECHNICIAN"`, `"RACER"`), `"PEAK LEAN L/R"`, `"CORNERS L/R"`, `"PEAK BRAKING"`, `"PEAK LATERAL"`, `"TRACKED DISTANCE"`, `"TOTAL RIDES"` / `"SESSIONS"`, `"View Ride History & Trajectory Heatmaps"`.

### 2.3 `TelemetryHistoryScreen.kt` (0% Translated)
- `"Ride History"` (`"História jázd"`), `"All Bikes"` (`"Všetky motorky"`), `"Distance: "`, `"Style: "`, `"Max Lean L/R: "`, Delete Ride confirmation dialog, Edit Ride Details dialog (`"Ride Name"`, `"Notes"`, `"Save"`, `"Cancel"`), `"Share Ride Card"`, `"Trajectory Heatmap"`, `"Telemetry Analytics"`, `"Top Corners Podium"`, `"No corner data recorded for this ride."`, `"Asymmetry Analyzer"`, `"LEFT"`/`"RIGHT"` (`"VĽAVO"`/`"VPRAVO"`), `"Avg Entry: "`, `"Max Lean: "`, `"Tire Utilization (Estimated from Lean Angle)"`, `"Chicken Strips: ..."`, `"Score: "`.

### 2.4 `BikeInfoScreen.kt` (>50 Untranslated Strings)
- Header/Actions: `"Bike Profile & Torques"`, `"+ NEW BIKE"`, `"SAVE"`, `"Motorcycle profile saved successfully!"`, `"CURRENT TOTAL ODOMETER"`, `"Base / Tracked / Offset"`, `"CALIBRATE"`, `"MOTORCYCLE SPECIFICATIONS"`.
- Form Labels: `"Display Name / Nickname"`, `"Make"`, `"Model"`, `"Year"`, `"Displacement (cc)"`, `"Category"`, `"Starting Odometer (km)"`, `"Purchase Price"`, `"Currency"`.
- Torque & Specs Section: `"FACTORY SPECS & BOLT TORQUES"`, `"UNIT: BAR"` / `"UNIT: PSI"`, `"EDIT SPECS"`, `"+ TORQUE"`, 8 `SpecPill` labels (`Oil Cap`, `Front/Rear Tire`, `Chain Slack`, `Oil Grade`, `Coolant`, `Brake Fluid`, `Spark Plug`), `"Search bolt or nut torque (Nm)..."`, `"No bolt torques found..."`.
- AI Extractor & Templates: `"AI SERVICE MANUAL EXTRACTOR"`, description paragraph, `"Copy AI Master Mechanic Prompt"`, `"Paste AI JSON / Clipboard Import"`, `"BUILT-IN MOTORCYCLE TEMPLATES"`, 6 template titles & subtitles, `"Delete Motorcycle (%s)"`.
- Dialogs: `ApplyTemplateDialog`, `PasteJsonDialog`, `DeleteConfirmDialog`, `EditTechnicalSpecsDialog` (10 labels), `QuickAddTorqueDialog`, `AddNewBikeDialog` (8 labels).

### 2.5 `FuelScreen.kt` (0% Translated)
- `"Fuel & Consumption"` (`"Tankovanie a spotreba"`), `"+ ADD FILL-UP"` (`"+ PRIDAŤ TANKOVANIE"`), `"No active motorcycle selected."`, `"FUEL ECONOMY OVERVIEW"`, `"AVG CONSUMPTION"`, `"EFFICIENCY"`, `"TOTAL SPENT"`, `"Total Liters"`, `"Total Tracked"`, `"Fill-ups"`, empty state, `"FULL TANK"` (`"PLNÁ NÁDRŽ"`), snackbars (`Fuel log deleted/updated`, `Fill-up logged`), `AddEditFuelDialog` (`Date`, `Odo`, `Liters`, `Price/L`, `Total Cost`, `Fuel Station / Brand / Notes`, `Full Tank Fill-up`, `Save Fill-up`, `Cancel`).

### 2.6 `PartsScreen.kt` (0% Translated)
- `"Parts & Modifications"` (`"Diely a úpravy"`), `"+ ADD PART"` (`"+ PRIDAŤ DIEL"`), `"PARTS & MODIFICATIONS SUMMARY"`, `"INSTALLED PARTS"`, `"ITEMS"`, `"TOTAL INVESTED"`, `"Installed on ... at ... km"`, snackbars, `AddEditPartDialog` (`Part Name`, `Brand / OEM`, `Part #`, `Cost`, `Odo`, `Date`, `Notes / Tightening Torque`), hardcoded fallback strings `"Custom Part"` / `"Aftermarket"`.

### 2.7 `RemindersScreen.kt` (0% Translated)
- `"Reminders & Alerts"` (`"Pripomienky a upozornenia"`), `"+ ADD REMINDER"`, `"AUTOMATIC MAINTENANCE ALERTS"`, empty alert message, `"CUSTOM REMINDERS"`, empty reminders message, snackbars, badges `"OVERDUE"` / `"DUE SOON"`, `"km remaining"`, `"days"`, `"Due at"`, `"Service"` button (`"Servisovať"`), `"Target:"`, `"Date:"`, `AddReminderDialog` labels & buttons.

### 2.8 `OfflineBackupScreen.kt` (Only Title Translated; 30+ Hardcoded Strings)
- Import/Export feedback snackbars (`Import Successful!...`, `Import Error`, `Garage backup exported...`, `Export Error`, snapshot restored/failed).
- Section `"GARAGE DATA STORAGE & RESTORE"`, description paragraph, `"Import Mode: Merge (Safe)"`, `"Replace Garage"`, `"Export Garage to JSON..."`, `"Import JSON Backup File..."`, `"Undo Last Replace (Restore Safety Snapshot)"`.
- Cloud section `"Google Drive Cloud Backup"`, guidance text, Sign-in / Allow Drive buttons, `"Signed in as:"`, `"Last cloud backup: Never"`, `"Backup to Drive"`, `"Restore from Drive"`, `"Sign out"`, Restore confirmation dialog, Google auth/Drive snackbars.

### 2.9 `BikeDataExportScreen.kt` (0% Translated)
- `"Digital Service Book & Export"` (`"Digitálna servisná knižka a export"`), `"OFFICIAL GARAGE EXPORTS"`, `"Share Digital Service Book (HTML Report)"`, `"Export Fuel, Parts & Service Logs (CSV)"`, `"RECORDED RIDE SESSIONS"`, empty state, `"Ride #"`, `avg` subtitle, `"Export GPX"`, `"GoPro Overlay CSV"`, export error snackbars.

### 2.10 `LogsScreen.kt` (0% Translated)
- `"Activity Timeline & Logs"` (`"Časová os aktivít a záznamy"`), filter chips (`"ALL"`, `"RIDES"`, `"SERVICE"`, `"FUEL"`, `"PARTS"`, `"SYSTEM"`), `"Search activity feed..."`, `"No activities found."`, `"Activity system logs cleared"`, hardcoded summaries for Ride/Service/Fuel/Part cards.

### 2.11 `AboutAndDiagnosticsScreen.kt` (0% Translated)
- `"Sensor Diagnostics & About"`, `"LIVE SENSOR FUSION ENGINE"`, `LEAN ANGLE`, `SPEED`, `LONGITUDINAL G`, `LATERAL G`, `"Reset Peak Holds"`, `"Simulate 100Hz"` / `"Stop Sim"`, `"ON-DEVICE HARDWARE SENSORS"`, sensor titles/descriptions (Accelerometer, Gyroscope, Barometer, GNSS/GPS), `"PRESENT"` / `"AVAILABLE"` / `"SIMULATED"`, `"ABOUT KURVECOUNTER TELEMETRY"`, table keys (`Application`, `Package`, `Version`, `Target SDK`, `Minimum SDK`, `Sensors Sample Rate`, `Database`).

### 2.12 `HomeScreen.kt`, `MainActivity.kt` & Reusable UI Components
- **`HomeScreen.kt`**: `"AVG FUEL"`, `"PEAK LEAN"`, `"COST / KM"`, `"L/100"`, `"L/R"`, `"100 HZ LIVE"`, `"View Maintenance"`, `"Launch Cockpit"`, Slovak pluralization on `overdueCount` / `dueSoonCount`.
- **`MainActivity.kt`**: `" // TFT"`, `"Back"`, `"Menu"`.
- **`MotoGeekDrawerSheet.kt`**: Replace technical label `tr("Maintenance Hub (3-Tab)", "Servis a údržba (3 karty)")` with `tr("Maintenance & Service", "Údržba a servis")`.
- **`ActiveBikeDropdownCard.kt`**: `"NO MOTORCYCLE"`, `"Select or Add Motorcycle"`, `"SYNC KM"`, `"+ Add New Motorcycle"`, Sync Odometer dialog strings.
- **`RideTrajectoryHeatmapCanvas.kt` & `SynchronizedTelemetryChart.kt`**: `"No telemetry data available."`, `"Lean"`, `"G-Force"`, `"Speed"`, `"Turn X (direction)"`, `"Entry"`, `"Apex Lean"`, `"Peak G"`, `"Roll Rate"`, `"Score"`, `"No telemetry data to chart."`, `"Speed/Lean/Lat G/Long G/Alt"`.
- **`RideSocialCardGenerator.kt`**: Pass active `AppLang` to localize `"DISTANCE"`, `"MAX LEAN (L|R)"`, `"CORNERS"`, `"MAX G (LAT|BRK)"`, `"STYLE"`, `"AVG SCORE"`, riding style badges, and Android Share chooser title.

---

## 3. Services, Workers, Domain & Persisted Data Localization Gaps

1. **`TelemetryForegroundService.kt`**:
   - Notification channel `"Telemetry Service"` (#L349), notification title `"MotoGeek Telemetry Active"` (#L338), body `"Standby: Auto-start > 5 km/h"` / `"Recording live telemetry..."` (#L339).
   - State strings: `"MANUAL 0° LOCKED"`, `"CALIBRATING POCKET MODE"`, `"LOCKED"`, fallback bike `"Universal Motorcycle"`, `"Ride Recorded..."` app log text (#L689–L690).
2. **`MaintenanceCheckWorker.kt`**:
   - Notification channel `"Maintenance Alerts"` (#L83), notification title `"Maintenance Alert: $bikeName"` (#L100), body text `"$overdueCount task(s) OVERDUE."` / `"due soon"` / `"reminder(s) due"` (#L93–L95).
3. **`ImportExportManager.kt`**:
   - `shareBikeServiceBookHtml` (#L618–L730): Entire HTML service book report (~20 labels/headings/table headers/footer) and `"Share Service Book"` intent chooser are English-only.
   - `shareAllLogsCsv` (#L749–L778): Hardcoded `"Liters"`, `"FullTank"`, `"Share Logs CSV"`; `shareRideGpx` & `shareRideGoProCsv` intent choosers and subjects are English-only.
   - Exception strings (`"Bike not found"`, `"Cannot read file"`, `"No safety snapshot available"`); `aiServiceManualPrompt` defaults to `"en"` when accessed as property (#L79).
4. **`GoogleDriveBackupManager.kt`**:
   - 10+ thrown error/prompt messages (`"Not signed in..."`, `"Network error..."`, `"No backup found..."`, `"Select where to save..."`, etc.) propagate directly to UI snackbars in English.
5. **`MaintenanceDtos.kt` & `MotorcycleTemplateCatalog.kt`**:
   - `TechnicalSpecsDto.toConsolidatedTorqueMap()` (#L112) hardcodes all 11 bolt torque keys in English (note: preserve default zero-arg EN behavior for `DomainLogicTest`, add localized `toConsolidatedTorqueMap(langCode)` / UI display mapper).
   - Static templates (`mt09`–`universal`) instantiate English schedules via zero-arg `buildStandardMaintenanceTasks(...)`, and use untranslated spec values (`"Shaft Drive"`, `"2-5 (Swingarm)"`, `"Street Motorcycle"`, `"OEM Iridium/Standard"`, `"FULL"`).
6. **`MainViewModel.kt`**:
   - All 9 `repository.logActivity(...)` calls write hardcoded English titles/details and categories (`"Motorcycle Selected"`, `"Fuel Added"`, `"Part Installed"`, `"Quick Service"`, etc.), plus `"Self"` and English bike fallbacks.
7. **`ServiceLogEntity.kt`**:
   - `DefaultServiceChecklistNames` and entity defaults (`"Periodic Maintenance"`, `"Home Garage"`, `"Self"`) are English literals (`ServiceChecklistCatalog.localizeItemName` now translates checklist item names dynamically in `MaintenanceServiceScreen`, while `ServiceLogEntity` defaults and `Dashboard`/`Home` exports should also localize cleanly).
