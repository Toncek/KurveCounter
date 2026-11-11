# KurveCounter (MotoGeekTelemetry) — Master Specification & Step-by-Step LLM Implementation Plan

## 1. Executive Codebase Inspection & Audit

After inspecting all source files in `app/src/main` and `app/src/test`, here is a candid assessment of what works today and the critical bugs/gaps holding the app back from being reliable in real-world riding and garage use.

### What Already Exists (Strengths)
- **Visual Direction**: Cohesive **ApexCarbon TFT** dark theme (`Color.kt`, `SegmentedDigitalText.kt`) with high-contrast gauges, trajectory heatmaps (`RideTrajectoryHeatmapCanvas.kt`), and synchronized telemetry scrubbing (`SynchronizedTelemetryChart.kt`).
- **Core Data Entities**: Room database v2 (`MotoGeekDatabase.kt`) covers 10 tables: motorcycles, ride sessions, corner events, track points, maintenance tasks, service logs, fuel logs, part logs, reminders, and app activity logs.
- **DSP Foundation**: `BiquadButterworthFilter.kt` and `PocketModeCalibrator.kt` implement finite-guarded low-pass filtering and gravity/heading alignment.

---

### Critical Findings (Must Fix)

> [!CAUTION]
> **P0 Bug #1 — Live Corner Detection & Gyro Integration Are Broken by a Timestamp Unit Mismatch**
> In `TelemetryForegroundService.onSensorChanged`, `val ts = System.currentTimeMillis()` (milliseconds) was passed into `MotorcycleSensorFusionEngine.processImu(timestampNanos: Long, ...)`:
> 1. `dtSec = ((timestampNanos - lastImuTimestampNanos) / 1_000_000_000.0).coerceIn(0.001, 0.05)` divided milliseconds by $10^9$, clamping `dtSec` to `0.001s` instead of `0.01s` (making gyro roll integration **10× too weak**).
> 2. `updateCornerStateMachine(timestampNanos / 1_000_000, rollRateDeg)` divided milliseconds by $10^6$. Inside `updateCornerStateMachine`, `timestampMs - cornerStartTimeMs >= 1200` required **1,200,000 seconds (~13.8 days) of continuous leaning** to confirm a single corner!

> [!CAUTION]
> **P0 Bug #2 — Backup & Restore Silently Drops Most User Data & Applying a Template Wipes the Garage**
> 1. **Incomplete Backup Payload**: `GarageBackupPayload` and `ImportExportManager.generateBackupJsonString` previously exported only basic motorcycle fields, maintenance tasks, and partial service logs.
>    - **Completely lost on backup/restore**: All `RideSessionEntity`, `CornerEventEntity`, `TelemetryTrackPointEntity`, `FuelLogEntity`, `PartLogEntity`, `ReminderEntity`, `AppLogEntity`, and `TelemetryPreferences`.
>    - **Partially lost on backup/restore**: `MotorcycleEntity` lost `name`, `type`, `startDateIso`, `buyingPrice`, `currencySymbol`. `ServiceLogEntity` lost `serviceTitle`, `locationOrShop`, `servicedAtKm`, `limitKm`, `endKm`, and `checklistItemsJson` (16-point inspection results).
> 2. **Destructive Template Application**: `MainViewModel.applyTemplate` executed `bikes.forEach { repository.deleteMotorcycle(it.id) }`. Because all Room entities use `ForeignKey.CASCADE`, tapping **"Apply"** on any template in `OfflineBackupScreen` **permanently deleted every motorcycle, ride, fuel log, part, and service record in the database** without confirmation!

