package com.gpstracking.utils

import android.location.Location
import com.gpstracking.Room.LocationPointEntity
import kotlin.math.roundToInt

object DistanceUtils {


    fun calculateDistance(points: List<LocationPointEntity>): Float {
        if (points.size < 2) return 0f

        var totalDistance = 0f
        val results = FloatArray(1)

        for (i in 0 until points.size - 1) {
            Location.distanceBetween(
                points[i].lat,
                points[i].lng,
                points[i + 1].lat,
                points[i + 1].lng,
                results
            )
            totalDistance += results[0]
        }

        return totalDistance
    }

    fun formatDistance(meters: Float): String {
        return when {
            meters < 1000 -> "${meters.roundToInt()} m"
            else -> String.format("%.2f km", meters / 1000)
        }
    }

    fun calculateDuration(startTime: Long, endTime: Long): Long {
        return endTime - startTime
    }


    fun formatDuration(durationMillis: Long): String {
        val seconds = (durationMillis / 1000).toInt()
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes % 60)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds % 60)
            else -> String.format("%ds", seconds)
        }
    }
}