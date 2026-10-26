package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speeding_incidents")
data class SpeedingIncidentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val roadName: String,
    val speedLimitKmH: Int,
    val recordedSpeedKmH: Int,
    val deltaKmH: Int,
    val latitude: Double,
    val longitude: Double
)

@Entity(tableName = "trips")
data class TripRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val maxSpeedKmH: Double,
    val avgSpeedKmH: Double,
    val speedingIncidentCount: Int
)
