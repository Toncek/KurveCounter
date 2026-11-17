package com.motogeek.telemetry.domain.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GarageSyncMergeTest {
    private fun bike(name: String = "Original", notes: String = "", km: Double = 100.0, offset: Double = 0.0) =
        LocalSyncRow("motorcycles", "bike", mapOf("name" to JsonPrimitive(name), "notes" to JsonPrimitive(notes),
            "accumulatedTrackedKm" to JsonPrimitive(km), "manualAdjustmentOffsetKm" to JsonPrimitive(offset)))
    private fun capture(base: GarageSyncDocument, row: LocalSyncRow, node: String, remote: GarageSyncDocument = base) =
        GarageSyncMerge.capture(base, listOf(row), remote, node, 1000)
    private fun field(doc: GarageSyncDocument, name: String) = doc.rows.getValue("motorcycles:bike").fields.getValue(name).value
    private fun initial() = capture(GarageSyncDocument(), bike(), "A")

    @Test fun independentOfflineEditsAreBothRetained() {
        val base = initial()
        val a = capture(base, bike(name = "New name"), "A")
        val b = capture(base, bike(notes = "New notes"), "B")
        val merged = GarageSyncMerge.merge(a, b)
        assertEquals(JsonPrimitive("New name"), field(merged, "name"))
        assertEquals(JsonPrimitive("New notes"), field(merged, "notes"))
    }

    @Test fun concurrentSameFieldConflictConvergesInEitherOrder() {
        val base = initial()
        val a = capture(base, bike(name = "A"), "A")
        val b = capture(base, bike(name = "B"), "B")
        assertEquals(GarageSyncMerge.merge(a, b), GarageSyncMerge.merge(b, a))
        assertEquals(JsonPrimitive("B"), field(GarageSyncMerge.merge(a, b), "name"))
    }

    @Test fun repeatedAndStaleReplicasAreIdempotent() {
        val base = initial()
        val changed = capture(base, bike(name = "Edited"), "A")
        assertEquals(changed, GarageSyncMerge.merge(changed, changed))
        assertEquals(changed, GarageSyncMerge.merge(changed, base))
        assertEquals(changed, capture(changed, bike(name = "Edited"), "A", base))
    }

    @Test fun deletionWinsAgainstOfflineEditsAndOldReplicas() {
        val base = initial()
        val deleted = GarageSyncMerge.capture(base, emptyList(), base, "A", 2000)
        val edited = capture(base, bike(name = "Offline edit"), "B", deleted)
        assertNotNull(edited.rows.getValue("motorcycles:bike").deleted)
        assertEquals(edited, GarageSyncMerge.merge(edited, base))
    }

    @Test fun emptyCloudDoesNotDeleteLocalRows() {
        val base = initial()
        assertEquals(base, capture(base, bike(), "A", GarageSyncDocument()))
    }

    @Test fun cloudWinsExistingIdentityOnFirstLink() {
        val cloud = capture(initial(), bike(name = "Cloud"), "A")
        val linked = capture(GarageSyncDocument(), bike(name = "Old restored copy"), "B", cloud)
        assertEquals(JsonPrimitive("Cloud"), field(linked, "name"))
    }

    @Test fun offlineRideDistanceFromBothDevicesIsAddedExactlyOnce() {
        val base = initial()
        val a = capture(base, bike(km = 110.0), "A")
        val b = capture(base, bike(km = 120.0), "B")
        val merged = GarageSyncMerge.merge(a, b)
        assertEquals(130.0, field(merged, "accumulatedTrackedKm").jsonPrimitive.double, 0.0001)
        assertEquals(merged, GarageSyncMerge.merge(merged, a))
        assertEquals(merged, capture(merged, bike(km = 130.0), "B"))
    }

    @Test fun telemetryCannotDecreaseButExplicitCorrectionCan() {
        val base = initial()
        val result = capture(base, bike(km = 90.0, offset = -50.0), "A")
        assertEquals(100.0, field(result, "accumulatedTrackedKm").jsonPrimitive.double, 0.0)
        assertEquals(-50.0, field(result, "manualAdjustmentOffsetKm").jsonPrimitive.double, 0.0)
    }

    @Test fun mergedStateRoundTripsIncludingTombstonesAndDistance() {
        val base = initial()
        val deleted = GarageSyncMerge.capture(base, emptyList(), base, "A", 3000)
        assertEquals(deleted, Json.decodeFromString<GarageSyncDocument>(Json.encodeToString(deleted)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun newerSchemaFailsBeforeMerge() {
        GarageSyncMerge.merge(initial(), GarageSyncDocument(schemaVersion = 2))
    }

    @Test fun mergeIsAssociativeAcrossThreeOfflineDevices() {
        val base = initial()
        val a = capture(base, bike(name = "A", km = 110.0), "A")
        val b = capture(base, bike(notes = "B", km = 120.0), "B")
        val c = capture(base, bike(offset = 20.0, km = 130.0), "C")
        assertEquals(GarageSyncMerge.merge(GarageSyncMerge.merge(a, b), c),
            GarageSyncMerge.merge(a, GarageSyncMerge.merge(b, c)))
    }
}
