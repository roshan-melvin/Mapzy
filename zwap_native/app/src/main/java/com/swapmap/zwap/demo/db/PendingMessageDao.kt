package com.swapmap.zwap.demo.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessage)
    
    @Update
    suspend fun update(message: PendingMessage)
    
    @Delete
    suspend fun delete(message: PendingMessage)
    
    @Query("SELECT * FROM pending_messages WHERE channelId = :channelId ORDER BY createdAt DESC")
    fun getMessagesByChannel(channelId: String): Flow<List<PendingMessage>>
    
    @Query("SELECT * FROM pending_messages WHERE channelId = :channelId AND threadId = :threadId ORDER BY createdAt ASC")
    fun getPendingMessages(channelId: String, threadId: String): Flow<List<PendingMessage>>
    
    @Query("SELECT * FROM pending_messages WHERE status = 'Pending' OR status = 'Failed'")
    suspend fun getPendingMessages(): List<PendingMessage>
    
    @Query("SELECT * FROM pending_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): PendingMessage?
    
    @Query("DELETE FROM pending_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)
    
    @Query("UPDATE pending_messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)
}
