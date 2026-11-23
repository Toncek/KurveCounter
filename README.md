# KurveCounter (MotoGeekTelemetry)

Native Android motorcycle telemetry and garage maintenance companion built with Kotlin and Jetpack Compose.

## Project Overview

KurveCounter (`com.motogeek.telemetry`) combines real-time riding telemetry with a full workshop and garage management hub:

### Telemetry & Ride Analytics
- **100Hz IMU + GPS Sensor Fusion**: Samples accelerometer, gyroscope, and barometer sensors on a dedicated `HandlerThread` (`TelemetryForegroundService`) with a 2nd-order Butterworth low-pass filter (`BiquadButterworthFilter`) and automatic pocket-mode orientation calibration (`PocketModeCalibrator`).
- **Live Ride Metrics**: Tracks lean angle (left/right peaks), longitudinal & lateral G-forces, roll/turn-in rate (`°/s`), corner detection & scoring, speed, and altitude/elevation gain.
- **Cockpit Layouts**: Portrait and landscape cockpit modes (`APEX-R`, `CIRCUIT`, `LAB`) with manual `0°` lean calibration, peak reset, and optional auto-start (`> 5 km/h`) / boot monitoring guards for Android 10–15.
- **Ride History & GPX 1.1**: Interactive trajectory heatmap (`RideTrajectoryHeatmapCanvas`) with pan/zoom, lean-colored path segments, apex corner pins, synchronized telemetry scrubbing (`SynchronizedTelemetryChart`), social ride card PNG export (`RideSocialCardGenerator`), CSV export, and full **GPX 1.1 export/import** with `<trkpt>` telemetry extensions and `<wpt>` corner apex markers.

### Garage, Workshop & Maintenance Hub
- **Multi-Bike Garage & Monotonic Odometer**: Manage multiple motorcycles with GPS-tracked distance, manual dashboard odometer calibration, and pre-populated factory templates (`MT-09`, `CB650R`, `R1250GS`, `KTM 890 Duke`, `Z900`, `Universal`).
- **Maintenance Evaluation & Service Checklist**: Evaluates distance and time intervals (`OK`, `DUE_SOON`, `OVERDUE`, plus break-in intervals), interactive `+ LOG SERVICE` multi-task checklist, repair guides, overdue auto-clear, and automated canonical task deduplication (`ServiceTaskDeduplicator`).
- **Total Cost of Ownership (TCO) & Fuel Economy**: Tracks fuel fill-ups (with partial/full-tank segment calculation for `L/100km` and `km/L`), replacement parts, custom reminders, and ownership cost per kilometer.
- **Bolt Torques & Technical Specs**: Searchable tightening torque reference (11 standard fields + custom maps), fluid capacities, valve clearances, spark plug gaps, and tire pressures with `PSI` / `BAR` toggle.
- **AI Service Manual JSON Import & Backups**: Resilient non-destructive guided import (`GuidedImportPlanner`) for KurveCounter backups, `mybikes-export-*.json`, and LLM-extracted service manual schedules with `Merge` / `Replace` modes and automatic pre-restore safety snapshots.
- **Google Drive Sync & Offline Exports**: Cloud sync/backup via Google Drive `appDataFolder` (with Storage Access Framework fallback), HTML Digital Service Book export, and periodic background maintenance checks.

---

## Tech Stack & Architecture

- **Language**: Kotlin `1.9.24` (Kotlin `1.9+`, Coroutines, `StateFlow`)
- **UI**: Jetpack Compose Material 3 (`composeBom 2024.05.00`) with the **ApexCarbon TFT** dark theme and custom `Canvas` gauges/charts
- **Architecture**: MVVM (`MainViewModel` + `MotoGeekRepository` + Room DAO)
- **Local Persistence**: Room `2.6.1` (schema **v3**, KSP compiler, migrations `1 -> 2 -> 3`)
- **Background Work**:
  - `TelemetryForegroundService` (`LifecycleService`, `foregroundServiceType="location"`)
  - WorkManager `2.9.0` (`MaintenanceCheckWorker` `CoroutineWorker`)
- **Serialization**: `kotlinx.serialization.json` (`1.6.3`) configured with `ignoreUnknownKeys = true`, `isLenient = true`, and `coerceInputValues = true`
- **SDK Targets**: `minSdk = 29` (Android 10), `compileSdk = 35`, `targetSdk = 35` (Android 15)

---

## Build & Release Prerequisites

### Java / JDK
- **JDK 17 or JDK 21** is required (`compileOptions` and `kotlinOptions` target JVM `17`).
- On Linux systems with OpenJDK 21 installed:
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ```

### Gradle Commands
Run from the repository root (`/home/toncek/work/projects/KurveCounter` or your clone path):

```bash
# Run all JVM unit tests
./gradlew testDebugUnitTest

# Run Android Lint checks on debug build
./gradlew lintDebug

# Build debug APK (signed with embedded debug.keystore)
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Release Signing Environment Variables
`app/build.gradle.kts` reads the release signing configuration from the following environment variables. If any variable is missing or `KEYSTORE_FILE` does not exist on disk, `assembleRelease` falls back to the debug signing config:

| Variable | Description |
| --- | --- |
| `KEYSTORE_FILE` | Path to the `.jks` / `.keystore` release keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias inside the keystore |
| `KEY_PASSWORD` | Key password for `KEY_ALIAS` |

---

## Localization

KurveCounter provides full bilingual **English (`en`)** and **Slovak (`sk`)** support:
- **In-App Language Toggle**: Switch between `EN` and `SK` at runtime via `MainViewModel.toggleAppLanguage()`, persisted in `TelemetryPreferences`.
- **UI & Domain Coverage**: All Jetpack Compose screens, components, torque names (`localizeTorqueName`), HTML service books, CSV exports, social cards (`RideSocialCardGenerator`), activity logs, foreground service notifications, and `MaintenanceCheckWorker` notifications adapt to the active language.
- **Android 13+ Per-App Language Resources**: Configured via [`app/src/main/res/xml/locales_config.xml`](app/src/main/res/xml/locales_config.xml), `android:localeConfig="@xml/locales_config"` in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml), and [`app/src/main/res/values-sk/strings.xml`](app/src/main/res/values-sk/strings.xml).
