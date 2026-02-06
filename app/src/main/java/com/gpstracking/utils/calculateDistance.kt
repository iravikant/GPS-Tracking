package com.gpstracking.utils

import android.location.Location
import com.gpstracking.Room.LocationPointEntity

fun calculateDistance(points: List<LocationPointEntity>): Float {
    var distance = 0f
    for (i in 0 until points.size - 1) {
        val result = FloatArray(1)
        Location.distanceBetween(
            points[i].lat, points[i].lng,
            points[i + 1].lat, points[i + 1].lng,
            result
        )
        distance += result[0]
    }
    return distance
}
