package com.motogeek.telemetry.domain.onboarding

import com.motogeek.telemetry.data.local.ServiceLogEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToLong

object ServiceBookBaselineCalculator {

    fun baselineLogId(motorcycleId: String, taskId: String): String =
        "baseline_${motorcycleId}_${taskId}"

    fun buildExpectedHistory(
        condition: BikePurchaseCondition,
        currentOdometerKm: Double,
        bikeYear: Int?,
        purchaseDateIso: String,
        tasks: List<MaintenanceTaskDto>,
        today: LocalDate = LocalDate.now()
    ): List<MaintenanceHistoryReviewItem> {
        val safeOdo = if (currentOdometerKm.isFinite() && currentOdometerKm >= 0.0) {
            currentOdometerKm
        } else {
            0.0
        }
        val safePurchaseDate = MaintenanceEvaluator.parseFlexibleLocalDate(purchaseDateIso, today)
            .let { if (it.isAfter(today)) today else it }
        val validYear = bikeYear?.takeIf { it in 1900..(today.year + 1) }

        val ageStartDate = when (condition) {
            BikePurchaseCondition.NEW -> safePurchaseDate
            BikePurchaseCondition.USED -> {
                if (validYear != null) {
                    val jan1 = LocalDate.of(validYear, 1, 1)
                    if (jan1.isBefore(today)) jan1 else today
                } else {
                    safePurchaseDate
                }
            }
        }

        return tasks.map { task ->
            calculateItem(
                condition = condition,
                safeOdo = safeOdo,
                ageStartDate = ageStartDate,
                today = today,
                task = task
            )
        }
    }

    private fun calculateItem(
        condition: BikePurchaseCondition,
        safeOdo: Double,
        ageStartDate: LocalDate,
        today: LocalDate,
        task: MaintenanceTaskDto
    ): MaintenanceHistoryReviewItem {
        val breakInKm = task.firstBreakInAtKm?.takeIf { it.isFinite() && it > 0.0 }
        val intervalKm = task.intervalKm?.takeIf { it.isFinite() && it > 0.0 }
        val intervalMonths = task.intervalMonths?.takeIf { it > 0 }

        val monthsElapsed = ChronoUnit.MONTHS.between(ageStartDate, today).coerceAtLeast(0L)
        val completedTimeIntervals = if (intervalMonths != null) {
            monthsElapsed / intervalMonths.toLong()
        } else {
            0L
        }
        val timeMilestoneDate = if (intervalMonths != null && completedTimeIntervals > 0L) {
            ageStartDate.plusMonths(completedTimeIntervals * intervalMonths.toLong())
                .let { if (it.isAfter(today)) today else it }
        } else {
            null
        }

        val periodicKm = if (intervalKm != null && safeOdo > 0.0) {
            val raw = floor(safeOdo / intervalKm) * intervalKm
            if (raw.isFinite()) raw.coerceIn(0.0, safeOdo) else 0.0
        } else {
            0.0
        }
        val kmMilestoneRespectingBreakIn = if (breakInKm != null && safeOdo >= breakInKm && periodicKm < breakInKm) {
            breakInKm.coerceIn(0.0, safeOdo)
        } else {
            periodicKm
        }

        var wasNeverDone = true
        var lastKm: Double? = null
        var lastDate: LocalDate? = null

        when (condition) {
            BikePurchaseCondition.NEW -> {
                if (breakInKm != null && safeOdo < breakInKm) {
                    wasNeverDone = true
                } else if (safeOdo > 0.0 && kmMilestoneRespectingBreakIn > 0.0) {
                    wasNeverDone = false
                    lastKm = kmMilestoneRespectingBreakIn
                    lastDate = interpolateDate(ageStartDate, today, kmMilestoneRespectingBreakIn, safeOdo)
                } else if (timeMilestoneDate != null) {
                    wasNeverDone = false
                    lastDate = timeMilestoneDate
                    lastKm = estimateKmAtDate(ageStartDate, timeMilestoneDate, today, safeOdo)
                }
            }

            BikePurchaseCondition.USED -> {
                when {
                    breakInKm != null && safeOdo < breakInKm && timeMilestoneDate == null -> {
                        wasNeverDone = true
                    }
                    intervalKm != null && periodicKm > 0.0 -> {
                        wasNeverDone = false
                        lastKm = kmMilestoneRespectingBreakIn
                        val propDate = interpolateDate(ageStartDate, today, kmMilestoneRespectingBreakIn, safeOdo)
                        lastDate = if (timeMilestoneDate != null && timeMilestoneDate.isAfter(propDate)) {
                            timeMilestoneDate
                        } else {
                            propDate
                        }
                    }
                    timeMilestoneDate != null -> {
                        wasNeverDone = false
                        val estKm = estimateKmAtDate(ageStartDate, timeMilestoneDate, today, safeOdo)
                        lastKm = if (breakInKm != null && safeOdo >= breakInKm) {
                            maxOf(estKm, breakInKm).coerceIn(0.0, safeOdo)
                        } else {
                            estKm
                        }
                        lastDate = timeMilestoneDate
                    }
                    kmMilestoneRespectingBreakIn > 0.0 -> {
                        wasNeverDone = false
                        lastKm = kmMilestoneRespectingBreakIn
                        lastDate = interpolateDate(ageStartDate, today, kmMilestoneRespectingBreakIn, safeOdo)
                    }
                }
            }
        }

        return MaintenanceHistoryReviewItem(
            taskId = task.taskId,
            category = task.category,
            taskName = task.taskName,
            actionType = task.actionType,
            firstBreakInAtKm = breakInKm,
            intervalKm = intervalKm,
            intervalMonths = intervalMonths,
            warningThresholdKm = task.warningThresholdKm?.takeIf { it.isFinite() && it >= 0.0 },
            warningThresholdDays = task.warningThresholdDays?.takeIf { it >= 0 },
            oemPartNumbers = task.oemPartNumbers,
            notes = task.notes,
            lastPerformedKmText = if (!wasNeverDone) formatCleanNumber(lastKm) else "",
            lastPerformedDateIso = if (!wasNeverDone) lastDate?.toString().orEmpty() else "",
            wasNeverDone = wasNeverDone,
            isUserModified = false
        )
    }

