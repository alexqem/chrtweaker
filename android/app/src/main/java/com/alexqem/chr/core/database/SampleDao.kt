package com.alexqem.chr.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SampleDao {
    @Insert
    suspend fun insertSample(sample: SampleEntity)

    @Insert
    suspend fun insertSamples(samples: List<SampleEntity>)

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY ts ASC")
    suspend fun getSamplesForSession(sessionId: Long): List<SampleEntity>
}
