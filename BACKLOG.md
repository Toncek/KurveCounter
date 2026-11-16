# KurveCounter (MotoGeekTelemetry) — Prioritized Engineering & Localization Backlog

**Last Updated**: November 16, 2026  
**Audit Sources**:
- [01 — Missing Features, Bugs, Architecture & Test Gaps](audits/01_missing_features_and_bugs_audit.md)
- [02 — Slovak (`sk`) Translation & Localization Gaps](audits/02_slovak_localization_audit.md)

---

## Recently Completed

- [x] **TASK-MAINT-HUB-01 — Interactive `+ LOG SERVICE` Checklist, Sub-Actions, Task Notes Column, `(i)` Repair Guide & Overdue Auto-Clear**
  - **Implemented**: Overhauled `MaintenanceServiceScreen.kt`, added `ServiceChecklistCatalog.kt`, extended `ServiceChecklistItem` in `ServiceLogEntity.kt`, updated `MaintenanceEvaluator.kt` to match checklist task completions, fixed `"Vytite"` typo and added extended EN/SK Repair Guides in `MotorcycleTemplateCatalog.kt`, and localized `MaintenanceServiceScreen.kt` 100% EN/SK.
  - **Verified**: `./gradlew testDebugUnitTest` including `ServiceChecklistAndMaintenanceTest.kt`.

---

## `[P0 - Release Blockers & Data Integrity]`

### `BACKLOG-001` Fix `exportRideAsCsv` Infinite Flow Hang
- **Audit Ref**: `01 §1.1`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/domain/maintenance/ImportExportManager.kt`
- **Acceptance Criteria**:
  - Replace `.collect { ... }` on `repository.getTelemetryTrackPointsForRide(rideId)` with `repository.getTelemetryTrackPointsListForRide(rideId)` in `exportRideAsCsv` (#L605).
  - Unit test verifies `exportRideAsCsv` terminates cleanly, closes output stream, and writes all expected trackpoint CSV rows.

### `BACKLOG-002` Fix `RestoreMode.MERGE` Parent Replace Cascade & Add Room Transactions
- **Audit Ref**: `01 §1.2`
- **Files to Modify**:
  - `app/src/main/java/com/motogeek/telemetry/data/local/MotoGeekDao.kt`
  - `app/src/main/java/com/motogeek/telemetry/data/local/MotoGeekRepository.kt`
  - `app/src/main/java/com/motogeek/telemetry/domain/maintenance/ImportExportManager.kt`
- **Acceptance Criteria**:
  - In `MERGE` restore and AI manual import onto an existing motorcycle, use `updateMotorcycle` instead of `OnConflictStrategy.REPLACE` `insertMotorcycle`.
  - Add service-log duplicate detection during `MERGE` and honor `targetMotorcycleId` when importing Format A single-bike payloads or warn explicitly.
  - Wrap full replace/merge database mutations in Room `@Transaction`.
  - Unit tests confirm merging a partial backup never deletes local rides, fuel, service, or part logs on an existing motorcycle.

### `BACKLOG-003` Fix `MIGRATION_1_2` Missing `app_logs` Table (v1 -> v2 Upgrade Crash)
- **Audit Ref**: `01 §1.3`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/data/local/MotoGeekDatabase.kt`
- **Acceptance Criteria**:
  - Add `CREATE TABLE IF NOT EXISTS app_logs (...)` and `CREATE INDEX IF NOT EXISTS index_app_logs_motorcycleId` inside `MIGRATION_1_2`.
  - Enable `exportSchema = true` (configuring Room schema output directory) and verify v1->v2 migration integrity with a test.

