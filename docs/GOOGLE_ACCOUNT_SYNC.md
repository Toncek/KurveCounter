# Google account sync

KurveCounter queues garage sync every time the activity starts and five seconds after garage data changes. WorkManager waits for a connected network and retries transient network/server errors with exponential backoff. Sync pauses while recording a ride and resumes on retry after it finishes.

Select the same Google account in **JSON & Cloud Backup** on every device. Allow Google Drive access when prompted. The cloud card shows the sync state and offers **Sync now**. Google authorization must be completed by the user; selecting an account alone does not grant Drive access.

The selected account's hidden Drive app-data folder holds compressed device replicas. Each device writes its own file, reads all replicas with pagination, and merges them. Other devices receive changes on their next app start or local sync pass; this is eventual synchronization, not a live remote-push subscription. Existing manual backup files remain independent from sync replicas. Existing local garage data is published on first successful sync.

Synced data includes motorcycles and specifications, maintenance tasks, service checklists/history, fuel, parts, reminders, rides, corners, GPS tracks, and activity logs. Active motorcycle selection, sensor calibration, permissions, language, and automatic recording settings stay local to each device.

Independent field edits merge. Concurrent edits to the same field use a logical timestamp with device identity as a deterministic tie breaker. Deletions win over stale edits and remain as tombstones. Tracked distance uses per-device increments so independent offline rides add together once; explicit odometer corrections remain independently versioned. On first link, an existing cloud identity wins overlapping fields; distinct local records are retained. Identical restored numeric records are adopted rather than duplicated. A completely deleted record must be recreated with a new identity.

SQLite integer IDs are mapped to cloud UUIDs, and ride children are remapped with their parent. A Room transaction applies changes and saves the merge baseline together. Updates preserve child rows and active local IDs. An interrupted upload keeps its pending checkpoint for retry. Database version 3 adds only the sync-state table through migration from version 2.

Signing out cancels queued work. Changing Google accounts requires confirmation before this device's garage is merged into the new account. Previously published data stays in the old account. All Drive clients use the explicitly selected account.

Verification: JVM merge tests cover concurrent edits, stale replicas, deletion, first link, distance, format rejection, and serialization. Android database tests cover migration, numeric collisions, parent updates, edits/deletion, reference remapping, retry checkpoints, and rollback on malformed data or account changes. Live cross-device Drive transfer additionally requires both devices to use the same account and grant Google's consent.

References: [Drive app data](https://developers.google.com/workspace/drive/api/guides/appdata), [WorkManager constraints and retries](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
