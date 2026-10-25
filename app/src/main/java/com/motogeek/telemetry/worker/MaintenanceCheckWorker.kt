package com.motogeek.telemetry.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.motogeek.telemetry.MainActivity
import com.motogeek.telemetry.R
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.data.local.MotoGeekRepository
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import kotlinx.coroutines.flow.first

class MaintenanceCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = MotoGeekDatabase.getDatabase(context)
        val repository = MotoGeekRepository(db.motoGeekDao())
        val evaluator = MaintenanceEvaluator()

        val motorcycles = repository.getAllMotorcycles().first()

        for (bike in motorcycles) {
            val tasks = repository.getMaintenanceTasksForMotorcycle(bike.id)
            val logs = repository.getServiceLogsForMotorcycle(bike.id)
            val evaluated = evaluator.evaluate(bike, tasks, logs)

            val overdue = evaluated.filter { it.status == TaskStatus.OVERDUE }
            val dueSoon = evaluated.filter { it.status == TaskStatus.DUE_SOON }

            if (overdue.isNotEmpty() || dueSoon.isNotEmpty()) {
                sendNotification(context, bike.make + " " + bike.model, overdue.size, dueSoon.size)
            }
        }
        return Result.success()
    }

    private fun sendNotification(context: Context, bikeName: String, overdueCount: Int, dueSoonCount: Int) {
        val channelId = "maintenance_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Maintenance Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val text = buildString {
            if (overdueCount > 0) append("$overdueCount task(s) OVERDUE. ")
            if (dueSoonCount > 0) append("$dueSoonCount task(s) due soon.")
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Maintenance Alert: $bikeName")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(bikeName.hashCode(), builder.build())
    }
}