> [!WARNING]
> **P0 Bug #3 — Concurrency, Foreign-Key Race Conditions & Android 14/15 Service Crashes**
> 1. **Buffer Race (`ConcurrentModificationException`)**: In `TelemetryForegroundService`, `trackPointBuffer` and `cornerEventBuffer` were plain `mutableListOf()` mutated on `ImuSensorThread` and `MainLooper` while `flushBuffersToDatabase()` read and cleared them on `Dispatchers.IO`.
> 2. **ForeignKey Crash (`SQLiteConstraintException`)**: In `startRide()`, `activeRideId = repository.insertRideSession(...)` ran asynchronously on `Dispatchers.IO`. Any GPS or corner points collected before the insert completed carried `rideId = 0L`, violating the `ForeignKey` constraint on flush.
> 3. **Android 14/15 Boot Start Crash**: `BootCompletedReceiver` started `TelemetryForegroundService` (`foregroundServiceType="location"`) on boot without `ACCESS_BACKGROUND_LOCATION` permission and without catching `ForegroundServiceStartNotAllowedException` / `SecurityException`.
> 4. **Stale State Across Rides & Dead Barometer**: `fusionEngine.reset()` was never called in `startRide()`. `Sensor.TYPE_PRESSURE` was registered at 100Hz in `registerSensors()` but never handled in `onSensorChanged()`, and `elevationGainMeters`, `maxTurnInRateDegPerSec`, `trailBrakingScorePct`, and `avgCornerScore` were hardcoded to `0.0` in `stopRide()`.
> 5. **Calibration & Peak Reset Disconnect**: `calibrateZero()` in `TelemetryForegroundService` never called `fusionEngine.calibrateManualZero()`.

> [!IMPORTANT]
> **P1 Bugs — Maintenance Evaluation, Repair Specs & UI Consistency**
> 1. **Date Parsing & Immediate Overdue Bug**: `MaintenanceEvaluator.evaluate` called `LocalDate.parse(it.performedAtIsoDate)` without a fallback (crashing if a user entered `01/01/2025` or non-ISO text). Worse, if `lastLog == null`, it computed `lastDate = currentDate.minusMonths(task.intervalMonths)`, making `nextDueDate == currentDate` (`remainingDays = 0`), so **every time-based task on a new bike was immediately marked `OVERDUE`**.
> 2. **Hidden Torque Specs**: `TechnicalSpecsDto` defines 11 specific torque fields (`frontCaliperBoltsTorqueNm`, `rearCaliperBoltsTorqueNm`, `frontAxleTorqueNm`, `frontPinchBoltsTorqueNm`, `oilFilterTorqueNm`, `sparkPlugTorqueNm`, `waterPumpDrainBoltTorqueNm`, `cylinderDrainBoltTorqueNm`, `brakeBleedNippleTorqueNm`, `rearAxleNutTorqueNm`, `oilDrainPlugTorqueNm`) plus `boltTorquesNm` and `torquesNm` maps. However, `BikeInfoScreen` only extracted **2** of them (`rearAxleNutTorqueNm`, `oilDrainPlugTorqueNm`) + `boltTorquesNm`, hiding 9 factory torques imported from AI service manuals!
> 3. **Random Task IDs & Mixed Languages**: `MotorcycleTemplateCatalog.buildStandardMaintenanceTasks` assigned `UUID.randomUUID()` to `taskId` instead of stable semantic keys and mixed Slovak procedure text into an otherwise English UI.
> 4. **Broken DAO Fuel Query**: `MotoGeekDao.getAvgFuelConsumption` computed `SUM(liters) * 100.0 / SUM(odometerKm)` instead of odometer deltas.
> 5. **Orphaned Screens & Duplicate Routes**: `MaintenanceScreen.kt` and `GarageSettingsScreen.kt` were unused dead files.
> 6. **Social Card Bug**: `RideSocialCardGenerator.kt` printed the raw UUID `ride.motorcycleId` in the header instead of the motorcycle's `displayName`.

---

## 2. Master Application Specification

### 2.1 Product Vision & Core Pillars
**KurveCounter (MotoGeekTelemetry)** is an all-in-one motorcycle companion designed around four core user journeys:
1. **Ride & Analyze (Telemetry)**: Mount the phone or slip it in a pocket, auto-calibrate 3D orientation, record 100Hz lean/G-force/GPS/altitude telemetry, and review corner-by-corner heatmaps and riding scores.
2. **Maintain & Track Costs (Garage & TCO)**: Track odometer monotonically across GPS rides, fuel fill-ups, and manual dash syncs; evaluate distance + time service intervals (`OK`, `DUE_SOON`, `OVERDUE`); and calculate real Total Cost of Ownership (TCO) and fuel economy.
3. **Repair & Wrench (Workshop Assistant)**: Give the home mechanic instant access to searchable tightening torques (Nm), fluid capacities/specs, tire pressures, valve clearances, OEM part numbers, and step-by-step repair guides—populated either from built-in templates or AI-extracted service manuals.
4. **Protect & Move Data (Lossless Backup & Restore)**: Full-fidelity local (SAF JSON) and cloud (Google Drive `appDataFolder`) backup and restore covering **100% of garage entities** with explicit **Merge** vs. **Replace** modes and automatic pre-restore safety snapshots.

