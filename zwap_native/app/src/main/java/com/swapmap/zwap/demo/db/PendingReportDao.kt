package com.swapmap.zwap.demo.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingReportDao {
    @Query("SELECT * FROM pending_reports ORDER BY createdAt DESC")
    fun getAllPendingReports(): Flow<List<PendingReport>>

    @Query("SELECT * FROM pending_reports WHERE id = :id")
    suspend fun getReportById(id: String): PendingReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: PendingReport)

    @Update
    suspend fun update(report: PendingReport)

    @Delete
    suspend fun delete(report: PendingReport)
}
