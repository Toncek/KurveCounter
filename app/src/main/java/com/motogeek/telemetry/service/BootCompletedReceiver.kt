package com.motogeek.telemetry.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val prefs = TelemetryPreferences(context)
            if (prefs.startOnBoot) {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                
                if (hasFine || hasCoarse) {
                    val serviceIntent = Intent(context, TelemetryForegroundService::class.java).apply {
                        action = TelemetryForegroundService.ACTION_START_MONITORING
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}
