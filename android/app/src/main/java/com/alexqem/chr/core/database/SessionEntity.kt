package com.alexqem.chr.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val csvFile: String,
    val startedAt: String,
    val finishedAt: String?,
    val mode: String,
    val startCoolantC: Double?,
    val warmAtS: Double?,
    val rows: Int,
    val aborted: String?,
    val metaJson: String
)
