package com.motogeek.telemetry.service

import android.content.Context
import android.content.SharedPreferences

class TelemetryPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("motogeek_telemetry_prefs", Context.MODE_PRIVATE)

    var autoStartAbove5Kmh: Boolean
        get() = prefs.getBoolean("autoStartAbove5Kmh", true)
        set(value) = prefs.edit().putBoolean("autoStartAbove5Kmh", value).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean("startOnBoot", false)
        set(value) = prefs.edit().putBoolean("startOnBoot", value).apply()

    var manualLeanOffsetDeg: Double
        get() = Double.fromBits(prefs.getLong("manualLeanOffsetDeg", java.lang.Double.doubleToRawLongBits(0.0)))
        set(value) = prefs.edit().putLong("manualLeanOffsetDeg", java.lang.Double.doubleToRawLongBits(value)).apply()

    var appLanguageCode: String
        get() = prefs.getString("appLanguageCode", "en") ?: "en"
        set(value) = prefs.edit().putString("appLanguageCode", value).apply()
}
