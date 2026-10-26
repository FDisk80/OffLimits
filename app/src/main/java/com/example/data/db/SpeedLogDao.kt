package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: SpeedingIncidentEntity): Long

    @Query("SELECT * FROM speeding_incidents ORDER BY timestamp DESC LIMIT 100")
    fun getAllIncidents(): Flow<List<SpeedingIncidentEntity>>

    @Query("SELECT COUNT(*) FROM speeding_incidents")
    fun getIncidentCount(): Flow<Int>

    @Query("DELETE FROM speeding_incidents")
    suspend fun clearIncidents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripRecordEntity): Long

    @Query("SELECT * FROM trips ORDER BY startTime DESC LIMIT 50")
    fun getAllTrips(): Flow<List<TripRecordEntity>>

    @Query("DELETE FROM trips")
    suspend fun clearTrips()
}