### `BACKLOG-004` Fix Dead `"CALIBRATE"` Odometer Button in `BikeInfoScreen`
- **Audit Ref**: `01 §1.4`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/BikeInfoScreen.kt`
- **Acceptance Criteria**:
  - Clicking `"CALIBRATE"` opens an odometer calibration dialog prompting the rider for a new dashboard reading instead of calling `viewModel.updateActiveBikeOdometer(it.totalOdometerKm)`.
  - Entered reading calls `viewModel.updateActiveBikeOdometer(parsedKm)` and updates the displayed total odometer and offset immediately.

---

## `[P1 - High-Priority Functionality, Stability & Logic]`

### `BACKLOG-005` Fix Android 10–15 Boot Auto-Start & Foreground Service Crash Guards
- **Audit Ref**: `01 §2.1`
- **Files to Modify**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/motogeek/telemetry/service/BootCompletedReceiver.kt`
  - `app/src/main/java/com/motogeek/telemetry/MainActivity.kt`
  - `app/src/main/java/com/motogeek/telemetry/ui/CockpitScreen.kt`
- **Acceptance Criteria**:
  - Declare `ACCESS_BACKGROUND_LOCATION` in `AndroidManifest.xml` (or update boot flow to respect OS FGS background-launch exemptions cleanly with user rationale prompt in Settings/Cockpit).
  - Wrap all `startForegroundService` / `startService` calls in `MainActivity` and `CockpitScreen` (`startRideService`, `stopRideService`, `ACTION_CALIBRATE_ZERO`) with `ForegroundServiceStartNotAllowedException` / `IllegalStateException` guards.

### `BACKLOG-006` Fix `TelemetryHistoryScreen` Double TopAppBar, System Back & Empty State
- **Audit Ref**: `01 §2.2`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/TelemetryHistoryScreen.kt`, `app/src/main/java/com/motogeek/telemetry/MainActivity.kt`
- **Acceptance Criteria**:
  - Remove double `TopAppBar` stacking when `selectedRide != null` (either suppress `MainActivity` bar for ride details or remove nested bar cleanly).
  - Add `BackHandler(enabled = selectedRide != null) { selectedRide = null }`.
  - Display localized empty-state card when no rides are recorded.

### `BACKLOG-007` Fix `MaintenanceEvaluator` Break-In Progress, Used-Bike Baseline & Date Formats
- **Audit Ref**: `01 §2.3`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/domain/maintenance/MaintenanceEvaluator.kt`, `app/src/test/java/com/motogeek/telemetry/DomainLogicTest.kt`
- **Acceptance Criteria**:
  - Evaluate `firstBreakInAtKm != null && lastLog == null` branch before `intervalKm` when computing `kmProgress`.
  - Anchor first due mileage to `motorcycle.baseOdometerKm` when `lastLog == null` for used motorcycles registered above 0 km (and skip 1,000 km break-in warning if `baseOdometerKm` already passed break-in).
  - Support `yyyy.MM.dd` and `yyyy/MM/dd` in `SUPPORTED_FORMATTERS`.

### `BACKLOG-008` Fix `MainViewModel` Quick Service, New-Bike Tasks, Full-Tank Fuel & TCO Ownership Distance
- **Audit Ref**: `01 §2.4`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/MainViewModel.kt`, `app/src/main/java/com/motogeek/telemetry/domain/maintenance/MotorcycleTemplateCatalog.kt`
- **Acceptance Criteria**:
  - `addNewMotorcycle` seeds default factory tasks localized to `_appLanguage.value.code`.
  - `applyTemplate` regenerates maintenance tasks using `MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, _appLanguage.value.code)`.
  - `logQuickServiceByCategory` maps standard categories deterministically (never orphan random UUID for standard items).
  - `fuelEconomyStats` accumulates partial fill-ups into full-tank segments (`FuelLogEntity.isFullTank`).
  - `tcoStats` calculates `costPerKm` using owner-ridden distance (`(totalOdometerKm - baseOdometerKm).coerceAtLeast(accumulatedTrackedKm)`), not prior owners' odometer.

### `BACKLOG-009` Complete `MotorcycleTemplateCatalog` Full Technical Specs Across All 6 Templates
- **Audit Ref**: `01 §2.5`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/domain/maintenance/MotorcycleTemplateCatalog.kt`
- **Acceptance Criteria**:
  - Pass `mt09`'s complete `technicalSpecs` into `buildStandardMaintenanceTasks(...)` instead of the partial 7-field DTO.
  - Populate full torques, fluid capacities, spark plug, valve clearance, and tire sizes across `cb650r`, `r1250gs`, `ktm890`, `z900`, and `universal`.

