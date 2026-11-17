package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The applied baseline and local ID mapping commit atomically with synchronized garage rows. */
@Entity(tableName = "cloud_sync_state")
data class CloudSyncStateEntity(@PrimaryKey val accountEmail: String, val stateJson: String)
