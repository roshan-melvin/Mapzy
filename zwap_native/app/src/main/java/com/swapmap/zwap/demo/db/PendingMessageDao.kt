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
    @Query("SELECT * FROM pending_messages WHERE channelId = :channelId AND threadId = :threadId ORDER BY createdAt ASC")
    fun getPendingMessages(channelId: String, threadId: String): Flow<List<PendingMessage>>

    @Query("SELECT * FROM pending_messages WHERE id = :id")
    suspend fun getMessageById(id: String): PendingMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessage)

    @Update
    suspend fun update(message: PendingMessage)

    @Delete
    suspend fun delete(message: PendingMessage)
}
