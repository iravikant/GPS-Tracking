package com.gpstracking.utils

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {

    private const val DATE_FORMAT = "MMM dd, yyyy"
    private const val TIME_FORMAT = "hh:mm a"
    private const val DATE_TIME_FORMAT = "MMM dd, yyyy • hh:mm a"

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}