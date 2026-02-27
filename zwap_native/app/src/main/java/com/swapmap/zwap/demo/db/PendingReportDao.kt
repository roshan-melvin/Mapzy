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
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: PendingReport)
    
    @Update
    suspend fun update(report: PendingReport)
    
    @Delete
    suspend fun delete(report: PendingReport)
    
    @Query("SELECT * FROM pending_reports ORDER BY createdAt DESC")
    fun getAllPendingReports(): Flow<List<PendingReport>>
    
    @Query("SELECT * FROM pending_reports WHERE userId = :userId ORDER BY createdAt DESC")
    fun getReportsByUser(userId: String): Flow<List<PendingReport>>
    
    @Query("SELECT * FROM pending_reports WHERE status = 'Pending' OR status = 'Failed'")
    suspend fun getPendingReports(): List<PendingReport>
    
    @Query("DELETE FROM pending_reports WHERE id = :reportId")
    suspend fun deleteById(reportId: String)
    
    @Query("UPDATE pending_reports SET status = :status WHERE id = :reportId")
    suspend fun updateStatus(reportId: String, status: String)
}
