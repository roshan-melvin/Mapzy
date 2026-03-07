package com.swapmap.zwap.demo.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DriverTaskDao {
    @Query("SELECT * FROM driver_tasks ORDER BY createdAt ASC")
    fun getAllLive(): LiveData<List<DriverTask>>

    @Query("SELECT * FROM driver_tasks ORDER BY createdAt ASC")
    suspend fun getAll(): List<DriverTask>

    @Query("SELECT * FROM driver_tasks WHERE nearestPlaceLat IS NOT NULL AND nearestPlaceLon IS NOT NULL AND isCompleted = 0")
    suspend fun getTasksWithPlaces(): List<DriverTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DriverTask): Long

    @Update
    suspend fun update(task: DriverTask)

    @Delete
    suspend fun delete(task: DriverTask)

    @Query("DELETE FROM driver_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE driver_tasks SET isCompleted = :done WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean)

    @Query("UPDATE driver_tasks SET category = :cat, geminiProcessing = 0 WHERE id = :id")
    suspend fun updateCategory(id: Long, cat: String)

    @Query("UPDATE driver_tasks SET nearestPlaceName = :name, nearestPlaceDistance = :dist, nearestPlaceLat = :lat, nearestPlaceLon = :lon WHERE id = :id")
    suspend fun updatePlace(id: Long, name: String, dist: Double, lat: Double, lon: Double)

    @Query("UPDATE driver_tasks SET geminiProcessing = :processing WHERE id = :id")
    suspend fun setGeminiProcessing(id: Long, processing: Boolean)
}
