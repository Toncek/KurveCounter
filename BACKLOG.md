# KurveCounter (MotoGeekTelemetry) — Prioritized Engineering & Localization Backlog

**Last Updated**: November 23, 2026  
**Audit Sources**:
- [01 — Missing Features, Bugs, Architecture & Test Gaps](audits/01_missing_features_and_bugs_audit.md)
- [02 — Slovak (`sk`) Translation & Localization Gaps](audits/02_slovak_localization_audit.md)

---

## Recently Completed

- [x] **TASK-MAINT-HUB-01 — Interactive `+ LOG SERVICE` Checklist, Sub-Actions, Task Notes Column, `(i)` Repair Guide & Overdue Auto-Clear**
  - **Implemented**: Overhauled `MaintenanceServiceScreen.kt`, added `ServiceChecklistCatalog.kt`, extended `ServiceChecklistItem` in `ServiceLogEntity.kt`, updated `MaintenanceEvaluator.kt` to match checklist task completions, fixed `"Vytite"` typo and added extended EN/SK Repair Guides in `MotorcycleTemplateCatalog.kt`, and localized `MaintenanceServiceScreen.kt` 100% EN/SK.
  - **Verified**: `./gradlew testDebugUnitTest` including `ServiceChecklistAndMaintenanceTest.kt`.
- [x] **TASK-GUIDED-IMPORT-01 — Non-Destructive Guided JSON Import (`mybikes-export-*.json`, KurveCounter Backups & AI Schedules)**
  - **Implemented**: Added `GuidedImportPlanner.kt` and `GuidedImportDialog.kt`; implemented `analyzeJsonString`, `analyzeJsonFile`, and non-destructive `executeGuidedImport` in `ImportExportManager.kt`; fixed `RestoreMode.MERGE` and Formats B/C `insertMotorcycle` (`REPLACE`) cascade deletion risk (now uses `updateMotorcycle`) and added `serviceLogs` / global `logId` collision deduplication; wired SAF file picker + Guided Import modal in `OfflineBackupScreen.kt` (localized EN/SK + Replace/Snapshot confirmation dialogs) and `BikeInfoScreen.kt`.
  - **Verified**: `./gradlew testDebugUnitTest` including `GuidedImportTest.kt` against real `mybikes-export-2026-11-16T12-15-04.json`.
- [x] **TASK-RIDE-HISTORY-GPX-01 — GPX 1.1 Export/Import, CSV Export Fix, Trajectory Heatmap Zoom/Sync, Telemetry History UX & Rider Analytics**
  - **Implemented**: Implemented GPX 1.1 standard export and import (`exportRideAsGpx`, `importRideFromGpxFile`) with `<trkpt>` extensions (lean, speed, g-force, altitude) and `<wpt>` corner apex pins (`sym=Flag, Blue/Red`); fixed `exportRideAsCsv` infinite Flow hang using direct list queries; enhanced `TelemetryForegroundService` with periodic uncommitted buffer flushes, lifecycle stop flush, and GPS accuracy/bearing filtering; upgraded `RideTrajectoryHeatmapCanvas` with pan/zoom gestures, corner pin overlays, lean-colored path segments, and bidirectional telemetry slider sync; streamlined `TelemetryHistoryScreen` with a single top bar, `BackHandler`, and localized empty state; integrated `RiderAnalyticsCalculator` and `DashboardScreen` analytical cards for lean angle distributions, cornering stats, and ride metrics.
  - **Verified**: `./gradlew testDebugUnitTest` including `RideHistoryGpxAndAnalyticsTest.kt`.
- [x] **TASK-SERVICE-DEDUP-01 — Maintenance Task Deduplication, Canonical Normalization & Service Log Remapping**
  - **Implemented**: Added `ServiceTaskDeduplicator.kt` with fuzzy & accent-insensitive title matching against `ServiceChecklistCatalog.kt` across EN and SK locales; added `MotoGeekRepository.deduplicateMaintenanceTasksForBike` to merge duplicate maintenance task entities into primary canonical tasks and remap all associated `serviceLogs`; integrated automated deduplication into Guided JSON Import, `MainViewModel`, and `MaintenanceServiceScreen` (`ServiceHub`).
  - **Verified**: `./gradlew testDebugUnitTest` including `MaintenanceTaskDeduplicationTest.kt`.

