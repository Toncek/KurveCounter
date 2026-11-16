# KurveCounter — Audit of Missing Features, Bugs, Architecture & Test Gaps

**Audit Date**: November 16, 2026  
**Repository**: `/home/toncek/work/projects/KurveCounter` (`com.motogeek.telemetry`)  
**Reference Specification**: [`docs/MASTER_SPEC_AND_PLAN.md`](../docs/MASTER_SPEC_AND_PLAN.md) & [`AGENTS.md`](../AGENTS.md)

---

## 0. Completed in Current Pass (Excluded from Open Backlog)

The following items from the Maintenance & Service Hub workflow were **implemented and verified** in this pass:
- **`+ LOG SERVICE` Multi-Task Checklist (`MaintenanceServiceScreen.kt`, `ServiceChecklistCatalog.kt`)**:
  - Replaced the flat 16-chip dialog with a full-width modal checklist listing every `MaintenanceTaskEntity` on the active motorcycle (ordered `OVERDUE` -> `DUE_SOON` -> `OK`) plus non-overlapping general workshop checks (`ServiceChecklistCatalog.remainingGeneralChecks`).
  - Added primary completion checkbox + domain-specific sub-checkboxes per item (e.g., Engine Oil: `Checked Level` / `Oil Changed` / `Filter Changed`; Coolant: `Water / Coolant Checked` / `Water / Coolant Changed` / `System Bled`; Brake Pads/Fluid, Air Filter, Spark Plugs, Chain, Tires, Valves, Suspension, Battery, Controls, Steering, Lights, Bolts).
  - Added a dedicated right-hand **Notes** column on every task row (`ServiceChecklistItem.note`) and inline `(i)` **Repair Guide & Torques** expandable panels (`MotorcycleTemplateCatalog.getProcedureGuideForTask`).
- **Smart Interval Resolution (`ServiceLogEntity.kt`, `MaintenanceEvaluator.kt`)**:
  - Marking an inspection-only sub-action (e.g. `OIL_CHECKED`) logs the inspection without clearing replacement tasks; checking a completion action (e.g. `OIL_CHANGED` with `completesTask = true`) clears `OVERDUE` status on that task immediately upon saving.
- **Extended Guides & Typo Fix (`MotorcycleTemplateCatalog.kt`)**:
  - Fixed Slovak typo `"Vytite"` -> `"Vytrite"` (L72) and added EN/SK repair guides for Brake Pads, Front Forks, Rear Shock, Clutch & Throttle, Steering Head Bearings, Battery, Lights, and Bolts/Torques.
- **Automated Verification**: Added `ServiceChecklistAndMaintenanceTest.kt` verifying check-vs-change behavior, multi-task overdue clearing, notes persistence, legacy `"[]"` compatibility, and EN/SK helpers (`./gradlew testDebugUnitTest` passing).

---

## 1. Critical Blockers & Data Integrity Bugs (`[P0]`)

### 1.1 Infinite Deadlock in CSV Telemetry Export
- **File**: `domain/maintenance/ImportExportManager.kt#L599-L616` (`exportRideAsCsv`)
- **Finding**: Writes CSV header then calls `repository.getTelemetryTrackPointsForRide(rideId).collect { ... }`. Because that DAO query returns a continuous Room `Flow<List<TelemetryTrackPointEntity>>`, `.collect` never completes, hanging the coroutine and leaving the exported SAF file stream open indefinitely.
- **Required Fix**: Switch to the one-shot suspend query `repository.getTelemetryTrackPointsListForRide(rideId)` (matching `shareRideGpx` and `shareRideGoProCsv`).

### 1.2 SQLite `CASCADE` Data Wipe During `RestoreMode.MERGE` & AI Import
- **Files**: `domain/maintenance/ImportExportManager.kt#L194-L215`, `#L347-L400`; `data/local/MotoGeekDao.kt#L12`
- **Finding**: `MotoGeekDao.insertMotorcycle` uses `@Insert(onConflict = OnConflictStrategy.REPLACE)`. During `RestoreMode.MERGE` (and AI manual updates to an existing bike), calling `insertMotorcycle(bikeEntity)` deletes and re-inserts the parent row on ID collision. If foreign-key cascades trigger, existing local child rows (rides, service logs, fuel, parts, reminders) are wiped before merge deduplication runs. Additionally, Format A ignores `targetMotorcycleId` and `backup.serviceLogs` lacks content deduplication beyond `logId`.
- **Required Fix**: Check `repository.getMotorcycleById(id)` and call `repository.updateMotorcycle(...)` for existing bikes, wrap multi-table imports/restores in Room `@Transaction` (`MotoGeekDao` / `MotoGeekRepository`), and deduplicate `serviceLogs` during merge.

