# AGENTS.md — MotoGeekTelemetry (`KurveCounter`)

## 0. Low-Credit & Jules-First Mandate (Codex / Claude / Antigravity)
- **Local Agent (Codex / Claude Code / Antigravity)**: Act strictly as low-credit planner/verifier.
  1. **Delegate 100% of Kotlin/Compose/test work to Google Jules** (free cloud VM) in 1 command: `python3 "/home/toncek/work projects/.agents/skills/jules-delegate/scripts/julesctl.py" batch` (or `quick-dispatch`). **Zero user approval needed** for dispatch, `watch`, `approve-plan`, `reply`, or `pull`.
  2. **Save Credits**: Never spawn local `Task`/`Explore` subagents. Never read whole files (`rg -n` + `sed -n` ≤ 60 lines max). Chain shell commands with `&&` in 1 tool call. Keep chat replies ≤ 5 terse bullets (no code dumps).
- **Remote Jules Worker (or Explicit Direct Mode)**: Implement within assigned `scope` and verify with `./gradlew testDebugUnitTest`.

## 1. Tech Stack & Architecture
- **Stack**: Kotlin 1.9+ (`StateFlow`, Coroutines), Jetpack Compose (Material3, custom `Canvas` gauges), MVVM + Room Database (KSP, `@Transaction`, migrations), Android 14/15 `LifecycleService` (`FOREGROUND_SERVICE_TYPE_LOCATION`) + WorkManager (`CoroutineWorker`), `kotlinx.serialization.json` (`ignoreUnknownKeys = true`, `isLenient = true`, `coerceInputValues = true`).

## 2. Core Engineering Rules
1. **Android 14/15 (API 34/35) Compliance**: Check runtime location permissions (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`) before starting `TelemetryForegroundService`. Run 100Hz sensor listeners (`TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`, `TYPE_PRESSURE`) on a dedicated `HandlerThread` with safe `PARTIAL_WAKE_LOCK` handling.
2. **Math & Canvas Safety**: Guard all divisions, `sqrt`, and trig in `MotorcycleSensorFusionEngine` / `BiquadButterworthFilter` and `Canvas` drawing against `NaN`/`Infinity` (`result.takeIf { it.isFinite() } ?: 0.0`) and `coerceIn(...)`.
3. **Odometer & AI Maintenance**: Preserve monotonic odometer updates + manual adjustments, categorize service intervals (`OK`, `DUE_SOON`, `OVERDUE`), and parse LLM-imported service manual JSONs (torques in Nm, fluid capacities) resiliently.
4. **Testing**: Verify sensor math, filters, repositories, and JSON parsers via `./gradlew testDebugUnitTest`.
