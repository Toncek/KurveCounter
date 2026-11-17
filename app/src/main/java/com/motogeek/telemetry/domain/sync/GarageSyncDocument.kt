package com.motogeek.telemetry.domain.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SyncVersion(val clock: Long, val device: String) : Comparable<SyncVersion> {
    override fun compareTo(other: SyncVersion): Int =
        compareValuesBy(this, other, SyncVersion::clock, SyncVersion::device)
}

@Serializable
data class SyncField(val value: JsonElement, val version: SyncVersion)

@Serializable
data class TrackedDistance(val baseKm: Double, val increments: Map<String, Double> = emptyMap()) {
    val totalKm: Double get() = baseKm + increments.values.sum()
    fun merge(other: TrackedDistance) = TrackedDistance(maxOf(baseKm, other.baseKm),
        (increments.keys + other.increments.keys).associateWith {
            maxOf(increments[it] ?: 0.0, other.increments[it] ?: 0.0)
        })
}

@Serializable
data class SyncRow(
    val table: String,
    val id: String,
    val fields: Map<String, SyncField>,
    val deleted: SyncVersion? = null,
    val trackedDistance: TrackedDistance? = null
) {
    val key: String get() = "$table:$id"
    fun values(): Map<String, JsonElement> = fields.mapValues { it.value.value }
}

@Serializable
data class GarageSyncDocument(val schemaVersion: Int = 1, val rows: Map<String, SyncRow> = emptyMap()) {
    fun latestClock(): Long = rows.values.maxOfOrNull { row ->
        maxOf(row.deleted?.clock ?: 0L, row.fields.values.maxOfOrNull { it.version.clock } ?: 0L)
    } ?: 0L
}

@Serializable
data class LocalSyncState(
    val document: GarageSyncDocument = GarageSyncDocument(),
    // SQLite integer IDs are device-local; they must never be used as cloud record identities.
    val numericIds: Map<String, Map<String, String>> = emptyMap()
)

data class LocalSyncRow(val table: String, val id: String, val values: Map<String, JsonElement>) {
    val key: String get() = "$table:$id"
}

/** Pure merge logic. Each device publishes its own replica, so simultaneous uploads cannot overwrite peers. */
object GarageSyncMerge {
    fun merge(left: GarageSyncDocument, right: GarageSyncDocument): GarageSyncDocument {
        require(left.schemaVersion == 1 && right.schemaVersion == 1) { "Update the app to read this sync format" }
        val result = left.rows.toMutableMap()
        right.rows.forEach { (key, remote) ->
            require(key == remote.key) { "Invalid sync record identity" }
            val local = result[key]
            result[key] = if (local == null) remote else {
                val fields = local.fields.toMutableMap()
                remote.fields.forEach { (name, field) ->
                    val old = fields[name]
                    fields[name] = if (old == null) field else mergeField(remote.table, name, old, field)
                }
                val distance = local.trackedDistance?.let { a -> remote.trackedDistance?.let(a::merge) ?: a }
                    ?: remote.trackedDistance
                if (distance != null) fields["accumulatedTrackedKm"]?.let {
                    fields["accumulatedTrackedKm"] = it.copy(value = JsonPrimitive(distance.totalKm))
                }
                remote.copy(fields = fields, deleted = listOfNotNull(local.deleted, remote.deleted).maxOrNull(),
                    trackedDistance = distance)
            }
        }
        return GarageSyncDocument(rows = result.toSortedMap())
    }

    private fun mergeField(table: String, name: String, a: SyncField, b: SyncField): SyncField {
        val winner = if (a.version >= b.version) a else b
        // Automatic telemetry must not roll an odometer backwards. Explicit corrections live in
        // manualAdjustmentOffsetKm, which remains an ordinary independently versioned field.
        if (table == "motorcycles" && name == "accumulatedTrackedKm") {
            val km = maxOf(a.value.jsonPrimitive.doubleOrNull ?: 0.0, b.value.jsonPrimitive.doubleOrNull ?: 0.0)
            return winner.copy(value = JsonPrimitive(km))
        }
        return winner
    }

    fun capture(
        baseline: GarageSyncDocument,
        current: List<LocalSyncRow>,
        remote: GarageSyncDocument,
        device: String,
        now: Long
    ): GarageSyncDocument {
        val version = SyncVersion(maxOf(now, baseline.latestClock() + 1, remote.latestClock() + 1), device)
        val rows = baseline.rows.toMutableMap()
        val present = current.map { it.key }.toSet()
        current.forEach { local ->
            val previous = baseline.rows[local.key]
            // On first link, the cloud is authoritative for an already-known identity. Distinct
            // local records are retained. After linking, only actually edited fields get a new version.
            val firstCloud = if (previous == null) remote.rows[local.key] else null
            val fields = (previous?.fields ?: firstCloud?.fields ?: emptyMap()).toMutableMap()
            local.values.forEach { (name, value) ->
                if (previous != null && previous.fields[name]?.value != value ||
                    previous == null && name !in fields) {
                    fields[name] = SyncField(value, version)
                }
            }
            var distance = previous?.trackedDistance ?: firstCloud?.trackedDistance
            if (local.table == "motorcycles") {
                val km = local.values["accumulatedTrackedKm"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                if (distance == null) distance = TrackedDistance(km)
                else if (previous != null && km > distance.totalKm) {
                    val increments = distance.increments.toMutableMap()
                    increments[device] = (increments[device] ?: 0.0) + km - distance.totalKm
                    distance = distance.copy(increments = increments)
                }
                fields["accumulatedTrackedKm"]?.let {
                    fields["accumulatedTrackedKm"] = it.copy(value = JsonPrimitive(distance.totalKm))
                }
            }
            rows[local.key] = SyncRow(local.table, local.id, fields, previous?.deleted ?: firstCloud?.deleted, distance)
        }
        baseline.rows.forEach { (key, old) ->
            if (key !in present && old.deleted == null) rows[key] = old.copy(deleted = version)
        }
        return merge(GarageSyncDocument(rows = rows), remote)
    }
}
