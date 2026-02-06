package com.gpstracking.Room
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: TrackingSessionEntity): Long

    @Query("UPDATE TrackingSessionEntity SET endTime = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long)

    @Query("SELECT * FROM TrackingSessionEntity ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<TrackingSessionEntity>

    @Query("SELECT * FROM TrackingSessionEntity WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): TrackingSessionEntity?

    @Query("DELETE FROM TrackingSessionEntity WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}