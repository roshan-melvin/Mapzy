package com.swapmap.zwap.demo.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY timestamp DESC")
    fun getAllSavedPlaces(): Flow<List<SavedPlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPlace(place: SavedPlace)

    @Delete
    suspend fun deleteSavedPlace(place: SavedPlace)

    @Query("DELETE FROM saved_places")
    suspend fun deleteAllSavedPlaces()
}
