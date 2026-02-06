package com.gpstracking.Room
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "TrackingSessionEntity")
data class TrackingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null
)