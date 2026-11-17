package com.motogeek.telemetry.domain.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

internal data class SyncTable(val name: String, val keys: List<String>, val numeric: Boolean = false)

internal val garageSyncTables = listOf(
    SyncTable("motorcycles", listOf("id")),
    SyncTable("maintenance_tasks", listOf("taskId", "motorcycleId")),
    SyncTable("service_logs", listOf("logId")),
    SyncTable("fuel_logs", listOf("fuelId"), true),
    SyncTable("part_logs", listOf("partId"), true),
    SyncTable("reminders", listOf("reminderId"), true),
    SyncTable("ride_sessions", listOf("rideId"), true),
    SyncTable("corner_events", listOf("cornerId"), true),
    SyncTable("telemetry_track_points", listOf("pointId"), true),
    SyncTable("app_logs", listOf("logId"))
)

/** Sync uses an explicit table allowlist, bound SQL values, and one Room transaction.
 * UPDATE (never INSERT OR REPLACE) preserves local foreign keys and ongoing ride IDs.
 */
class GarageSyncStore(private val database: MotoGeekDatabase) {
    private val json = Json { encodeDefaults = true }

    suspend fun mergeAndApply(
        account: String,
        device: String,
        remote: GarageSyncDocument,
        verifySession: () -> Unit = {}
    ): GarageSyncDocument =
        database.withTransaction {
            verifySession()
            val db = database.openHelper.writableDatabase
            val baseline = db.query("SELECT stateJson FROM cloud_sync_state WHERE accountEmail = ?", arrayOf(account)).use {
                if (it.moveToFirst()) json.decodeFromString<LocalSyncState>(it.getString(0)) else LocalSyncState()
            }
            val ids = mutableMapOf<String, MutableMap<String, String>>()
            val local = mutableListOf<LocalSyncRow>()
            val rawByKey = mutableMapOf<String, Map<String, JsonElement>>()
            garageSyncTables.forEach { table ->
                val used = mutableSetOf<String>()
                val mapping = mutableMapOf<String, String>()
                ids[table.name] = mapping
                readTable(db, table).forEach { raw ->
                    val values = raw.toMutableMap()
                    if (table.name == "motorcycles") values.remove("isActive") // selection is per device
                    if (table.numeric) values.remove(table.keys.single())
                    if ("rideId" in values) {
                        val ride = ids.getValue("ride_sessions")[values.getValue("rideId").jsonPrimitive.content]
                            ?: error("Ride reference missing from local garage")
                        values["rideId"] = JsonPrimitive(ride)
                    }
                    val id = if (table.numeric) {
                        val localId = raw.getValue(table.keys.single()).jsonPrimitive.content
                        val existing = baseline.numericIds[table.name]?.get(localId)
                        // Adopt identical previously imported rows on first link, one-to-one. Numeric
                        // key collisions between different devices never overwrite unrelated records.
                        val match = if (existing == null) remote.rows.values.firstOrNull {
                            it.table == table.name && it.deleted == null && it.id !in used && it.values() == values
                        }?.id else null
                        (existing ?: match ?: UUID.randomUUID().toString()).also { mapping[localId] = it }
                    } else identity(table, raw)
                    used += id
                    val row = LocalSyncRow(table.name, id, values)
                    local += row
                    rawByKey[row.key] = raw
                }
            }
            val merged = GarageSyncMerge.capture(baseline.document, local, remote, device, System.currentTimeMillis())
            validate(db, merged)
            val live = merged.rows.filterValues { isLive(it, merged) }
            verifySession()

            // Remove children first. Parent deletions propagate even when another device was offline.
            garageSyncTables.asReversed().forEach { table ->
                local.filter { it.table == table.name && it.key !in live }.forEach { row ->
                    val raw = rawByKey.getValue(row.key)
                    db.delete(table.name, where(table), arguments(table, raw))
                    if (table.numeric) ids.getValue(table.name).remove(raw.getValue(table.keys.single()).jsonPrimitive.content)
                }
            }
            garageSyncTables.forEach { table ->
                live.values.filter { it.table == table.name }.sortedBy { it.id }.forEach { row ->
                    val values = row.values().toMutableMap()
                    if ("rideId" in values) {
                        val globalRide = values.getValue("rideId").jsonPrimitive.content
                        val localRide = ids.getValue("ride_sessions").entries.firstOrNull { it.value == globalRide }?.key
                            ?: error("Synced ride reference is missing")
                        values["rideId"] = JsonPrimitive(localRide.toLong())
                    }
                    val previous = rawByKey[row.key]
                    if (previous == null) {
                        if (table.name == "motorcycles") values["isActive"] = JsonPrimitive(0)
                        val inserted = db.insert(table.name, SQLiteDatabase.CONFLICT_ABORT, contentValues(values))
                        if (table.numeric) ids.getValue(table.name)[inserted.toString()] = row.id
                    } else {
                        val changes = values.filter { (key, value) -> previous[key] != value }
                        if (changes.isNotEmpty()) db.update(table.name, SQLiteDatabase.CONFLICT_ABORT,
                            contentValues(changes), where(table), arguments(table, previous))
                    }
                }
            }
            // Keep the current selection; if its bike was deleted, select one of the remaining bikes.
            db.execSQL("UPDATE motorcycles SET isActive = 1 WHERE id = (SELECT id FROM motorcycles ORDER BY id LIMIT 1) AND NOT EXISTS (SELECT 1 FROM motorcycles WHERE isActive = 1)")
            val state = LocalSyncState(merged, ids)
            db.execSQL("INSERT OR REPLACE INTO cloud_sync_state(accountEmail, stateJson) VALUES (?, ?)",
                arrayOf(account, json.encodeToString(state)))
            verifySession()
            merged
        }