    private fun interpolateDate(
        startDate: LocalDate,
        today: LocalDate,
        milestoneKm: Double,
        safeOdo: Double
    ): LocalDate {
        val totalDays = ChronoUnit.DAYS.between(startDate, today)
        if (totalDays <= 0L || safeOdo <= 0.0 || !milestoneKm.isFinite()) return today
        val ratio = (milestoneKm / safeOdo).coerceIn(0.0, 1.0)
        val offsetDays = (totalDays.toDouble() * ratio).roundToLong().coerceIn(0L, totalDays)
        return startDate.plusDays(offsetDays)
    }

    private fun estimateKmAtDate(
        startDate: LocalDate,
        targetDate: LocalDate,
        today: LocalDate,
        safeOdo: Double
    ): Double {
        if (safeOdo <= 0.0) return 0.0
        val totalDays = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(1L)
        val targetDays = ChronoUnit.DAYS.between(startDate, targetDate).coerceIn(0L, totalDays)
        val ratio = targetDays.toDouble() / totalDays.toDouble()
        val est = safeOdo * ratio
        return if (est.isFinite()) est.roundToLong().toDouble().coerceIn(0.0, safeOdo) else 0.0
    }

    fun buildBaselineServiceLog(
        motorcycleId: String,
        item: MaintenanceHistoryReviewItem,
        condition: BikePurchaseCondition,
        defaultDateIso: String
    ): ServiceLogEntity? {
        if (item.wasNeverDone) return null

        val parsedKm = item.lastPerformedKmText.toFiniteNonNegativeDoubleOrNull() ?: 0.0
        if (condition == BikePurchaseCondition.NEW && parsedKm <= 0.0 && !item.isUserModified) {
            return null
        }

        val resolvedDate = item.lastPerformedDateIso.trim().ifBlank { defaultDateIso }
        val limitKm = item.intervalKm?.takeIf { it.isFinite() && it > 0.0 }
            ?: item.firstBreakInAtKm?.takeIf { it.isFinite() && it > 0.0 }
            ?: 5000.0

        return ServiceLogEntity(
            logId = baselineLogId(motorcycleId, item.taskId),
            motorcycleId = motorcycleId,
            taskId = item.taskId,
            serviceTitle = item.taskName.ifBlank { item.taskId },
            locationOrShop = if (condition == BikePurchaseCondition.NEW) "Dealer / Break-In" else "Service Book Baseline",
            performedAtIsoDate = resolvedDate,
            odometerAtServiceKm = parsedKm,
            servicedAtKm = parsedKm,
            limitKm = limitKm,
            endKm = parsedKm + limitKm,
            costEur = 0.0,
            performedBy = if (condition == BikePurchaseCondition.NEW) "Dealer" else "Previous Owner",
            notes = item.logNotes.trim().ifBlank { "Baseline service history from onboarding setup" },
            checklistItemsJson = "[]"
        )
    }
}
