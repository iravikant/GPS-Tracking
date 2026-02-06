package com.gpstracking.Room
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {

    @Insert
    suspend fun insertLocation(location: LocationPointEntity)

    @Query("SELECT * FROM LocationPointEntity WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLocationsForSession(sessionId: Long): List<LocationPointEntity>

    @Query("SELECT COUNT(*) FROM LocationPointEntity WHERE sessionId = :sessionId")
    suspend fun getLocationCount(sessionId: Long): Int

    @Query("DELETE FROM LocationPointEntity WHERE sessionId = :sessionId")
    suspend fun deleteLocationsForSession(sessionId: Long)
}