    private fun validate(db: SupportSQLiteDatabase, document: GarageSyncDocument) {
        require(document.schemaVersion == 1) { "Update the app to sync this garage" }
        val columns = garageSyncTables.associate { table ->
            table.name to db.query("PRAGMA table_info(${table.name})").use { cursor ->
                buildMap<String, Boolean> {
                    while (cursor.moveToNext()) put(cursor.getString(1), cursor.getInt(3) == 1)
                }
            }
        }
        document.rows.forEach { (key, row) ->
            val table = garageSyncTables.firstOrNull { it.name == row.table } ?: error("Unsupported sync table")
            require(key == row.key && row.id.isNotBlank()) { "Invalid sync identity" }
            row.trackedDistance?.let { distance ->
                require(row.table == "motorcycles" && distance.baseKm.isFinite() && distance.baseKm >= 0 &&
                    distance.increments.values.all { it.isFinite() && it >= 0 } && distance.totalKm.isFinite()) {
                    "Invalid tracked distance"
                }
            }
            val expected = columns.getValue(row.table).filterKeys {
                !(table.numeric && it == table.keys.single()) && !(table.name == "motorcycles" && it == "isActive")
            }
            require(row.fields.keys == expected.keys) { "Incompatible sync columns; update all devices" }
            row.fields.forEach { (name, field) ->
                require(field.value is JsonPrimitive) { "Invalid sync value" }
                require(field.version.clock >= 0 && field.version.clock < Long.MAX_VALUE - 1) { "Invalid sync version" }
                require(!expected.getValue(name) || field.value != JsonNull) { "Missing required sync value" }
            }
            if (!table.numeric) require(identity(table, row.values()) == row.id) { "Sync primary key mismatch" }
        }
    }

    private fun isLive(row: SyncRow, doc: GarageSyncDocument): Boolean {
        if (row.deleted != null) return false
        val parent = when {
            row.table in setOf("corner_events", "telemetry_track_points") -> "ride_sessions:${row.fields.getValue("rideId").value.jsonPrimitive.content}"
            row.table != "motorcycles" -> "motorcycles:${row.fields.getValue("motorcycleId").value.jsonPrimitive.content}"
            else -> return true
        }
        return isLive(doc.rows[parent] ?: error("Sync parent record is missing"), doc)
    }

    private fun identity(table: SyncTable, values: Map<String, JsonElement>): String =
        if (table.keys.size == 1) values.getValue(table.keys.single()).jsonPrimitive.content
        else JsonArray(table.keys.map { values.getValue(it) }).toString()

    private fun readTable(db: SupportSQLiteDatabase, table: SyncTable): List<Map<String, JsonElement>> =
        db.query("SELECT * FROM ${table.name}").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.columnNames.mapIndexed { index, name ->
                    name to when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JsonNull
                        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(index))
                        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(cursor.getString(index))
                        else -> error("Unsupported garage column type")
                    }
                }.toMap())
            }
        }

    private fun where(table: SyncTable) = table.keys.joinToString(" AND ") { "$it = ?" }
    private fun arguments(table: SyncTable, values: Map<String, JsonElement>): Array<Any> =
        table.keys.map { values.getValue(it).jsonPrimitive.content as Any }.toTypedArray()

    private fun contentValues(values: Map<String, JsonElement>) = ContentValues().apply {
        values.forEach { (key, value) ->
            val primitive = value.jsonPrimitive
            when {
                value == JsonNull -> putNull(key)
                primitive.isString -> put(key, primitive.content)
                primitive.longOrNull != null -> put(key, primitive.long)
                primitive.doubleOrNull != null -> put(key, primitive.double)
                else -> error("Invalid garage value")
            }
        }
    }
}