---

## `[P0 - Release Blockers & Data Integrity]`

### `BACKLOG-001` Fix `exportRideAsCsv` Infinite Flow Hang *(Completed in `TASK-RIDE-HISTORY-GPX-01`)*
- **Audit Ref**: `01 §1.1`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/domain/maintenance/ImportExportManager.kt`
- **Acceptance Criteria**:
  - [x] Replace `.collect { ... }` on `repository.getTelemetryTrackPointsForRide(rideId)` with `repository.getTelemetryTrackPointsListForRide(rideId)` in `exportRideAsCsv` (#L605).
  - [x] Unit test verifies `exportRideAsCsv` terminates cleanly, closes output stream, and writes all expected trackpoint CSV rows.

### `BACKLOG-002` Complete Room `@Transaction` Wrappers & Format A Single-Bike Target Override *(Partially Completed in `TASK-GUIDED-IMPORT-01` & Release Pass)*
- **Audit Ref**: `01 §1.2`
- **Files to Modify**:
  - `app/src/main/java/com/motogeek/telemetry/data/local/MotoGeekDao.kt`
  - `app/src/main/java/com/motogeek/telemetry/data/local/MotoGeekRepository.kt`
  - `app/src/main/java/com/motogeek/telemetry/domain/maintenance/ImportExportManager.kt`
- **Acceptance Criteria**:
  - [x] In `MERGE` restore and AI manual import onto an existing motorcycle, use `updateMotorcycle` instead of `OnConflictStrategy.REPLACE` `insertMotorcycle`.
  - [x] Add service-log duplicate detection during `MERGE` (verified in `GuidedImportTest`).
  - [x] Honor `targetMotorcycleId` when calling `importFromJsonString` non-guided on Format A single-bike payloads.
  - [ ] Wrap full replace/merge database mutations in Room `@Transaction`.

### `BACKLOG-003` Fix `MIGRATION_1_2` Missing `app_logs` Table (v1 -> v2 Upgrade Crash) *(Partially Completed)*
- **Audit Ref**: `01 §1.3`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/data/local/MotoGeekDatabase.kt`
- **Acceptance Criteria**:
  - [x] Add `CREATE TABLE IF NOT EXISTS app_logs (...)` and `CREATE INDEX IF NOT EXISTS index_app_logs_motorcycleId` inside `MIGRATION_1_2`.
  - [ ] Enable `exportSchema = true` (configuring Room schema output directory) and verify v1->v2 migration integrity with a test.

### `BACKLOG-004` Fix Dead `"CALIBRATE"` Odometer Button in `BikeInfoScreen` *(Completed)*
- **Audit Ref**: `01 §1.4`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/BikeInfoScreen.kt`
- **Acceptance Criteria**:
  - [x] Clicking `"CALIBRATE"` opens an odometer calibration dialog prompting the rider for a new dashboard reading instead of calling `viewModel.updateActiveBikeOdometer(it.totalOdometerKm)`.
  - [x] Entered reading calls `viewModel.updateActiveBikeOdometer(parsedKm)` and updates the displayed total odometer and offset immediately.

---

## `[P1 - High-Priority Functionality, Stability & Logic]`

### `BACKLOG-005` Fix Android 10–15 Boot Auto-Start & Foreground Service Crash Guards *(Completed)*
- **Audit Ref**: `01 §2.1`
- **Files to Modify**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/motogeek/telemetry/service/BootCompletedReceiver.kt`
  - `app/src/main/java/com/motogeek/telemetry/MainActivity.kt`
  - `app/src/main/java/com/motogeek/telemetry/ui/CockpitScreen.kt`