### 2.2 Functional Specifications by Pillar

#### Pillar A: Ride Tracking & 100Hz Telemetry Engine
- **Timebase**: `MotorcycleSensorFusionEngine.processImu` uses `SensorEvent.timestamp` (monotonic hardware nanoseconds) for `dtSec` calculation, and passes wall-clock `System.currentTimeMillis()` separately for `CornerEventEntity.timestampMs`.
- **Barometric Elevation**: Processes `Sensor.TYPE_PRESSURE` at 5–10Hz (`SENSOR_DELAY_UI`) with an exponential moving average filter to compute accurate `elevationGainMeters` (accumulating positive climbs exceeding a 1.5m hysteresis deadband) and falls back to GPS altitude when no barometer is present.
- **Ride Session Summary Metrics**: On `stopRide()`, computes real values from the ride's recorded corners and track points:
  - `maxTurnInRateDegPerSec = corners.maxOfOrNull { it.maxRollRateDegPerSec } ?: 0.0`
  - `avgCornerScore = corners.map { it.cornerScore }.average()` (or `0.0` if empty)
  - `trailBrakingScorePct`: percentage of corners where braking G (`peakBrakingG < -0.15g`) overlapped with lean entry.
  - `elevationGainMeters`: barometric or smoothed GPS cumulative climb.
- **Thread-Safe Buffering**: Guards `trackPointBuffer` and `cornerEventBuffer` in `TelemetryForegroundService` with `synchronized(bufferLock)` and awaits `activeRideId != 0L` before flushing any track points or corner events.
- **Android 14/15 Compliance**:
  - Guards `BootCompletedReceiver` FGS start on API 29+/34+ with `ACCESS_BACKGROUND_LOCATION` check and `try/catch`.

#### Pillar B: Maintenance Scheduling, Odometer & Alerts
- **Monotonic Odometer Sync**:
  - When logging a **Fuel Fill-up**, **Service Record**, or **Installed Part** with `odometerKm > activeBike.totalOdometerKm`, automatically applies a monotonic odometer sync (`updateActiveBikeOdometer(odometerKm)`) so the bike's total odometer never lags behind logged maintenance or fuel events.
- **Resilient Date & Interval Evaluation (`MaintenanceEvaluator`)**:
  - Parses dates via `parseFlexibleLocalDate(dateStr, fallback)` supporting `yyyy-MM-dd`, `dd/MM/yyyy`, and `dd.MM.yyyy`.
  - When `lastLog == null` for a time-based task, anchors `lastDate` to `bike.startDateIso` (parsed safely, falling back to `currentDate`).
  - Computes `progressFraction = max(kmProgress, timeProgress).coerceIn(0f, 1f)`.
- **Deterministic Task IDs & CRUD**:
  - Standard tasks use stable IDs (`STD_COOLANT`, `STD_ENGINE_OIL`, `STD_BRAKE_FLUID`, `STD_TIRES_AXLES`, `STD_CHAIN_LUBE`, `STD_VALVE_CLEARANCE`, `STD_SPARK_PLUGS`, `STD_AIR_FILTER`).
  - Users can **Edit** and **Delete** existing scheduled tasks and service history entries directly in `MaintenanceServiceScreen`.
- **Daily Background Notifications (`MaintenanceCheckWorker`)**:
  - Evaluates both `MaintenanceTaskEntity` statuses (`OVERDUE` / `DUE_SOON`) **and** custom `ReminderEntity` items (due by odometer or date), respecting `POST_NOTIFICATIONS` permission on Android 13+.

#### Pillar C: Workshop & Repair Assistant
- **Complete Technical Specs & Torque Table**:
  - Consolidates all 11 explicit torque fields from `TechnicalSpecsDto` + `boltTorquesNm` + `torquesNm` via `TechnicalSpecsDto.toConsolidatedTorqueMap()` into a unified, searchable torque list in `BikeInfoScreen`.
  - Allows editing fluid specs, tire sizes & pressures (with **PSI ↔ Bar** unit toggle), chain slack, spark plug model/gap, and valve clearances directly in the UI without hand-writing JSON.
