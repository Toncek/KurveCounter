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

    private val trackPointBuffer = mutableListOf<TelemetryTrackPointEntity>()
    private val cornerEventBuffer = mutableListOf<CornerEventEntity>()

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

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = MotoGeekRepository(MotoGeekDatabase.getDatabase(this).motoGeekDao())

        fusionEngine = MotorcycleSensorFusionEngine { event ->
            val updatedEvent = event.copy(rideId = activeRideId)
            cornerEventBuffer.add(updatedEvent)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_RIDE -> {
                val bikeId = intent.getStringExtra(EXTRA_MOTORCYCLE_ID) ?: ""
                startRide(bikeId)
            }
            ACTION_PAUSE_RIDE -> pauseRide()
            ACTION_RESUME_RIDE -> resumeRide()
            ACTION_STOP_RIDE -> stopRide()
            ACTION_RESET_PEAKS -> resetPeaksInternal()
        }
        return START_NOT_STICKY
    }

    private fun startRide(bikeId: String) {
        if (_state.value.isRiding) return
        motorcycleId = bikeId
        startTimeMs = System.currentTimeMillis()
        accumulatedDistanceKm = 0.0
        timePausedMs = 0L
        isPaused = false

        // Reset stats
        maxSpeedKmh = 0.0
        maxLeanLeft = 0.0
        maxLeanRight = 0.0
        maxBrakingG = 0.0
        maxAccelG = 0.0
        maxLateralG = 0.0
        trackPointBuffer.clear()
        cornerEventBuffer.clear()

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

            activeRideId = repository.insertRideSession(
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
        }

        _state.update { it.copy(isRiding = true, calibrationStatus = "CALIBRATING POCKET MODE") }

        startForegroundServiceWithNotification()
        acquireWakeLock()
        registerSensors()
        requestLocationUpdates()
        startPeriodicFlush()
        startUiUpdateLoop()
    }

    private fun startForegroundServiceWithNotification() {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            stopSelf()
            return
        }

        val notification = buildNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MotoGeek Telemetry Active")
            .setContentText("Recording live telemetry...")
            .setSmallIcon(R.mipmap.ic_launcher) // Adjust icon as needed
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Telemetry Service",
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
        barometer?.let { sensorManager.registerListener(this, it, samplingPeriodUs, sensorHandler) }
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

    private fun processLocationUpdate(location: Location) {
        if (lastLocation != null) {
            accumulatedDistanceKm += lastLocation!!.distanceTo(location) / 1000.0
        }
        lastLocation = location
        fusionEngine.processLocation(location)

        val tp = TelemetryTrackPointEntity(
            rideId = activeRideId,
            timestampMs = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = fusionEngine.currentSpeedKmh,
            leanAngleDeg = fusionEngine.currentLeanAngleDeg,
            longitudinalG = fusionEngine.currentLongitudinalG,
            lateralG = fusionEngine.currentLateralG,
            altitudeMeters = location.altitude
        )
        trackPointBuffer.add(tp)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isPaused) return
        val ts = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = event.values[0].toDouble()
                lastAccelY = event.values[1].toDouble()
                lastAccelZ = event.values[2].toDouble()

                fusionEngine.processImu(
                    ts,
                    lastAccelX, lastAccelY, lastAccelZ,
                    lastGyroX, lastGyroY, lastGyroZ
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0].toDouble()
                lastGyroY = event.values[1].toDouble()
                lastGyroZ = event.values[2].toDouble()
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
        val lean = fusionEngine.currentLeanAngleDeg
        if (lean > 0) maxLeanRight = max(maxLeanRight, lean)
        else maxLeanLeft = max(maxLeanLeft, abs(lean))

        maxSpeedKmh = max(maxSpeedKmh, fusionEngine.currentSpeedKmh)
        if (fusionEngine.currentLongitudinalG > 0) maxAccelG = max(maxAccelG, fusionEngine.currentLongitudinalG)
        else maxBrakingG = max(maxBrakingG, abs(fusionEngine.currentLongitudinalG))
        maxLateralG = max(maxLateralG, abs(fusionEngine.currentLateralG))

        val durationSec = (System.currentTimeMillis() - startTimeMs - timePausedMs) / 1000
        val avgSpeed = if (durationSec > 0) accumulatedDistanceKm / (durationSec / 3600.0) else 0.0

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
                calibrationStatus = if (fusionEngine.calibrator.isCalibrated) "LOCKED" else "CALIBRATING POCKET MODE",
                distanceKm = accumulatedDistanceKm,
                durationSeconds = durationSec,
                isPaused = isPaused || fusionEngine.isAutoPaused
            )
        }
    }

    private suspend fun flushBuffersToDatabase() {
        val pointsToInsert = trackPointBuffer.toList()
        trackPointBuffer.clear()
        if (pointsToInsert.isNotEmpty()) {
            repository.insertTelemetryTrackPoints(pointsToInsert)
        }

        val eventsToInsert = cornerEventBuffer.toList()
        cornerEventBuffer.clear()
        if (eventsToInsert.isNotEmpty()) {
            repository.insertCornerEvents(eventsToInsert)
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
        _state.update { it.copy(peakLeanLeftDeg = 0.0, peakLeanRightDeg = 0.0) }
    }

    private fun stopRide() {
        if (!_state.value.isRiding) return

        if (isPaused) {
             timePausedMs += System.currentTimeMillis() - pauseStartTimeMs
        }

        sensorManager.unregisterListener(this)
        sensorThread.quitSafely()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        dbFlushJob?.cancel()
        uiUpdateJob?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            flushBuffersToDatabase()

            // Finalize Ride Session
            val durationSec = (System.currentTimeMillis() - startTimeMs - timePausedMs) / 1000
            val avgSpeed = if (durationSec > 0) accumulatedDistanceKm / (durationSec / 3600.0) else 0.0

            val badge = fusionEngine.classifyRidingStyle(avgG = (maxBrakingG + maxAccelG + maxLateralG) / 3, maxLean = max(maxLeanLeft, maxLeanRight))

            repository.insertRideSession(
                RideSessionEntity(
                    rideId = activeRideId,
                    motorcycleId = motorcycleId,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = System.currentTimeMillis(),
                    distanceKm = accumulatedDistanceKm,
                    avgSpeedKmh = avgSpeed,
                    maxSpeedKmh = maxSpeedKmh,
                    maxLeanLeftDeg = maxLeanLeft,
                    maxLeanRightDeg = maxLeanRight,
                    maxLateralG = maxLateralG,
                    maxBrakingG = maxBrakingG,
                    maxAccelG = maxAccelG,
                    totalCornersCount = fusionEngine.totalCorners,
                    leftCornersCount = fusionEngine.leftCorners,
                    rightCornersCount = fusionEngine.rightCorners,
                    maxTurnInRateDegPerSec = 0.0, // simplified
                    trailBrakingScorePct = 0.0, // simplified
                    avgCornerScore = 0.0, // simplified
                    elevationGainMeters = 0.0, // simplified
                    ridingStyleBadge = badge
                )
            )

            // Add distance to Motorcycle
            repository.addAccumulatedTrackedKm(motorcycleId, accumulatedDistanceKm)

            _state.update { it.copy(isRiding = false) }

            if (::wakeLock.isInitialized && wakeLock.isHeld) {
                wakeLock.release()
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        fun resetPeaks() {
            _state.update { it.copy(peakLeanLeftDeg = 0.0, peakLeanRightDeg = 0.0) }
            // Note: In a real app we might also need to reset maxLeanLeft and maxLeanRight variables within the service instance
            // but since they are private, updating the state will just be overwritten on next update if we don\'t reset them.
            // We\'d need an intent or a better way to communicate with the service instance to do it properly.
        }

        const val ACTION_RESET_PEAKS = "ACTION_RESET_PEAKS"
        const val ACTION_START_RIDE = "ACTION_START_RIDE"
        const val ACTION_PAUSE_RIDE = "ACTION_PAUSE_RIDE"
        const val ACTION_RESUME_RIDE = "ACTION_RESUME_RIDE"
        const val ACTION_STOP_RIDE = "ACTION_STOP_RIDE"
        const val EXTRA_MOTORCYCLE_ID = "EXTRA_MOTORCYCLE_ID"

        private const val CHANNEL_ID = "telemetry_channel"
        private const val NOTIF_ID = 1001

        private val _state = MutableStateFlow(LiveTelemetryState())
        val state: StateFlow<LiveTelemetryState> = _state.asStateFlow()
    }
}