- **Acceptance Criteria**:
  - [x] Declare `ACCESS_BACKGROUND_LOCATION` in `AndroidManifest.xml` (or update boot flow to respect OS FGS background-launch exemptions cleanly with user rationale prompt in Settings/Cockpit).
  - [x] Wrap all `startForegroundService` / `startService` calls in `MainActivity` and `CockpitScreen` (`startRideService`, `stopRideService`, `ACTION_CALIBRATE_ZERO`) with `ForegroundServiceStartNotAllowedException` / `IllegalStateException` guards.

### `BACKLOG-006` Fix `TelemetryHistoryScreen` Double TopAppBar, System Back & Empty State *(Completed in `TASK-RIDE-HISTORY-GPX-01`)*
- **Audit Ref**: `01 §2.2`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/TelemetryHistoryScreen.kt`, `app/src/main/java/com/motogeek/telemetry/MainActivity.kt`
- **Acceptance Criteria**:
  - [x] Remove double `TopAppBar` stacking when `selectedRide != null` (either suppress `MainActivity` bar for ride details or remove nested bar cleanly).
  - [x] Add `BackHandler(enabled = selectedRide != null) { selectedRide = null }`.
  - [x] Display localized empty-state card when no rides are recorded.

### `BACKLOG-007` Fix `MaintenanceEvaluator` Break-In Progress, Used-Bike Baseline & Date Formats *(Partially Completed)*
- **Audit Ref**: `01 §2.3`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/domain/maintenance/MaintenanceEvaluator.kt`, `app/src/test/java/com/motogeek/telemetry/DomainLogicTest.kt`
- **Acceptance Criteria**:
  - [x] Evaluate `firstBreakInAtKm != null && lastLog == null` branch before `intervalKm` when computing `kmProgress`.
  - [ ] Anchor first due mileage to `motorcycle.baseOdometerKm` when `lastLog == null` for used motorcycles registered above 0 km (and skip 1,000 km break-in warning if `baseOdometerKm` already passed break-in).
  - [x] Support `yyyy.MM.dd` and `yyyy/MM/dd` in `SUPPORTED_FORMATTERS`.

### `BACKLOG-008` Fix `MainViewModel` Quick Service, New-Bike Tasks, Full-Tank Fuel & TCO Ownership Distance *(Completed)*
- **Audit Ref**: `01 §2.4`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/MainViewModel.kt`, `app/src/main/java/com/motogeek/telemetry/domain/maintenance/MotorcycleTemplateCatalog.kt`
- **Acceptance Criteria**:
  - [x] `addNewMotorcycle` seeds default factory tasks localized to `_appLanguage.value.code`.
  - [x] `applyTemplate` regenerates maintenance tasks using `MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, _appLanguage.value.code)`.
  - [x] `logQuickServiceByCategory` maps standard categories deterministically (never orphan random UUID for standard items).
  - [x] `fuelEconomyStats` accumulates partial fill-ups into full-tank segments (`FuelLogEntity.isFullTank`).
  - [x] `tcoStats` calculates `costPerKm` using owner-ridden distance (`(totalOdometerKm - baseOdometerKm).coerceAtLeast(accumulatedTrackedKm)`), not prior owners' odometer.

### `BACKLOG-009` Complete `MotorcycleTemplateCatalog` Full Technical Specs Across All 6 Templates *(Completed)*
- **Audit Ref**: `01 §2.5`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/domain/maintenance/MotorcycleTemplateCatalog.kt`
- **Acceptance Criteria**:
  - [x] Pass `mt09`'s complete `technicalSpecs` into `buildStandardMaintenanceTasks(...)` instead of the partial 7-field DTO.
  - [x] Populate full torques, fluid capacities, spark plug, valve clearance, and tire sizes across `cb650r`, `r1250gs`, `ktm890`, `z900`, and `universal`.

### `BACKLOG-010` Fix DSP Butterworth Cutoff Math, GPS Yaw Rate Link & Barometer-Less Altitude Filter *(Completed)*
- **Audit Ref**: `01 §2.6`
- **Files to Modify**:
  - `app/src/main/java/com/motogeek/telemetry/domain/dsp/BiquadButterworthFilter.kt`
  - `app/src/main/java/com/motogeek/telemetry/domain/dsp/MotorcycleSensorFusionEngine.kt`
  - `app/src/main/java/com/motogeek/telemetry/service/TelemetryForegroundService.kt`
