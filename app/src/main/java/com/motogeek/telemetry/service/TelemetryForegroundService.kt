package com.motogeek.telemetry.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.motogeek.telemetry.MainActivity
import com.motogeek.telemetry.R
import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.data.local.MotoGeekRepository
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import com.motogeek.telemetry.domain.dsp.MotorcycleSensorFusionEngine
import com.motogeek.telemetry.domain.telemetry.GpxTelemetryCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class TelemetryForegroundService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: android.os.Handler

    private lateinit var repository: MotoGeekRepository
    private lateinit var fusionEngine: MotorcycleSensorFusionEngine

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var barometer: Sensor? = null

    // Tracking
    private var activeRideId: Long = 0L
    private var motorcycleId: String = ""
    private var startTimeMs: Long = 0L
    private var accumulatedDistanceKm: Double = 0.0
    private var lastLocation: Location? = null

    private var maxSpeedKmh: Double = 0.0
    private var maxLeanLeft: Double = 0.0
    private var maxLeanRight: Double = 0.0
    private var maxBrakingG: Double = 0.0
    private var maxAccelG: Double = 0.0
    private var maxLateralG: Double = 0.0

    private val bufferLock = Any()
    private val trackPointBuffer = mutableListOf<TelemetryTrackPointEntity>()
    private val cornerEventBuffer = mutableListOf<CornerEventEntity>()
    private val sessionCorners = mutableListOf<CornerEventEntity>()

    private var smoothedBaroAltitudeMeters: Double? = null
    private var smoothedGpsAltitudeMeters: Double? = null
    private var lastReferenceAltitudeMeters: Double? = null
    private var accumulatedElevationGainMeters: Double = 0.0
    private var hasBarometerSample: Boolean = false

    private var dbFlushJob: Job? = null
    private var uiUpdateJob: Job? = null

    // Caching latest IMU values for passing to FusionEngine
    private var lastAccelX = 0.0
    private var lastAccelY = 0.0
    private var lastAccelZ = 0.0
    private var lastGyroX = 0.0
    private var lastGyroY = 0.0
    private var lastGyroZ = 0.0

    private var isPaused = false
    private var timePausedMs = 0L
    private var pauseStartTimeMs = 0L

    private var isMonitoringStandby = false
    private var manualLeanOffsetDeg = 0.0
    private var lastSpeedAbove5KmhMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = MotoGeekRepository(MotoGeekDatabase.getDatabase(this).motoGeekDao())

        fusionEngine = MotorcycleSensorFusionEngine { event ->
            synchronized(bufferLock) {
                val coords = if (GpxTelemetryCodec.isValidCoordinate(event.latitude, event.longitude)) {
                    Pair(event.latitude, event.longitude)
                } else {
                    lastLocation?.let {
                        if (GpxTelemetryCodec.isValidCoordinate(it.latitude, it.longitude)) {
                            Pair(it.latitude, it.longitude)
                        } else null
                    } ?: Pair(0.0, 0.0)
                }
                val updatedEvent = event.copy(
                    rideId = activeRideId,
                    latitude = coords.first,
                    longitude = coords.second
                )
                cornerEventBuffer.add(updatedEvent)
                sessionCorners.add(updatedEvent)
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoringStandby()
            ACTION_STOP_MONITORING -> stopMonitoringStandby()
            ACTION_START_RIDE -> {
                val bikeId = intent.getStringExtra(EXTRA_MOTORCYCLE_ID) ?: ""
                startRide(bikeId)
            }
            ACTION_PAUSE_RIDE -> pauseRide()
            ACTION_RESUME_RIDE -> resumeRide()
            ACTION_STOP_RIDE -> stopRide()
            ACTION_RESET_PEAKS -> resetPeaksInternal()
            ACTION_CALIBRATE_ZERO -> calibrateZero()
            ACTION_SYNC_PREFS -> syncPrefs()
        }
        return START_NOT_STICKY
    }

    private fun syncPrefs() {
        val prefs = TelemetryPreferences(this)
        _state.update { it.copy(autoStartAbove5Kmh = prefs.autoStartAbove5Kmh, startOnBoot = prefs.startOnBoot) }
        if (!_state.value.isRiding && !isMonitoringStandby) {
            stopSelf()
        }
    }

    private fun calibrateZero() {
        fusionEngine.calibrateManualZero()
        manualLeanOffsetDeg = fusionEngine.manualLeanOffsetDeg
        TelemetryPreferences(this).manualLeanOffsetDeg = manualLeanOffsetDeg
        resetPeaksInternal()
        _state.update {
            it.copy(
                calibrationStatus = "MANUAL 0° LOCKED",
                manualLeanOffsetDeg = manualLeanOffsetDeg,
                leanAngleDeg = 0.0
            )
        }
        if (!_state.value.isRiding && !isMonitoringStandby) {
            stopSelf()
        }
    }

    private fun startMonitoringStandby() {
        if (_state.value.isRiding || isMonitoringStandby) return
        isMonitoringStandby = true
        val prefs = TelemetryPreferences(this)
        manualLeanOffsetDeg = prefs.manualLeanOffsetDeg
        fusionEngine.setManualLeanOffset(manualLeanOffsetDeg)
        _state.update { it.copy(isMonitoringStandby = true, manualLeanOffsetDeg = manualLeanOffsetDeg, autoStartAbove5Kmh = prefs.autoStartAbove5Kmh, startOnBoot = prefs.startOnBoot) }
        
        startForegroundServiceWithNotification(isStandby = true)
        acquireWakeLock()
        registerSensors()
        requestLocationUpdates()
        startUiUpdateLoop()
    }

    private fun stopMonitoringStandby() {
        if (_state.value.isRiding || !isMonitoringStandby) return
        isMonitoringStandby = false
        _state.update { it.copy(isMonitoringStandby = false) }

        sensorManager.unregisterListener(this)
        if (::sensorThread.isInitialized) {
            sensorThread.quitSafely()
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        uiUpdateJob?.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRide(bikeId: String) {
        if (_state.value.isRiding) return
        motorcycleId = bikeId
        startTimeMs = System.currentTimeMillis()
        lastSpeedAbove5KmhMs = startTimeMs
        activeRideId = 0L

        if (!isMonitoringStandby) {
            val prefs = TelemetryPreferences(this)
            manualLeanOffsetDeg = prefs.manualLeanOffsetDeg
        }
        isMonitoringStandby = false
        accumulatedDistanceKm = 0.0
        timePausedMs = 0L
        isPaused = false
        lastLocation = null

        fusionEngine.reset()
        fusionEngine.setManualLeanOffset(manualLeanOffsetDeg)

        // Reset stats
        maxSpeedKmh = 0.0
        maxLeanLeft = 0.0
        maxLeanRight = 0.0
        maxBrakingG = 0.0
        maxAccelG = 0.0
        maxLateralG = 0.0
        smoothedBaroAltitudeMeters = null
        smoothedGpsAltitudeMeters = null
        lastReferenceAltitudeMeters = null
        accumulatedElevationGainMeters = 0.0
        hasBarometerSample = false

        synchronized(bufferLock) {
            trackPointBuffer.clear()
            cornerEventBuffer.clear()
            sessionCorners.clear()
        }

        // Persist initial Ride Session
        lifecycleScope.launch(Dispatchers.IO) {
            val validBike = if (motorcycleId.isNotBlank()) repository.getMotorcycleById(motorcycleId) else null
            val resolvedBikeId = validBike?.id
                ?: repository.getActiveMotorcycle()?.id
                ?: repository.getAllMotorcycles().firstOrNull()?.firstOrNull()?.id
                ?: run {
                    val fallback = com.motogeek.telemetry.data.local.MotorcycleEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        make = "Universal",
                        model = "Motorcycle",
                        year = 2024,
                        engineDisplacementCc = 650,
                        baseOdometerKm = 0.0,
                        accumulatedTrackedKm = 0.0,
                        manualAdjustmentOffsetKm = 0.0,
                        technicalSpecsJson = "{}",
                        isActive = true
                    )
                    repository.insertMotorcycle(fallback)
                    fallback.id
                }
            motorcycleId = resolvedBikeId

            val insertedRideId = repository.insertRideSession(
                RideSessionEntity(
                    motorcycleId = resolvedBikeId,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = 0L,
                    distanceKm = 0.0,
                    avgSpeedKmh = 0.0,
                    maxSpeedKmh = 0.0,
                    maxLeanLeftDeg = 0.0,
                    maxLeanRightDeg = 0.0,
                    maxLateralG = 0.0,
                    maxBrakingG = 0.0,
                    maxAccelG = 0.0,
                    totalCornersCount = 0,
                    leftCornersCount = 0,
                    rightCornersCount = 0,
                    maxTurnInRateDegPerSec = 0.0,
                    trailBrakingScorePct = 0.0,
                    avgCornerScore = 0.0,
                    elevationGainMeters = 0.0,
                    ridingStyleBadge = "UNKNOWN"
                )
            )
            synchronized(bufferLock) {
                activeRideId = insertedRideId
                for (i in trackPointBuffer.indices) {
                    if (trackPointBuffer[i].rideId == 0L) {
                        trackPointBuffer[i] = trackPointBuffer[i].copy(rideId = insertedRideId)
                    }
                }
                for (i in cornerEventBuffer.indices) {
                    if (cornerEventBuffer[i].rideId == 0L) {
                        cornerEventBuffer[i] = cornerEventBuffer[i].copy(rideId = insertedRideId)
                    }
                }
                for (i in sessionCorners.indices) {
                    if (sessionCorners[i].rideId == 0L) {
                        sessionCorners[i] = sessionCorners[i].copy(rideId = insertedRideId)
                    }
                }
            }
        }

        _state.update { it.copy(isRiding = true, isMonitoringStandby = false, calibrationStatus = if (manualLeanOffsetDeg != 0.0) "MANUAL 0° LOCKED" else "CALIBRATING POCKET MODE") }

        startForegroundServiceWithNotification(isStandby = false)
        
        if (!::wakeLock.isInitialized || !wakeLock.isHeld) acquireWakeLock()
        if (!::sensorThread.isInitialized || !sensorThread.isAlive) registerSensors()
        if (!::locationCallback.isInitialized) requestLocationUpdates()
        
        startPeriodicFlush()
        
        if (uiUpdateJob == null || uiUpdateJob?.isActive != true) startUiUpdateLoop()
    }

    private fun startForegroundServiceWithNotification(isStandby: Boolean = false) {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            stopSelf()
            return
        }

        createNotificationChannel()
        val notification = buildNotification(isStandby)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(isStandby: Boolean = false): Notification {
        val isSk = TelemetryPreferences(this).appLanguageCode.equals("sk", ignoreCase = true)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (isSk) "MotoGeek Telemetria aktívna" else "MotoGeek Telemetry Active"
        val body = if (isStandby) {
            if (isSk) "Pohotovosť: Auto-štart > 20 km/h • Auto-stop 5m ≤ 5 km/h" else "Standby: Auto-start > 20 km/h • Auto-stop 5m ≤ 5 km/h"
        } else {
            if (isSk) "Zaznamenáva sa živá telemetria..." else "Recording live telemetry..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher) // Adjust icon as needed
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val isSk = TelemetryPreferences(this).appLanguageCode.equals("sk", ignoreCase = true)
        val channel = NotificationChannel(
            CHANNEL_ID,
            if (isSk) "Telemetrická služba" else "Telemetry Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoGeek::TelemetryWakeLock")
        wakeLock.acquire(4 * 60 * 60 * 1000L) // 4 hours max
    }

    private fun registerSensors() {
        sensorThread = HandlerThread("ImuSensorThread")
        sensorThread.start()
        sensorHandler = android.os.Handler(sensorThread.looper)

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        val samplingPeriodUs = 10_000 // 100 Hz
        accelerometer?.let { sensorManager.registerListener(this, it, samplingPeriodUs, sensorHandler) }
        gyroscope?.let { sensorManager.registerListener(this, it, samplingPeriodUs, sensorHandler) }
        barometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, sensorHandler) }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(200L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isPaused) {
                    for (location in result.locations) {
                        processLocationUpdate(location)
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun isLocationAccurateAndValid(location: Location): Boolean {
        val lat = location.latitude
        val lon = location.longitude
        if (!GpxTelemetryCodec.isValidCoordinate(lat, lon)) return false
        if (location.hasAccuracy()) {
            val acc = location.accuracy
            if (!acc.isFinite() || acc <= 0f || acc > 50f) return false
        }
        return true
    }

    private fun updateElevationClimb(altitudeMeters: Double, deadbandMeters: Double = 1.5) {
        if (!altitudeMeters.isFinite()) return
        val prevRef = lastReferenceAltitudeMeters
        if (prevRef == null || !prevRef.isFinite()) {
            lastReferenceAltitudeMeters = altitudeMeters
            return
        }
        val delta = altitudeMeters - prevRef
        if (delta.isFinite() && delta >= deadbandMeters) {
            accumulatedElevationGainMeters += delta
            lastReferenceAltitudeMeters = altitudeMeters
        } else if (delta.isFinite() && delta <= -deadbandMeters) {
            lastReferenceAltitudeMeters = altitudeMeters
        }
    }

    private fun processLocationUpdate(location: Location) {
        val isValidFix = isLocationAccurateAndValid(location)

        if (_state.value.isRiding && isValidFix) {
            val prev = lastLocation
            if (prev != null) {
                val distMeters = prev.distanceTo(location).toDouble()
                if (distMeters.isFinite() && distMeters >= 0.0 && distMeters < 5000.0) {
                    accumulatedDistanceKm += distMeters / 1000.0
                }
            }
            if (!hasBarometerSample && location.hasAltitude() && location.altitude.isFinite()) {
                val rawAlt = location.altitude
                val currentSmoothed = smoothedGpsAltitudeMeters
                val nextSmoothed = if (currentSmoothed == null) rawAlt else (0.8 * currentSmoothed + 0.2 * rawAlt)
                smoothedGpsAltitudeMeters = nextSmoothed
                updateElevationClimb(nextSmoothed, deadbandMeters = 6.0)
            }
        }

        if (isValidFix) {
            lastLocation = location
        }

        fusionEngine.processLocation(location)

        val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toDouble() else fusionEngine.currentSpeedKmh
        if (!_state.value.isRiding && TelemetryPreferences(this).autoStartAbove5Kmh && speedKmh > AUTO_START_SPEED_THRESHOLD_KMH) {
            startRide(motorcycleId)
        }

        if (_state.value.isRiding && !isPaused) {
            val nowMs = System.currentTimeMillis()
            if (speedKmh > AUTO_STOP_SPEED_THRESHOLD_KMH) {
                lastSpeedAbove5KmhMs = nowMs
            } else if (lastSpeedAbove5KmhMs > 0L && (nowMs - lastSpeedAbove5KmhMs) >= AUTO_STOP_TIMEOUT_MS) {
                stopRide(autoStopped = true)
                return
            }
        }

        if (_state.value.isRiding && isValidFix) {
            val resolvedAlt = smoothedBaroAltitudeMeters ?: smoothedGpsAltitudeMeters ?: (if (location.hasAltitude() && location.altitude.isFinite()) location.altitude else 0.0)
            val tp = TelemetryTrackPointEntity(
                rideId = activeRideId,
                timestampMs = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = fusionEngine.currentSpeedKmh.takeIf { it.isFinite() } ?: 0.0,
                leanAngleDeg = fusionEngine.currentLeanAngleDeg.takeIf { it.isFinite() } ?: 0.0,
                longitudinalG = fusionEngine.currentLongitudinalG.takeIf { it.isFinite() } ?: 0.0,
                lateralG = fusionEngine.currentLateralG.takeIf { it.isFinite() } ?: 0.0,
                altitudeMeters = resolvedAlt.takeIf { it.isFinite() } ?: 0.0
            )
            synchronized(bufferLock) {
                trackPointBuffer.add(tp)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isPaused) return
        val wallClockMs = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = event.values[0].toDouble()
                lastAccelY = event.values[1].toDouble()
                lastAccelZ = event.values[2].toDouble()

                fusionEngine.processImu(
                    timestampNanos = event.timestamp,
                    accelX = lastAccelX,
                    accelY = lastAccelY,
                    accelZ = lastAccelZ,
                    gyroX = lastGyroX,
                    gyroY = lastGyroY,
                    gyroZ = lastGyroZ,
                    wallClockMs = wallClockMs
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0].toDouble()
                lastGyroY = event.values[1].toDouble()
                lastGyroZ = event.values[2].toDouble()
            }
            Sensor.TYPE_PRESSURE -> {
                val pressureHpa = event.values[0]
                if (pressureHpa.isFinite() && pressureHpa > 100f) {
                    val rawAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa).toDouble()
                    if (rawAlt.isFinite()) {
                        hasBarometerSample = true
                        val currentSmoothed = smoothedBaroAltitudeMeters
                        val nextSmoothed = if (currentSmoothed == null) rawAlt else (0.85 * currentSmoothed + 0.15 * rawAlt)
                        smoothedBaroAltitudeMeters = nextSmoothed
                        if (_state.value.isRiding) {
                            updateElevationClimb(nextSmoothed, deadbandMeters = 1.5)
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPeriodicFlush() {
        dbFlushJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(15_000)
                flushBuffersToDatabase()
            }
        }
    }

    private fun startUiUpdateLoop() {
        uiUpdateJob = lifecycleScope.launch {
            while (true) {
                delay(500)
                if (!isPaused) {
                    updateState()
                }
            }
        }
    }

    private fun updateState() {
        if (_state.value.isRiding && !isPaused) {
            val nowMs = System.currentTimeMillis()
            if (fusionEngine.currentSpeedKmh > AUTO_STOP_SPEED_THRESHOLD_KMH) {
                lastSpeedAbove5KmhMs = nowMs
            } else if (lastSpeedAbove5KmhMs > 0L && (nowMs - lastSpeedAbove5KmhMs) >= AUTO_STOP_TIMEOUT_MS) {
                stopRide(autoStopped = true)
                return
            }
        }

        val lean = fusionEngine.currentLeanAngleDeg.coerceIn(-70.0, 70.0)
        
        if (lean > 0) maxLeanRight = max(maxLeanRight, lean)
        else maxLeanLeft = max(maxLeanLeft, abs(lean))

        maxSpeedKmh = max(maxSpeedKmh, fusionEngine.currentSpeedKmh)
        if (fusionEngine.currentLongitudinalG > 0) maxAccelG = max(maxAccelG, fusionEngine.currentLongitudinalG)
        else maxBrakingG = max(maxBrakingG, abs(fusionEngine.currentLongitudinalG))
        maxLateralG = max(maxLateralG, abs(fusionEngine.currentLateralG))

        val durationSec = (System.currentTimeMillis() - startTimeMs - timePausedMs) / 1000
        val avgSpeed = if (durationSec > 0) {
            (accumulatedDistanceKm / (durationSec / 3600.0)).takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        _state.update {
            it.copy(
                leanAngleDeg = lean,
                peakLeanLeftDeg = maxLeanLeft,
                peakLeanRightDeg = maxLeanRight,
                currentSpeedKmh = fusionEngine.currentSpeedKmh,
                averageSpeedKmh = avgSpeed,
                longitudinalG = fusionEngine.currentLongitudinalG,
                lateralG = fusionEngine.currentLateralG,
                leftCorners = fusionEngine.leftCorners,
                rightCorners = fusionEngine.rightCorners,
                currentRollRateDegPerSec = fusionEngine.currentRollRateDegPerSec,
                peakRollRateDegPerSec = fusionEngine.peakRollRateDegPerSec,
                lastCornerScore = fusionEngine.lastCornerScore,
                calibrationStatus = if (manualLeanOffsetDeg != 0.0) "MANUAL 0° LOCKED" else if (fusionEngine.calibrator.isCalibrated) "LOCKED" else "CALIBRATING POCKET MODE",
                distanceKm = accumulatedDistanceKm,
                durationSeconds = durationSec,
                isPaused = isPaused || fusionEngine.isAutoPaused
            )
        }
    }

    private suspend fun flushBuffersToDatabase() {
        val pointsToInsert: List<TelemetryTrackPointEntity>
        val eventsToInsert: List<CornerEventEntity>

        synchronized(bufferLock) {
            if (activeRideId == 0L) return
            pointsToInsert = trackPointBuffer.map {
                if (it.rideId == 0L) it.copy(rideId = activeRideId) else it
            }
            trackPointBuffer.clear()

            eventsToInsert = cornerEventBuffer.map {
                if (it.rideId == 0L) it.copy(rideId = activeRideId) else it
            }
            cornerEventBuffer.clear()
        }

        if (pointsToInsert.isNotEmpty()) {
            repository.insertTelemetryTrackPoints(pointsToInsert)
        }
        if (eventsToInsert.isNotEmpty()) {
            repository.insertCornerEvents(eventsToInsert)
        }

        // Periodically persist the active RideSessionEntity summary so forced process kills never leave ghost rides
        if (activeRideId != 0L && _state.value.isRiding) {
            val now = System.currentTimeMillis()
            val durationSec = (now - startTimeMs - timePausedMs) / 1000
            val avgSpeed = if (durationSec > 0) {
                (accumulatedDistanceKm / (durationSec / 3600.0)).takeIf { it.isFinite() } ?: 0.0
            } else 0.0

            val recordedCorners = synchronized(bufferLock) { sessionCorners.toList() }
            val maxTurnInRate = recordedCorners.maxOfOrNull { it.maxRollRateDegPerSec }?.takeIf { it.isFinite() } ?: 0.0
            val avgScore = if (recordedCorners.isNotEmpty()) {
                recordedCorners.map { it.cornerScore }.average().takeIf { it.isFinite() } ?: 0.0
            } else 0.0
            val trailBrakingPct = if (recordedCorners.isNotEmpty()) {
                val countWithTrailBrake = recordedCorners.count { it.peakBrakingG <= -0.12 }
                ((countWithTrailBrake.toDouble() / recordedCorners.size) * 100.0).takeIf { it.isFinite() } ?: 0.0
            } else 0.0
            val finiteElevationGain = accumulatedElevationGainMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

            val badge = fusionEngine.classifyRidingStyle(
                avgG = ((maxBrakingG + maxAccelG + maxLateralG) / 3.0).takeIf { it.isFinite() } ?: 0.0,
                maxLean = max(maxLeanLeft, maxLeanRight)
            )

            repository.insertRideSession(
                RideSessionEntity(
                    rideId = activeRideId,
                    motorcycleId = motorcycleId,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = now,
                    distanceKm = accumulatedDistanceKm.takeIf { it.isFinite() } ?: 0.0,
                    avgSpeedKmh = avgSpeed.takeIf { it.isFinite() } ?: 0.0,
                    maxSpeedKmh = maxSpeedKmh.takeIf { it.isFinite() } ?: 0.0,
                    maxLeanLeftDeg = maxLeanLeft.takeIf { it.isFinite() } ?: 0.0,
                    maxLeanRightDeg = maxLeanRight.takeIf { it.isFinite() } ?: 0.0,
                    maxLateralG = maxLateralG.takeIf { it.isFinite() } ?: 0.0,
                    maxBrakingG = maxBrakingG.takeIf { it.isFinite() } ?: 0.0,
                    maxAccelG = maxAccelG.takeIf { it.isFinite() } ?: 0.0,
                    totalCornersCount = fusionEngine.totalCorners,
                    leftCornersCount = fusionEngine.leftCorners,
                    rightCornersCount = fusionEngine.rightCorners,
                    maxTurnInRateDegPerSec = maxTurnInRate,
                    trailBrakingScorePct = trailBrakingPct,
                    avgCornerScore = avgScore,
                    elevationGainMeters = finiteElevationGain,
                    ridingStyleBadge = badge
                )
            )
        }
    }

    private fun pauseRide() {
        if (!isPaused) {
            isPaused = true
            pauseStartTimeMs = System.currentTimeMillis()
            _state.update { it.copy(isPaused = true) }
        }
    }

    private fun resumeRide() {
        if (isPaused) {
            isPaused = false
            timePausedMs += System.currentTimeMillis() - pauseStartTimeMs
            lastSpeedAbove5KmhMs = System.currentTimeMillis()
            lastLocation = null // prevent bad distance jump
            _state.update { it.copy(isPaused = false) }
        }
    }

    private fun resetPeaksInternal() {
        maxLeanLeft = 0.0
        maxLeanRight = 0.0
        maxSpeedKmh = 0.0
        maxAccelG = 0.0
        maxBrakingG = 0.0
        maxLateralG = 0.0
        _state.update {
            it.copy(
                peakLeanLeftDeg = 0.0,
                peakLeanRightDeg = 0.0,
                peakRollRateDegPerSec = 0.0,
                currentRollRateDegPerSec = 0.0,
                lastCornerScore = 0.0
            )
        }
        if (!_state.value.isRiding && !isMonitoringStandby) {
            stopSelf()
        }
    }

    private fun stopRide(autoStopped: Boolean = false) {
        if (!_state.value.isRiding) return

        if (isPaused) {
             timePausedMs += System.currentTimeMillis() - pauseStartTimeMs
        }

        sensorManager.unregisterListener(this)
        if (::sensorThread.isInitialized) {
            sensorThread.quitSafely()
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        dbFlushJob?.cancel()
        uiUpdateJob?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            flushBuffersToDatabase()

            // Finalize Ride Session
            val durationSec = (System.currentTimeMillis() - startTimeMs - timePausedMs) / 1000
            val avgSpeed = if (durationSec > 0) {
                (accumulatedDistanceKm / (durationSec / 3600.0)).takeIf { it.isFinite() } ?: 0.0
            } else 0.0

            val recordedCorners = synchronized(bufferLock) { sessionCorners.toList() }
            val maxTurnInRate = recordedCorners.maxOfOrNull { it.maxRollRateDegPerSec }?.takeIf { it.isFinite() } ?: 0.0
            val avgScore = if (recordedCorners.isNotEmpty()) {
                recordedCorners.map { it.cornerScore }.average().takeIf { it.isFinite() } ?: 0.0
            } else 0.0
            val trailBrakingPct = if (recordedCorners.isNotEmpty()) {
                val countWithTrailBrake = recordedCorners.count { it.peakBrakingG <= -0.12 }
                ((countWithTrailBrake.toDouble() / recordedCorners.size) * 100.0).takeIf { it.isFinite() } ?: 0.0
            } else 0.0
            val finalElevationGain = accumulatedElevationGainMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

            val badge = fusionEngine.classifyRidingStyle(
                avgG = (maxBrakingG + maxAccelG + maxLateralG) / 3.0,
                maxLean = max(maxLeanLeft, maxLeanRight)
            )

            repository.insertRideSession(
                RideSessionEntity(
                    rideId = activeRideId,
                    motorcycleId = motorcycleId,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = System.currentTimeMillis(),
                    distanceKm = accumulatedDistanceKm.takeIf { it.isFinite() } ?: 0.0,
                    avgSpeedKmh = avgSpeed.takeIf { it.isFinite() } ?: 0.0,
                    maxSpeedKmh = maxSpeedKmh.takeIf { it.isFinite() } ?: 0.0,
                    maxLeanLeftDeg = maxLeanLeft.takeIf { it.isFinite() } ?: 0.0,
                    maxLeanRightDeg = maxLeanRight.takeIf { it.isFinite() } ?: 0.0,
                    maxLateralG = maxLateralG.takeIf { it.isFinite() } ?: 0.0,
                    maxBrakingG = maxBrakingG.takeIf { it.isFinite() } ?: 0.0,
                    maxAccelG = maxAccelG.takeIf { it.isFinite() } ?: 0.0,
                    totalCornersCount = fusionEngine.totalCorners,
                    leftCornersCount = fusionEngine.leftCorners,
                    rightCornersCount = fusionEngine.rightCorners,
                    maxTurnInRateDegPerSec = maxTurnInRate,
                    trailBrakingScorePct = trailBrakingPct,
                    avgCornerScore = avgScore,
                    elevationGainMeters = finalElevationGain,
                    ridingStyleBadge = badge
                )
            )

            // Add distance to Motorcycle
            if (accumulatedDistanceKm > 0.0) {
                repository.addAccumulatedTrackedKm(motorcycleId, accumulatedDistanceKm)
            }

            val isSk = TelemetryPreferences(this@TelemetryForegroundService).appLanguageCode.equals("sk", ignoreCase = true)
            val distFormatted = String.format("%.1f", accumulatedDistanceKm)
            val rideTitle = if (autoStopped) {
                if (isSk) {
                    "Jazda automaticky ukončená ($distFormatted km • 5 min ≤ 5 km/h)"
                } else {
                    "Ride Auto-Stopped ($distFormatted km • 5 min ≤ 5 km/h)"
                }
            } else {
                if (isSk) "Jazda zaznamenaná ($distFormatted km)" else "Ride Recorded ($distFormatted km)"
            }
            repository.logActivity(
                motorcycleId = motorcycleId,
                category = "RIDE",
                title = rideTitle,
                details = if (isSk) {
                    "Max náklon: ${maxLeanLeft.toInt()}°Ľ / ${maxLeanRight.toInt()}°P • ${fusionEngine.totalCorners} zákrut • Štýl: $badge"
                } else {
                    "Peak Lean: ${maxLeanLeft.toInt()}°L / ${maxLeanRight.toInt()}°R • ${fusionEngine.totalCorners} corners • Style: $badge"
                }
            )

            _state.update { it.copy(isRiding = false) }

            if (::wakeLock.isInitialized && wakeLock.isHeld) {
                wakeLock.release()
            }

            if (autoStopped && TelemetryPreferences(this@TelemetryForegroundService).autoStartAbove5Kmh) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    startMonitoringStandby()
                }
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    companion object {
        fun resetPeaks() {
            _state.update { it.copy(peakLeanLeftDeg = 0.0, peakLeanRightDeg = 0.0, peakRollRateDegPerSec = 0.0) }
        }

        const val ACTION_RESET_PEAKS = "ACTION_RESET_PEAKS"
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "ACTION_STOP_MONITORING"
        const val ACTION_CALIBRATE_ZERO = "ACTION_CALIBRATE_ZERO"
        const val ACTION_SYNC_PREFS = "ACTION_SYNC_PREFS"
        const val ACTION_START_RIDE = "ACTION_START_RIDE"
        const val ACTION_PAUSE_RIDE = "ACTION_PAUSE_RIDE"
        const val ACTION_RESUME_RIDE = "ACTION_RESUME_RIDE"
        const val ACTION_STOP_RIDE = "ACTION_STOP_RIDE"
        const val EXTRA_MOTORCYCLE_ID = "EXTRA_MOTORCYCLE_ID"

        const val AUTO_START_SPEED_THRESHOLD_KMH = 20.0
        const val AUTO_STOP_SPEED_THRESHOLD_KMH = 5.0
        const val AUTO_STOP_TIMEOUT_MS = 5 * 60 * 1000L

        private const val CHANNEL_ID = "telemetry_channel"
        private const val NOTIF_ID = 1001

        private val _state = MutableStateFlow(LiveTelemetryState())
        val state: StateFlow<LiveTelemetryState> = _state.asStateFlow()
    }
}