### `BACKLOG-010` Fix DSP Butterworth Cutoff Math, GPS Yaw Rate Link & Barometer-Less Altitude Filter
- **Audit Ref**: `01 §2.6`
- **Files to Modify**:
  - `app/src/main/java/com/motogeek/telemetry/domain/dsp/BiquadButterworthFilter.kt`
  - `app/src/main/java/com/motogeek/telemetry/domain/dsp/MotorcycleSensorFusionEngine.kt`
  - `app/src/main/java/com/motogeek/telemetry/service/TelemetryForegroundService.kt`
- **Acceptance Criteria**:
  - Fix `omega = 2.0 * PI * (cutoffHz / sampleRateHz)` in `BiquadButterworthFilter` and add frequency-response unit test.
  - Compute real GPS heading yaw rate (`deg/sec`) from consecutive GPS bearings for `PocketModeCalibrator`.
  - Increase GPS-only altitude deadband/smoothing ($\ge 5\text{--}8\text{ m}$ + EMA) when barometer sensor is absent.

### `BACKLOG-011` Add Delete Confirmations & Snapshot Restore Alert Guards Across Garage Screens
- **Audit Ref**: `01 §2.7`
- **Files to Modify**: `FuelScreen.kt`, `PartsScreen.kt`, `RemindersScreen.kt`, `LogsScreen.kt`, `OfflineBackupScreen.kt`
- **Acceptance Criteria**:
  - Add confirmation dialogs (or Undo snackbar) before deleting fuel logs, part logs, reminders, or clearing activity logs.
  - Require confirmation dialog before `"Undo Last Replace (Restore Safety Snapshot)"` overwrites local data.