- **Acceptance Criteria**:
  - [x] Fix `omega = 2.0 * PI * (cutoffHz / sampleRateHz)` in `BiquadButterworthFilter` and add frequency-response unit test.
  - [x] Compute real GPS heading yaw rate (`deg/sec`) from consecutive GPS bearings for `PocketModeCalibrator`.
  - [x] Increase GPS-only altitude deadband/smoothing ($\ge 5\text{--}8\text{ m}$ + EMA) when barometer sensor is absent.

### `BACKLOG-011` Add Delete Confirmations & Snapshot Restore Alert Guards Across Garage Screens *(Completed)*
- **Audit Ref**: `01 §2.7`
- **Files to Modify**: `FuelScreen.kt`, `PartsScreen.kt`, `RemindersScreen.kt`, `LogsScreen.kt`, `OfflineBackupScreen.kt`
- **Acceptance Criteria**:
  - [x] Add confirmation dialogs (or Undo snackbar) before deleting fuel logs, part logs, reminders, or clearing activity logs.
  - [x] Require confirmation dialog before `"Undo Last Replace (Restore Safety Snapshot)"` overwrites local data.

### `BACKLOG-012` Wire Live Cockpit Metrics, No-Bike Warning Banner & Landscape Mode Selector *(Completed)*
- **Audit Ref**: `01 §2.8`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/CockpitScreen.kt`, `app/src/main/java/com/motogeek/telemetry/service/LiveTelemetryState.kt`
- **Acceptance Criteria**:
  - [x] Replace hardcoded `"N/A"` stubs in `CircuitLayout` (#L621–L622) with real live/latest corner score and roll-rate values from telemetry state.
  - [x] Display localized banner prompting bike selection when `activeBike == null`.
  - [x] Expose layout switcher (`APEX-R`, `CIRCUIT`, `LAB`) and auto-start/boot toggles in landscape orientation cleanly.

---

## `[P1 - Complete Slovak (sk) Localization]`

### `BACKLOG-013` Localize Priority Telemetry & Analytics Screens (`CockpitScreen`, `DashboardScreen`, `TelemetryHistoryScreen`) *(Completed)*
- **Audit Ref**: `02 §2.1, §2.2, §2.3`
- **Files to Modify**: `CockpitScreen.kt`, `DashboardScreen.kt`, `TelemetryHistoryScreen.kt`
- **Acceptance Criteria**:
  - [x] Zero remaining hardcoded English user-facing strings across all 3 screens (100% wrapped in `tr(en, sk)`).
  - [x] Translate status tokens (`LOCKED`, `CALIBRATING`, `UNLOCKED`), style badges (`TOURER`, `TECHNICIAN`, `RACER`), metrics, dialogs, and accessibility labels cleanly into natural Slovak.

### `BACKLOG-014` Localize Bike Profile & Backup Screens (`BikeInfoScreen`, `OfflineBackupScreen`) *(Completed)*
- **Audit Ref**: `02 §2.4, §2.8`
- **Files to Modify**: `BikeInfoScreen.kt`, `OfflineBackupScreen.kt`, `MaintenanceDtos.kt`
- **Acceptance Criteria**:
  - [x] Wrap all >50 strings in `BikeInfoScreen` (profile form, specs, torque search, AI extractor text/buttons, template cards, 3+ dialogs, snackbars) in `tr(en, sk)`.
  - [x] Add localized torque label mapper for the 11 standard torque keys (`toConsolidatedTorqueMap(langCode)` / UI mapper while preserving zero-arg EN compatibility).
  - [x] Wrap all 30+ body, Drive card, auth/error snackbars, and restore dialog strings in `OfflineBackupScreen` in `tr(en, sk)`.

### `BACKLOG-015` Localize Garage Ledger & Utility Screens (`FuelScreen`, `PartsScreen`, `RemindersScreen`, `BikeDataExportScreen`, `LogsScreen`, `AboutAndDiagnosticsScreen`) *(Completed)*
- **Audit Ref**: `02 §2.5, §2.6, §2.7, §2.9, §2.10, §2.11`
- **Files to Modify**: `FuelScreen.kt`, `PartsScreen.kt`, `RemindersScreen.kt`, `BikeDataExportScreen.kt`, `LogsScreen.kt`, `AboutAndDiagnosticsScreen.kt`
- **Acceptance Criteria**:
  - [x] Replace every hardcoded English title, label, placeholder, filter chip, snackbar, empty state, and dialog in all 6 screens with `tr(en, sk)`.

### `BACKLOG-016` Localize Home Remaining Strings, Reusable Components & Social Card Export *(Completed)*
- **Audit Ref**: `02 §2.12`
- **Files to Modify**: `HomeScreen.kt`, `MainActivity.kt`, `MotoGeekDrawerSheet.kt`, `ActiveBikeDropdownCard.kt`, `RideTrajectoryHeatmapCanvas.kt`, `SynchronizedTelemetryChart.kt`, `RideSocialCardGenerator.kt`
- **Acceptance Criteria**:
  - [x] Translate remaining `HomeScreen` mini-KPIs/units/LIVE badge/icons, `ActiveBikeDropdownCard` & Sync dialog, heatmap/chart overlays, `MainActivity` `"Back"`/`"Menu"`, and drawer jargon (`Maintenance & Service`).
  - [x] Pass `AppLang` into `RideSocialCardGenerator.generateAndSaveCard(...)` to render Slovak labels (`VZDIALENOSŤ`, `MAX NÁKLON (Ľ|P)`, `ZÁKRUTY`, `ŠTÝL`, `PRIEM. SKÓRE`, etc.).

### `BACKLOG-017` Localize Background Services, Notifications, Domain Exports, Activity Logs & Android Resources *(Completed)*
- **Audit Ref**: `02 §1, §3`
- **Files to Modify**:
  - `service/TelemetryForegroundService.kt`, `worker/MaintenanceCheckWorker.kt`
  - `domain/maintenance/ImportExportManager.kt`, `domain/backup/GoogleDriveBackupManager.kt`
  - `ui/MainViewModel.kt`, `data/local/MotorcycleEntity.kt` / `ServiceLogEntity.kt`
  - `res/values/strings.xml`, new `res/values-sk/strings.xml`, new `res/xml/locales_config.xml`, `AndroidManifest.xml`
- **Acceptance Criteria**:
  - [x] Services and `MaintenanceCheckWorker` read `TelemetryPreferences(context).appLanguageCode` to render localized notification channels, titles, and bodies in EN or SK.
  - [x] HTML Digital Service Book, CSV headers/fuel details, and Share intent choosers support both EN and SK based on active `AppLang`.
  - [x] Map Google Drive errors to localized EN/SK user-facing messages; localize activity log generation and add proper Slovak pluralization helper (1 / 2–4 / 5+ forms).
  - [x] Add `res/values-sk/strings.xml` and Android 13+ `locales_config.xml`, and align `app_name` to `KurveCounter`.

---

## `[P2 - Feature Completeness, UX Polish & Component Fixes]`

### `BACKLOG-018` Fix Canvas State Mutation, Chart Gesture Conflict, Social Card Bugs & Euro Currency Hardcoding *(Completed)*
- **Audit Ref**: `01 §3.5, §3.6`
- **Files to Modify**: `RideTrajectoryHeatmapCanvas.kt`, `SynchronizedTelemetryChart.kt`, `RideSocialCardGenerator.kt`, `LogsScreen.kt`, `HomeScreen.kt`
- **Acceptance Criteria**:
  - [x] Precompute `cornerPinOffsets` outside `Canvas` draw scope in `RideTrajectoryHeatmapCanvas`; unify tap & horizontal drag gestures into a single `pointerInput` in `SynchronizedTelemetryChart`.
  - [x] Fix `RideSocialCardGenerator` blank-bike UUID fallback and preserve ride date alongside custom ride name.
  - [x] Use `activeBike.currencySymbol` instead of `"€"` in `LogsScreen`; remove unused `tasks` collection in `HomeScreen.kt:32`.

### `BACKLOG-019` Complete `RemindersScreen` Edit/DatePicker/Direct-Task Link, `Fuel`/`Parts`/`BikeInfo`/`Dashboard`/`About` Polish *(Partially Completed)*
- **Audit Ref**: `01 §3.1, §3.2, §3.3, §3.4, §3.7, §3.9`
- **Files to Modify**: `RemindersScreen.kt`, `PartsScreen.kt`, `FuelScreen.kt`, `BikeInfoScreen.kt`, `DashboardScreen.kt`, `AboutAndDiagnosticsScreen.kt`, `OfflineBackupScreen.kt`
- **Acceptance Criteria**:
  - [x] `DashboardScreen` displays `fuelStats.avgLitersPer100Km` (`avgTrailBrakingScore` card remains pending).
  - [x] `RemindersScreen` supports editing custom reminders and AutoAlert `"Service"` navigates to Maintenance Hub (Material3 DatePicker and direct due-task focus remain pending).
  - [x] `FuelScreen` validates positive liters; `BikeInfoScreen` supports Bar input in `EditTechnicalSpecsDialog` and permits deleting/resetting single bike cleanly; `AboutAndDiagnosticsScreen` distinguishes `"UNAVAILABLE"` vs `"SIMULATED"`, uses live app version, and offers `0°` calibration; `OfflineBackupScreen` shows local export/import spinner and friendly Drive errors (`PartsScreen` category selector remains pending).

### `BACKLOG-020` Sync Documentation (`MASTER_SPEC_AND_PLAN.md` & New Root `README.md`) *(Partially Completed)*
- **Audit Ref**: `01 §3.10`
- **Files to Modify**: `docs/MASTER_SPEC_AND_PLAN.md`, new `README.md`
- **Acceptance Criteria**:
  - [ ] Update `MASTER_SPEC_AND_PLAN.md` to reflect `BikeInfoScreen` owning templates & AI extractor (commit `07068e1`) and new `+ LOG SERVICE` multi-task checklist.
  - [x] Create root `README.md` with build prerequisites (JDK 17/21), architecture overview, features, and localization guide.

---

## `[P3 - Test Coverage & Build Hygiene]`

### `BACKLOG-021` Expand JVM Unit Tests & Add `androidTest` Suite *(Partially Completed)*
- **Audit Ref**: `01 §4.1`
- **Files to Modify**: New tests under `app/src/test/java/com/motogeek/telemetry/` and `app/src/androidTest/java/com/motogeek/telemetry/`
- **Acceptance Criteria**:
  - [x] Add unit tests for `ImportExportManager` (CSV/GPX/GoPro/HTML/MERGE), `BiquadButterworthFilter` cutoff response, `MotorcycleTemplateCatalog` specs, `MotorcycleSensorFusionEngine` roll/yaw metrics, and `MaintenanceEvaluator` edge cases (`BACKLOG-007`).
  - [ ] Add instrumented/Room migration test covering `MIGRATION_1_2` (`BACKLOG-003`) and basic Compose UI smoke tests.

### `BACKLOG-022` Clean Up `app/build.gradle.kts`, Deprecated Icons, Warnings & CI Config *(Completed)*
- **Audit Ref**: `01 §4.2, §4.3`
- **Files to Modify**: `app/build.gradle.kts`, `gradle/libs.versions.toml`, Compose UI icons across screens, `.github/workflows/`
- **Acceptance Criteria**:
  - [x] Remove duplicate `google-http-client-gson` and duplicate outer `dependencies` block in `app/build.gradle.kts`; centralize versions in `libs.versions.toml`; document JDK 17/21 requirement or configure Gradle Java toolchain.
  - [x] Migrate deprecated `Icons.Default.ArrowBack` to `Icons.AutoMirrored.Filled.ArrowBack` and clean up unused locals.
  - [x] Audit `.github/workflows/ci.yml` (add `lintDebug`) and verify `.github/workflows/cleanup-artifacts.yml` aligns with workspace CI policy in root `AGENTS.md`.
