package com.hmie.btreport.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hmie.btreport.model.Trip

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun getAllTrips(): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Int): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)
}