- **Interactive Repair Procedure Guides**:
  - Localizes all procedure guides into clean English with dynamic interpolation of the active bike's exact torque Nm and fluid capacity values from `TechnicalSpecsDto`.

#### Pillar D: Lossless Backup & Restore (Local SAF + Google Drive)
- **Backup Schema v2 (`GarageBackupPayload`)**:
  - Includes **all** garage data per motorcycle (`MotorcycleEntity` with all 15 columns, `TechnicalSpecsDto`, `MaintenanceTaskEntity`, `ServiceLogEntity` with `checklistItemsJson`, `FuelLogEntity`, `PartLogEntity`, `ReminderEntity`, `RideSessionEntity` + `CornerEventEntity` + `TelemetryTrackPointEntity`, `AppLogEntity`, and global `TelemetryPreferencesDto`).
- **Safe Restore Modes & Pre-Restore Snapshot**:
  - **Merge Mode (Default)**: Upserts bikes, tasks, service logs, fuel logs, part logs, reminders, and rides without wiping non-conflicting local bikes.
  - **Replace Mode (Clean Restore)**: Automatically writes a local rollback snapshot (`pre_restore_safety_snapshot.json` in `context.filesDir`) before clearing and restoring the database.
  - **Non-Destructive Template Application**: Applying a built-in template either updates the active bike's specs/schedule or adds a new bike to the garage—**never** deleting existing motorcycles!

#### Pillar E: Easy-to-Use Navigation & UX Polish
- **Persistent Bottom Navigation Bar** on primary screens (`Home`, `Cockpit`, `Workshop`, `Garage`, `Analytics`) paired with the existing `ModalNavigationDrawer` for secondary tools.
- **Remove Dead Code**: Deletes orphaned `MaintenanceScreen.kt` and `GarageSettingsScreen.kt`.

---

## 3. Step-by-Step LLM Implementation Plan

1. **Phase 1: Telemetry & Sensor Fusion Stability (P0 Fixes)**
   - Task 1.1: Fix IMU Timestamp Units, Corner Detection FSM, and Ride Reset (`MotorcycleSensorFusionEngine.kt`, `TelemetryForegroundService.kt`).
   - Task 1.2: Fix Service Concurrency, ForeignKey Race, Barometer & Summary Stats (`TelemetryForegroundService.kt`, `BootCompletedReceiver.kt`).
2. **Phase 2: Lossless Backup & Restore (Schema v2 + Safe Restore Modes)**
   - Task 2.1: Upgrade Backup DTOs & Room DAO Transaction Helpers (`MaintenanceDtos.kt`, `MotoGeekDao.kt`, `MotoGeekRepository.kt`).
   - Task 2.2: Implement Full Export, Merge/Replace Restore & Pre-Restore Safety Snapshot (`ImportExportManager.kt`, `GoogleDriveBackupManager.kt`).
   - Task 2.3: Fix Destructive Template Application & Upgrade Backup UI (`MainViewModel.kt`, `OfflineBackupScreen.kt`).
3. **Phase 3: Smart Maintenance Engine, Odometer Integrity & Notifications**
   - Task 3.1: Fix `MaintenanceEvaluator` Date Parsing, New-Bike Overdue Bug & Time Progress (`MaintenanceEvaluator.kt`).
   - Task 3.2: Deterministic Task IDs, English Procedure Guides & Dynamic Spec Interpolation (`MotorcycleTemplateCatalog.kt`).
   - Task 3.3: Monotonic Odometer Auto-Sync & Daily Reminder Notifications (`MainViewModel.kt`, `MaintenanceCheckWorker.kt`).
4. **Phase 4: Workshop & Repair Assistant Upgrade**
   - Task 4.1: Expose All Factory Specs, Fluid Capacities & Full Torque Table in `BikeInfoScreen` (`BikeInfoScreen.kt`).
   - Task 4.2: Upgrade `MaintenanceServiceScreen` into an Interactive Workshop & Repair Hub (`MaintenanceServiceScreen.kt`).
5. **Phase 5: Navigation, Usability & Social Card Polish**
   - Task 5.1: Unified TopBar/BottomBar Navigation, Social Card Fix & Dead File Cleanup (`MainActivity.kt`, `RideSocialCardGenerator.kt`, `TelemetryHistoryScreen.kt`).
