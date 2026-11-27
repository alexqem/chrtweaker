package com.alexqem.chr.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SessionLtftStat(
    val sessionId: Long,
    val startedAt: String,
    val mode: String,
    val startCoolantC: Double?,
    val warmAtS: Double?,
    val avgLtft: Double?,
    val minLtft: Double?,
    val maxLtft: Double?,
    val countLtft: Int
)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("""
        SELECT 
            s.id AS sessionId,
            s.startedAt AS startedAt,
            s.mode AS mode,
            s.startCoolantC AS startCoolantC,
            s.warmAtS AS warmAtS,
            AVG(sp.ltft) AS avgLtft,
            MIN(sp.ltft) AS minLtft,
            MAX(sp.ltft) AS maxLtft,
            COUNT(sp.ltft) AS countLtft
        FROM sessions s
        LEFT JOIN samples sp ON s.id = sp.sessionId AND sp.ltft IS NOT NULL
        WHERE s.aborted IS NULL 
          AND s.finishedAt IS NOT NULL 
          AND s.mode != 'sim'
        GROUP BY s.id
        ORDER BY s.startedAt ASC
    """)
    suspend fun getLtftTrends(): List<SessionLtftStat>
}
