package com.motogeek.telemetry.domain.maintenance

import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.data.local.ServiceLogEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

enum class TaskStatus { OK, DUE_SOON, OVERDUE }

data class MaintenanceTaskStatus(
    val task: MaintenanceTaskEntity,
    val status: TaskStatus,
    val remainingKm: Double?,
    val remainingDays: Long?,
    val nextDueOdometerKm: Double?,
    val lastPerformedKm: Double?,
    val progressFraction: Float
)

class MaintenanceEvaluator {

    companion object {
        private val SUPPORTED_FORMATTERS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
        )

        fun parseFlexibleLocalDate(raw: String?, fallback: LocalDate): LocalDate {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return fallback
            val datePart = trimmed.substringBefore('T').substringBefore(' ')
            for (formatter in SUPPORTED_FORMATTERS) {
                try {
                    return LocalDate.parse(datePart, formatter)
                } catch (_: Exception) {
                }
            }
            return fallback
        }
    }

    fun evaluate(
        motorcycle: MotorcycleEntity,
        tasks: List<MaintenanceTaskEntity>,
        logs: List<ServiceLogEntity>,
        currentDate: LocalDate = LocalDate.now()
    ): List<MaintenanceTaskStatus> {
        val currentOdo = motorcycle.totalOdometerKm

        return tasks.map { task ->
            val taskLogs = logs.filter { it.taskId == task.taskId }
                .sortedByDescending { it.odometerAtServiceKm }

            val lastLog = taskLogs.firstOrNull()

            var nextDueKm: Double? = null
            var remainingKm: Double? = null
            var remainingDays: Long? = null

            // Mileage based logic
            if (lastLog == null && task.firstBreakInAtKm != null) {
                nextDueKm = task.firstBreakInAtKm
            } else if (task.intervalKm != null) {
                val lastPerformedKm = lastLog?.odometerAtServiceKm ?: 0.0
                nextDueKm = lastPerformedKm + task.intervalKm
            }

            if (nextDueKm != null) {
                remainingKm = nextDueKm - currentOdo
            }

            // Time based logic
            if (task.intervalMonths != null && task.intervalMonths > 0) {
                val lastDate = lastLog?.let {
                    parseFlexibleLocalDate(it.performedAtIsoDate, currentDate)
                } ?: parseFlexibleLocalDate(motorcycle.startDateIso, currentDate)
                val nextDueDate = lastDate.plusMonths(task.intervalMonths.toLong())
                remainingDays = ChronoUnit.DAYS.between(currentDate, nextDueDate)
            }

            // Determine status
            var status = TaskStatus.OK
            val warnKm = task.warningThresholdKm ?: 500.0
            val warnDays = task.warningThresholdDays ?: 30

            if (remainingKm != null) {
                if (remainingKm <= 0) status = TaskStatus.OVERDUE
                else if (remainingKm <= warnKm && status != TaskStatus.OVERDUE) status = TaskStatus.DUE_SOON
            }

            if (remainingDays != null) {
                if (remainingDays <= 0) status = TaskStatus.OVERDUE
                else if (remainingDays <= warnDays && status != TaskStatus.OVERDUE) status = TaskStatus.DUE_SOON
            }

            // Progress fraction for UI (0.0 = just serviced, 1.0 = due)
            var kmProgress = 0f
            if (remainingKm != null && task.intervalKm != null && task.intervalKm > 0) {
                val p = 1f - (remainingKm / task.intervalKm).toFloat()
                if (p.isFinite()) kmProgress = max(0f, p)
            } else if (remainingKm != null && task.firstBreakInAtKm != null && task.firstBreakInAtKm > 0 && lastLog == null) {
                val p = 1f - (remainingKm / task.firstBreakInAtKm).toFloat()
                if (p.isFinite()) kmProgress = max(0f, p)
            }

            var timeProgress = 0f
            if (remainingDays != null && task.intervalMonths != null && task.intervalMonths > 0) {
                val totalDays = task.intervalMonths * 30.4375f
                val p = 1f - (remainingDays.toFloat() / totalDays)
                if (p.isFinite()) timeProgress = max(0f, p)
            }

            val progress = max(kmProgress, timeProgress).coerceIn(0f, 1f)

            MaintenanceTaskStatus(
                task = task,
                status = status,
                remainingKm = remainingKm,
                remainingDays = remainingDays,
                nextDueOdometerKm = nextDueKm,
                lastPerformedKm = lastLog?.odometerAtServiceKm,
                progressFraction = progress
            )
        }
    }
}
