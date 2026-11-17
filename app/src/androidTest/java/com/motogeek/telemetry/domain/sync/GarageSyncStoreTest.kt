package com.motogeek.telemetry.domain.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motogeek.telemetry.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GarageSyncStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val a = Room.inMemoryDatabaseBuilder(context, MotoGeekDatabase::class.java).build()
    private val b = Room.inMemoryDatabaseBuilder(context, MotoGeekDatabase::class.java).build()
    private val account = "sync-test@example.com"
    @After fun close() { a.close(); b.close() }
    private fun bike(id: String = "bike") = MotorcycleEntity(id, "Honda", "CB650R", 2024, 649,
        1000.0, 10.0, 0.0, "{}", isActive = true)
    private fun fuel(id: Long = 0, bike: String = "bike", notes: String = "A") =
        FuelLogEntity(id, bike, "2026-11-17", 1010.0, 10.0, 1.5, 15.0, true, notes)
    private suspend fun sync(db: MotoGeekDatabase, device: String, remote: GarageSyncDocument = GarageSyncDocument()) =
        GarageSyncStore(db).mergeAndApply(account, device, remote)

    @Test fun sameIntegerIdOnTwoDevicesDoesNotOverwriteRecords() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        b.motoGeekDao().insertMotorcycle(bike())
        a.motoGeekDao().insertFuelLog(fuel(1, notes = "A"))
        b.motoGeekDao().insertFuelLog(fuel(1, notes = "B"))
        val docA = sync(a, "A")
        val docB = sync(b, "B", docA)
        sync(a, "A", docB)
        for (db in listOf(a, b)) {
            val logs = db.motoGeekDao().getFuelLogsForMotorcycleList("bike")
            assertEquals(setOf("A", "B"), logs.map { it.notes }.toSet())
            assertEquals(2, logs.size)
        }
        sync(b, "B", docA)
        assertEquals(2, b.motoGeekDao().getFuelLogsForMotorcycleList("bike").size)
    }

    @Test fun parentUpdatesPreserveChildrenAndActiveSelection() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        a.motoGeekDao().insertFuelLog(fuel())
        val first = sync(a, "A")
        sync(b, "B", first)
        b.motoGeekDao().insertMotorcycle(bike("local").copy(isActive = false))
        b.motoGeekDao().clearActiveMotorcycles()
        b.motoGeekDao().setActiveMotorcycle("local")
        a.motoGeekDao().updateMotorcycle(bike().copy(name = "Renamed"))
        sync(b, "B", sync(a, "A"))
        assertEquals("Renamed", b.motoGeekDao().getMotorcycleById("bike")!!.name)
        assertEquals(1, b.motoGeekDao().getFuelLogsForMotorcycleList("bike").size)
        assertEquals("local", b.motoGeekDao().getActiveMotorcycle()!!.id)
    }

    @Test fun editsAndDeletionsPropagateWithoutResurrection() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        a.motoGeekDao().insertFuelLog(fuel())
        val initial = sync(a, "A")
        sync(b, "B", initial)
        val bFuel = b.motoGeekDao().getFuelLogsForMotorcycleList("bike").single()
        b.motoGeekDao().updateFuelLog(bFuel.copy(notes = "Edited on B"))
        sync(a, "A", sync(b, "B", initial))
        assertEquals("Edited on B", a.motoGeekDao().getFuelLogsForMotorcycleList("bike").single().notes)
        a.motoGeekDao().deleteMotorcycle("bike")
        val deleted = sync(a, "A")
        sync(b, "B", deleted)
        sync(b, "B", initial)
        assertTrue(b.motoGeekDao().getAllMotorcycles().first().isEmpty())
        assertTrue(b.motoGeekDao().getFuelLogsForMotorcycleList("bike").isEmpty())
    }

    @Test fun identicalRestoredRowsAreAdoptedOnFirstLink() = runBlocking {
        for (db in listOf(a, b)) {
            db.motoGeekDao().insertMotorcycle(bike())
            db.motoGeekDao().insertFuelLog(fuel())
        }
        val cloud = sync(a, "A")
        sync(b, "B", cloud)
        assertEquals(1, b.motoGeekDao().getFuelLogsForMotorcycleList("bike").size)
    }

    @Test fun invalidCloudRollsBackAllLocalChangesAndCheckpoint() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        val cloud = sync(a, "A")
        val row = cloud.rows.getValue("motorcycles:bike")
        val invalid = cloud.copy(rows = mapOf(row.key to row.copy(fields = row.fields +
            ("unexpectedColumn" to SyncField(JsonPrimitive("bad"), SyncVersion(1, "A"))))))
        try { sync(b, "B", invalid); fail("Malformed cloud data must fail") } catch (_: IllegalArgumentException) { }
        assertTrue(b.motoGeekDao().getAllMotorcycles().first().isEmpty())
        b.openHelper.readableDatabase.query("SELECT COUNT(*) FROM cloud_sync_state").use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun unsentCheckpointSurvivesRetryAgainstOlderCloud() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        val initial = sync(a, "A")
        a.motoGeekDao().updateMotorcycle(bike().copy(name = "Not yet uploaded"))
        val pending = sync(a, "A", initial)
        assertEquals(pending, sync(a, "A", initial))
    }

    @Test fun rideAndTrackReferencesAreRemappedTogether() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        b.motoGeekDao().insertMotorcycle(bike("other"))
        fun insertRide(db: MotoGeekDatabase, bikeId: String) {
            // Required numeric statistics default to zero for this database fixture.
            val columns = db.openHelper.writableDatabase.query("PRAGMA table_info(ride_sessions)").use { c ->
                buildList { while (c.moveToNext()) if (c.getString(1) != "rideId") add(c.getString(1)) }
            }
            val values = android.content.ContentValues().apply {
                columns.forEach { put(it, 0) }
                put("motorcycleId", bikeId)
                put("ridingStyleBadge", "Touring")
                putNull("name"); putNull("notes")
            }
            db.openHelper.writableDatabase.insert("ride_sessions", 0, values)
        }
        insertRide(a, "bike")
        insertRide(b, "other")
        a.motoGeekDao().insertTelemetryTrackPoints(listOf(
            TelemetryTrackPointEntity(0, 1, 1000, 48.0, 17.0, 50.0, 10.0, 0.1, 0.2, 100.0)))
        sync(b, "B", sync(a, "A"))
        val imported = b.motoGeekDao().getRideSessionsForMotorcycleList("bike").single()
        assertNotEquals(1L, imported.rideId)
        assertEquals(1, b.motoGeekDao().getTelemetryTrackPointsListForRide(imported.rideId).size)
        assertTrue(b.motoGeekDao().getTelemetryTrackPointsListForRide(1).isEmpty())
    }

    @Test fun changedAccountDuringApplyRollsBackDatabase() = runBlocking {
        a.motoGeekDao().insertMotorcycle(bike())
        val cloud = sync(a, "A")
        var checks = 0
        try {
            GarageSyncStore(b).mergeAndApply(account, "B", cloud) {
                if (++checks == 3) throw SyncAccountChangedException()
            }
            fail("Session change must stop the transaction")
        } catch (_: SyncAccountChangedException) { }
        assertTrue(b.motoGeekDao().getAllMotorcycles().first().isEmpty())
    }

    @Test fun schemaTwoMigrationPreservesGarage() = runBlocking {
        val name = "garage-sync-migration-test.db"
        context.deleteDatabase(name)
        try {
            val old = Room.databaseBuilder(context, MotoGeekDatabase::class.java, name).build()
            old.motoGeekDao().insertMotorcycle(bike())
            old.openHelper.writableDatabase.execSQL("DROP TABLE cloud_sync_state")
            old.openHelper.writableDatabase.version = 2
            old.close()
            val migrated = Room.databaseBuilder(context, MotoGeekDatabase::class.java, name)
                .addMigrations(MotoGeekDatabase.MIGRATION_2_3).build()
            try {
                assertEquals("Honda", migrated.motoGeekDao().getMotorcycleById("bike")!!.make)
                assertEquals(1, sync(migrated, "migrated").rows.size)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