### 1.3 Room `MIGRATION_1_2` Omits `app_logs` Table (Crash on v1 -> v2 Upgrade)
- **File**: `data/local/MotoGeekDatabase.kt#L24-L41`
- **Finding**: `MotoGeekDatabase` (`version = 2`) includes `AppLogEntity::class` (`app_logs` table with foreign key to `motorcycles(id)`), yet `MIGRATION_1_2` only executes 3 `ALTER TABLE` statements (`motorcycles.name`, `service_logs.notes`, `ride_sessions.notes`) and **omits `CREATE TABLE IF NOT EXISTS app_logs`** and its index. Upgrading any v1 database crashes immediately with Room schema verification failure (`Expected: app_logs, Found: null`). Schema export is also disabled (`exportSchema = false`).
- **Required Fix**: Add `CREATE TABLE IF NOT EXISTS app_logs (...)` and `CREATE INDEX IF NOT EXISTS index_app_logs_motorcycleId ON app_logs (motorcycleId)` to `MIGRATION_1_2`, and enable Room schema export with migration unit/instrumented tests.

### 1.4 Dead `"CALIBRATE"` Odometer Button in Bike Profile
- **File**: `ui/BikeInfoScreen.kt#L200-L212`
- **Finding**: Tapping the `"CALIBRATE"` button next to Current Total Odometer executes `viewModel.updateActiveBikeOdometer(it.totalOdometerKm)` with the bike's *already-stored* odometer value instead of opening an input dialog (`SYNC KM` style), making the button a silent no-op.
- **Required Fix**: Open an odometer input dialog (shared with or matching `ActiveBikeDropdownCard`'s `Sync Odometer` dialog) so the user can enter their dashboard reading.

---

## 2. High-Priority Missing Functionality, Stability & Logic Bugs (`[P1]`)

### 2.1 Android 10+ Boot Auto-Start Blocked & Foreground Service Start Exceptions
- **Files**: `AndroidManifest.xml`, `service/BootCompletedReceiver.kt#L17-L34`, `MainActivity.kt#L255-L307`, `ui/CockpitScreen.kt#L177-L181, #L374-L378`
- **Finding**:
  1. `BootCompletedReceiver` checks `Manifest.permission.ACCESS_BACKGROUND_LOCATION` on API 29+, but `ACCESS_BACKGROUND_LOCATION` is **missing from `AndroidManifest.xml`** and never requested in `MainActivity.kt`, causing `startOnBoot` to always fail permission check on Android 10+.
  2. `MainActivity.startRideService()` / `checkAndStartMonitoring()` call `ContextCompat.startForegroundService()` without catching `ForegroundServiceStartNotAllowedException` / `SecurityException`.
  3. `MainActivity.stopRideService()` and `CockpitScreen` `"CALIBRATE 0°"` call `startService(...)` without checking whether the service is running or guarding against background-start restrictions on Android 8+.

### 2.2 `TelemetryHistoryScreen` Double `TopAppBar`, Missing `BackHandler` & Empty State
- **Files**: `ui/TelemetryHistoryScreen.kt#L184`, `MainActivity.kt#L131-L183`
- **Finding**:
  1. When `selectedRide != null`, `TelemetryHistoryScreen` renders a nested `Scaffold` with its own `TopAppBar` underneath `MainActivity`'s active `"KURVE // HISTORY"` `TopAppBar`, creating stacked double headers.
  2. Missing `BackHandler(enabled = selectedRide != null) { selectedRide = null }`: system Back exits History instead of closing Ride Details.
  3. Missing empty-state placeholder when `displayedRides` is empty.

### 2.3 `MaintenanceEvaluator` Break-In & Used-Bike Baseline Bugs
- **File**: `domain/maintenance/MaintenanceEvaluator.kt#L26-L31, #L66-L75, #L102-L109`
- **Finding**:
  1. Break-in progress calculation checks `task.intervalKm != null` (10,000 km) **before** `task.firstBreakInAtKm != null && lastLog == null` (1,000 km), making break-in oil progress show ~95% at 500 km instead of 50%.
  2. When adding a used motorcycle (`baseOdometerKm > 0`, `logs.isEmpty()`), `lastPerformedKm` defaults to `0.0` (and `firstBreakInAtKm` still triggers 1,000 km), immediately marking every mileage task `OVERDUE` by lifetime mileage.
  3. `SUPPORTED_FORMATTERS` omits `yyyy.MM.dd` and `yyyy/MM/dd`.

### 2.4 `MainViewModel` Task Seeding, Quick Service UUID, Fuel `isFullTank` & TCO Denominator
- **File**: `ui/MainViewModel.kt#L143-L218, #L228-L262, #L466-L482, #L557-L583`
- **Finding**:
  1. `addNewMotorcycle(...)` inserts `MotorcycleEntity` with `technicalSpecsJson = "{}"` and **zero `MaintenanceTaskEntity` rows**, leaving manually added bikes without a maintenance schedule.
  2. `logQuickServiceByCategory` generates `UUID.randomUUID().toString()` if `categoryKeyword` fails to match, creating orphaned logs that do not clear overdue tasks.
  3. `fuelEconomyStats` ignores `FuelLogEntity.isFullTank`, distorting segmented L/100km math on partial fill-ups.
  4. `tcoStats` divides `totalCost` by `bike.totalOdometerKm` (including prior owners' `baseOdometerKm`) instead of distance owned/tracked.
  5. `applyTemplate` copies English static `template.maintenanceSchedule` instead of calling `buildStandardMaintenanceTasks(specs, _appLanguage.value.code)`.

### 2.5 `MotorcycleTemplateCatalog` Spec Stripping & Incomplete Models
- **File**: `domain/maintenance/MotorcycleTemplateCatalog.kt#L282-L457`
- **Finding**: `mt09` defines 28 specs in `technicalSpecs` but constructs an incomplete 7-field `TechnicalSpecsDto` on L282 for `maintenanceSchedule` generation; templates `cb650r`, `r1250gs`, `ktm890`, `z900`, and `universal` lack coolant, fork oil, valve clearance, spark plug, brake bleed, and axle/pinch torque fields.

### 2.6 Sensor Fusion & DSP Cutoff Math Gaps
- **Files**: `domain/dsp/BiquadButterworthFilter.kt#L29-L30`, `domain/dsp/MotorcycleSensorFusionEngine.kt#L164-L176`, `service/TelemetryForegroundService.kt#L427-L429`
- **Finding**:
  1. `BiquadButterworthFilter.configure` calculates `val nyquist = sampleRateHz / 2.0; val omega = 2.0 * PI * (cutoffHz / nyquist)`, which **doubles** cutoff frequency (3.5 Hz becomes 7.0 Hz). Should divide by `sampleRateHz`.
  2. `MotorcycleSensorFusionEngine.processGps` leaves `finalYawRateDegSec = 0.0` hardcoded, bypassing `PocketModeCalibrator` straight-line yaw-rate guard during heading lock.
  3. GPS-only altitude fallback in `TelemetryForegroundService` uses the 1.5m barometric hysteresis deadband, which accumulates phantom elevation gain from normal GPS altitude jitter when a barometer is absent.

### 2.7 Missing Delete / Destructive Action Confirmations & Undo
- **Files**: `ui/FuelScreen.kt#L200`, `ui/PartsScreen.kt#L170`, `ui/RemindersScreen.kt#L189`, `ui/LogsScreen.kt#L184-L190`, `ui/OfflineBackupScreen.kt#L200-L219`
- **Finding**: Deleting fuel logs, parts, reminders, or clearing app logs executes immediately on tap without confirmation dialog or Undo; `OfflineBackupScreen`'s `"Undo Last Replace (Restore Safety Snapshot)"` immediately overwrites the database without confirmation.

### 2.8 `CockpitScreen` Stubs, Null-Bike UX & Landscape Restriction
- **File**: `ui/CockpitScreen.kt#L89-L198, #L168, #L365, #L621-L622`
- **Finding**:
  1. `CircuitLayout` L621–622 renders hardcoded `"N/A"` stubs for `Turn-In °/s` and `Corner Score` instead of live telemetry/peak fields.
  2. Landscape mode locks the user into one 3-column layout with no switcher for `APEX-R`, `CIRCUIT`, or `LAB`, and hides Auto-Start / Run-on-Boot toggles.
  3. When `activeBike == null`, `START RIDE` is silently disabled without a prompt directing the rider to select/add a motorcycle.

---

## 3. Medium-Priority UX, Component & Data Polish Gaps (`[P2]`)

1. **`DashboardScreen.kt` Unused Computed Stats (`#L31, #L43-L45`)**: Calculates `avgTrailBrakingScore` across all rides and collects `fuelEconomyStats`, but neither is rendered in the UI; lacks TopAppBar language toggle.
2. **`RemindersScreen.kt` Missing Edit, DatePicker & Linked Task Navigation (`#L145, #L282-L425`)**: Cannot edit existing custom reminders; target date requires manual `"YYYY-MM-DD"` typing with no validation/DatePickerDialog; AutoAlertCard `"Service"` button navigates generically to `"maintenance"` without passing/focusing the overdue `taskId`.
3. **`BikeInfoScreen.kt` UX Polish (`#L439, #L620, #L916-L940`)**: `EditTechnicalSpecsDialog` accepts PSI only even when Bar toggle is selected; empty torque hint references `'+ QUICK ADD TORQUE'` instead of `+ TORQUE`; delete button hidden when `allBikes.size == 1`.
4. **`PartsScreen.kt` & `FuelScreen.kt` Input Validation & Filtering**: `PartsScreen:400-401` saves silent `"Custom Part"` / `"Aftermarket"` defaults on blank fields and lacks category filters or Installed/Removed status; `FuelScreen` allows saving 0/negative liters or malformed dates and gives no feedback when odometer auto-syncs forward.
5. **Chart & Social Card Component Bugs**:
   - `components/RideSocialCardGenerator.kt#L49-L57`: Falls back to raw UUID `motorcycleId` if `displayName` is blank and overwrites the ride date timestamp with `ride.name` when named.
   - `components/RideTrajectoryHeatmapCanvas.kt#L192-L195`: Mutates Compose snapshot state (`cornerPinOffsets.clear()/add()`) inside `Canvas` `DrawScope`.
   - `components/SynchronizedTelemetryChart.kt#L54, #L70`: Conflicting dual `pointerInput(Unit)` blocks (`detectTapGestures` + `detectHorizontalDragGestures`).
6. **`LogsScreen.kt` Hardcoded Euro Currency (`#L105, #L125, #L145`)**: Displays `"€"` regardless of `activeBike.currencySymbol`; TopBar Clear only clears `AppLogEntity` while appearing under `"ALL"`/`"RIDES"`.
7. **`AboutAndDiagnosticsScreen.kt` Misleading Sensor Status & Static Version (`#L248, #L310`)**: Displays `"SIMULATED"` for missing hardware sensors even when Demo Mode is OFF, hardcodes `"1.0.0"` version text instead of reading package/BuildConfig version, and lacks a `0°` calibration shortcut.
8. **`HomeScreen.kt` Dead Variable & Navigation Polish (`#L32, #L167-L284`)**: Unused `val tasks by viewModel.currentServiceLogs.collectAsState()` on L32; active ride hero lacks direct pause/stop button; quick action pills don't pre-open Add dialogs.
9. **`OfflineBackupScreen.kt` & `GoogleDriveBackupManager.kt`**: Local JSON SAF import/export lacks loading progress UI; raw `GoogleJsonResponseException` text surfaces directly in snackbars.
10. **Documentation & Spec Drift**: `docs/MASTER_SPEC_AND_PLAN.md` still references AI Manual Extractor & Templates under `OfflineBackupScreen` (moved to `BikeInfoScreen` in commit `07068e1`); repository root `README.md` is missing; `MainActivity` contains alias routes (`"garage"`, `"settings"`, `"bike_info"` -> `BikeInfoScreen`; `"offline_backup"`, `"cloud_backup"` -> `OfflineBackupScreen`).

---

## 4. Testing, Build & Repository Hygiene Gaps (`[P3]`)

1. **Automated Test Coverage Gaps**:
   - Only `DomainLogicTest.kt`, `GoogleDriveBackupTest.kt`, and new `ServiceChecklistAndMaintenanceTest.kt` exist (all pure unit tests).
   - **Zero unit tests** for `MainViewModel`, `MotoGeekDao`/`MotoGeekRepository`, Room `MIGRATION_1_2`, `ImportExportManager` (`exportRideAsCsv`, `shareRideGpx`, `shareRideGoProCsv`, `shareBikeServiceBookHtml`, MERGE mode), `TelemetryForegroundService`, `BootCompletedReceiver`, `MaintenanceCheckWorker`, and `GoogleAuthManager`.
   - **`app/src/androidTest/` does not exist**: Zero Compose UI, Room migration, or on-device telemetry instrumentation tests.
2. **Gradle & Deprecation Hygiene (`app/build.gradle.kts`)**:
   - Duplicated `google-http-client-gson` dependency (L139, L143) and redundant second outer `dependencies { ... }` block (L155-L157).
   - Inline library versions not centralized in `gradle/libs.versions.toml`.
   - Default host JDK 25 triggers Gradle 8.8 failure unless `JAVA_HOME` points to JDK 17/21 (needs documented toolchain / build wrapper note).
   - Deprecated `Icons.Default.ArrowBack` (`AutoMirrored`) and unused local variables across multiple Compose files.
3. **CI / Artifact Policy Alignment (`.github/workflows/`)**:
   - Verify `.github/workflows/cleanup-artifacts.yml` and `ci.yml` (`lintDebug` check missing; confirm workspace CI policy compliance from `AGENTS.md`).
4. **App Branding Drift (`res/values/strings.xml`, `AndroidManifest.xml`)**:
   - `strings.xml` sets `app_name` to `MotoGeekTelemetry` instead of `KurveCounter` (while splash/launcher/docs brand the app as `KurveCounter`).