### `BACKLOG-012` Wire Live Cockpit Metrics, No-Bike Warning Banner & Landscape Mode Selector
- **Audit Ref**: `01 §2.8`
- **Files to Modify**: `app/src/main/java/com/motogeek/telemetry/ui/CockpitScreen.kt`, `app/src/main/java/com/motogeek/telemetry/service/LiveTelemetryState.kt`
- **Acceptance Criteria**:
  - Replace hardcoded `"N/A"` stubs in `CircuitLayout` (#L621–L622) with real live/latest corner score and roll-rate values from telemetry state.
  - Display localized banner prompting bike selection when `activeBike == null`.
  - Expose layout switcher (`APEX-R`, `CIRCUIT`, `LAB`) and auto-start/boot toggles in landscape orientation cleanly.

---

## `[P1 - Complete Slovak (sk) Localization]`

### `BACKLOG-013` Localize Priority Telemetry & Analytics Screens (`CockpitScreen`, `DashboardScreen`, `TelemetryHistoryScreen`)
- **Audit Ref**: `02 §2.1, §2.2, §2.3`
- **Files to Modify**: `CockpitScreen.kt`, `DashboardScreen.kt`, `TelemetryHistoryScreen.kt`
- **Acceptance Criteria**:
  - Zero remaining hardcoded English user-facing strings across all 3 screens (100% wrapped in `tr(en, sk)`).
  - Translate status tokens (`LOCKED`, `CALIBRATING`, `UNLOCKED`), style badges (`TOURER`, `TECHNICIAN`, `RACER`), metrics, dialogs, and accessibility labels cleanly into natural Slovak.

### `BACKLOG-014` Localize Bike Profile & Backup Screens (`BikeInfoScreen`, `OfflineBackupScreen`)
- **Audit Ref**: `02 §2.4, §2.8`
- **Files to Modify**: `BikeInfoScreen.kt`, `OfflineBackupScreen.kt`, `MaintenanceDtos.kt`
- **Acceptance Criteria**:
  - Wrap all >50 strings in `BikeInfoScreen` (profile form, specs, torque search, AI extractor text/buttons, template cards, 3+ dialogs, snackbars) in `tr(en, sk)`.
  - Add localized torque label mapper for the 11 standard torque keys (`toConsolidatedTorqueMap(langCode)` / UI mapper while preserving zero-arg EN compatibility).
  - Wrap all 30+ body, Drive card, auth/error snackbars, and restore dialog strings in `OfflineBackupScreen` in `tr(en, sk)`.

### `BACKLOG-015` Localize Garage Ledger & Utility Screens (`FuelScreen`, `PartsScreen`, `RemindersScreen`, `BikeDataExportScreen`, `LogsScreen`, `AboutAndDiagnosticsScreen`)
- **Audit Ref**: `02 §2.5, §2.6, §2.7, §2.9, §2.10, §2.11`
- **Files to Modify**: `FuelScreen.kt`, `PartsScreen.kt`, `RemindersScreen.kt`, `BikeDataExportScreen.kt`, `LogsScreen.kt`, `AboutAndDiagnosticsScreen.kt`
- **Acceptance Criteria**:
  - Replace every hardcoded English title, label, placeholder, filter chip, snackbar, empty state, and dialog in all 6 screens with `tr(en, sk)`.

### `BACKLOG-016` Localize Home Remaining Strings, Reusable Components & Social Card Export
- **Audit Ref**: `02 §2.12`
- **Files to Modify**: `HomeScreen.kt`, `MainActivity.kt`, `MotoGeekDrawerSheet.kt`, `ActiveBikeDropdownCard.kt`, `RideTrajectoryHeatmapCanvas.kt`, `SynchronizedTelemetryChart.kt`, `RideSocialCardGenerator.kt`
- **Acceptance Criteria**:
  - Translate remaining `HomeScreen` mini-KPIs/units/LIVE badge/icons, `ActiveBikeDropdownCard` & Sync dialog, heatmap/chart overlays, `MainActivity` `"Back"`/`"Menu"`, and drawer jargon (`Maintenance & Service`).
  - Pass `AppLang` into `RideSocialCardGenerator.generateAndSaveCard(...)` to render Slovak labels (`VZDIALENOSŤ`, `MAX NÁKLON (Ľ|P)`, `ZÁKRUTY`, `ŠTÝL`, `PRIEM. SKÓRE`, etc.).

### `BACKLOG-017` Localize Background Services, Notifications, Domain Exports, Activity Logs & Android Resources
- **Audit Ref**: `02 §1, §3`
- **Files to Modify**:
  - `service/TelemetryForegroundService.kt`, `worker/MaintenanceCheckWorker.kt`
  - `domain/maintenance/ImportExportManager.kt`, `domain/backup/GoogleDriveBackupManager.kt`
  - `ui/MainViewModel.kt`, `data/local/MotorcycleEntity.kt` / `ServiceLogEntity.kt`
  - `res/values/strings.xml`, new `res/values-sk/strings.xml`, new `res/xml/locales_config.xml`, `AndroidManifest.xml`
- **Acceptance Criteria**:
  - Services and `MaintenanceCheckWorker` read `TelemetryPreferences(context).appLanguageCode` to render localized notification channels, titles, and bodies in EN or SK.
  - HTML Digital Service Book, CSV headers/fuel details, and Share intent choosers support both EN and SK based on active `AppLang`.
  - Map Google Drive errors to localized EN/SK user-facing messages; localize activity log generation and add proper Slovak pluralization helper (1 / 2–4 / 5+ forms).
  - Add `res/values-sk/strings.xml` and Android 13+ `locales_config.xml`, and align `app_name` to `KurveCounter`.

---

## `[P2 - Feature Completeness, UX Polish & Component Fixes]`

### `BACKLOG-018` Fix Canvas State Mutation, Chart Gesture Conflict, Social Card Bugs & Euro Currency Hardcoding
- **Audit Ref**: `01 §3.5, §3.6`
- **Files to Modify**: `RideTrajectoryHeatmapCanvas.kt`, `SynchronizedTelemetryChart.kt`, `RideSocialCardGenerator.kt`, `LogsScreen.kt`, `HomeScreen.kt`
- **Acceptance Criteria**:
  - Precompute `cornerPinOffsets` outside `Canvas` draw scope in `RideTrajectoryHeatmapCanvas`; unify tap & horizontal drag gestures into a single `pointerInput` in `SynchronizedTelemetryChart`.
  - Fix `RideSocialCardGenerator` blank-bike UUID fallback and preserve ride date alongside custom ride name.
  - Use `activeBike.currencySymbol` instead of `"€"` in `LogsScreen`; remove unused `tasks` collection in `HomeScreen.kt:32`.

### `BACKLOG-019` Complete `RemindersScreen` Edit/DatePicker/Direct-Task Link, `Fuel`/`Parts`/`BikeInfo`/`Dashboard`/`About` Polish
- **Audit Ref**: `01 §3.1, §3.2, §3.3, §3.4, §3.7, §3.9`
- **Files to Modify**: `RemindersScreen.kt`, `PartsScreen.kt`, `FuelScreen.kt`, `BikeInfoScreen.kt`, `DashboardScreen.kt`, `AboutAndDiagnosticsScreen.kt`, `OfflineBackupScreen.kt`
- **Acceptance Criteria**:
  - `DashboardScreen` displays `avgTrailBrakingScore` and `fuelStats.avgLitersPer100Km`.
  - `RemindersScreen` supports editing custom reminders, Material3 DatePicker, and AutoAlert `"Service"` navigates cleanly to Maintenance Hub focused on the due task.
  - `PartsScreen` validates non-blank names and supports categories; `FuelScreen` validates positive liters; `BikeInfoScreen` supports Bar input in `EditTechnicalSpecsDialog` and permits deleting/resetting single bike cleanly; `AboutAndDiagnosticsScreen` distinguishes `"UNAVAILABLE"` vs `"SIMULATED"`, uses live app version, and offers `0°` calibration; `OfflineBackupScreen` shows local export/import spinner and friendly Drive errors.

### `BACKLOG-020` Sync Documentation (`MASTER_SPEC_AND_PLAN.md` & New Root `README.md`)
- **Audit Ref**: `01 §3.10`
- **Files to Modify**: `docs/MASTER_SPEC_AND_PLAN.md`, new `README.md`
- **Acceptance Criteria**:
  - Update `MASTER_SPEC_AND_PLAN.md` to reflect `BikeInfoScreen` owning templates & AI extractor (commit `07068e1`) and new `+ LOG SERVICE` multi-task checklist.
  - Create root `README.md` with build prerequisites (JDK 17/21), architecture overview, features, and localization guide.

---

## `[P3 - Test Coverage & Build Hygiene]`

### `BACKLOG-021` Expand JVM Unit Tests & Add `androidTest` Suite
- **Audit Ref**: `01 §4.1`
- **Files to Modify**: New tests under `app/src/test/java/com/motogeek/telemetry/` and `app/src/androidTest/java/com/motogeek/telemetry/`
- **Acceptance Criteria**:
  - Add unit tests for `ImportExportManager` (CSV/GPX/GoPro/HTML/MERGE), `MainViewModel`, `BiquadButterworthFilter` cutoff response, and `MaintenanceEvaluator` edge cases (`BACKLOG-007`).
  - Add instrumented/Room migration test covering `MIGRATION_1_2` (`BACKLOG-003`) and basic Compose UI smoke tests.

### `BACKLOG-022` Clean Up `app/build.gradle.kts`, Deprecated Icons, Warnings & CI Config
- **Audit Ref**: `01 §4.2, §4.3`
- **Files to Modify**: `app/build.gradle.kts`, `gradle/libs.versions.toml`, Compose UI icons across screens, `.github/workflows/`
- **Acceptance Criteria**:
  - Remove duplicate `google-http-client-gson` and duplicate outer `dependencies` block in `app/build.gradle.kts`; centralize versions in `libs.versions.toml`; document JDK 17/21 requirement or configure Gradle Java toolchain.
  - Migrate deprecated `Icons.Default.ArrowBack` to `Icons.AutoMirrored.Filled.ArrowBack` and clean up unused locals.
  - Audit `.github/workflows/ci.yml` (add `lintDebug`) and verify `.github/workflows/cleanup-artifacts.yml` aligns with workspace CI policy in root `AGENTS.md`.
