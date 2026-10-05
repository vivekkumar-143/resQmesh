package com.resqmesh.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqmesh.app.model.MeshMessageEntity

@Dao
interface MeshMessageDao {

    // Save a new mesh message.
    // If the same messageId already exists, it will NOT create a duplicate.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MeshMessageEntity)

    // Get all messages, newest first.
    @Query("SELECT * FROM mesh_messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<MeshMessageEntity>

    // Get messages that still need to be transmitted to another node.
    @Query("""
        SELECT * FROM mesh_messages
        WHERE transmitted = 0
        ORDER BY timestamp ASC
    """)
    suspend fun getPendingMessages(): List<MeshMessageEntity>

    // Mark a message as successfully transmitted.
    @Query("""
        UPDATE mesh_messages
        SET transmitted = 1
        WHERE messageId = :messageId
    """)
    suspend fun markAsTransmitted(messageId: String)

    // Check whether a message has already been received.
    // This is important for duplicate protection in a mesh network.
    @Query("""
        SELECT * FROM mesh_messages
        WHERE messageId = :messageId
        LIMIT 1
    """)
    suspend fun getMessage(messageId: String): MeshMessageEntity?

    // Count stored messages.
    @Query("SELECT COUNT(*) FROM mesh_messages")
    suspend fun getMessageCount(): Int

    // Delete a message if required.
    @Query("""
        DELETE FROM mesh_messages
        WHERE messageId = :messageId
    """)
    suspend fun deleteMessage(messageId: String)
}