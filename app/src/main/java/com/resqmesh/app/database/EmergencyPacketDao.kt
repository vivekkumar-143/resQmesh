package com.resqmesh.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqmesh.app.model.EmergencyPacketEntity
@Dao
interface EmergencyPacketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: EmergencyPacketEntity)

    @Query("SELECT * FROM emergency_packets ORDER BY timestamp DESC")
    suspend fun getAllPackets(): List<EmergencyPacketEntity>

    @Query("SELECT * FROM emergency_packets WHERE transmitted = 0 ORDER BY timestamp ASC")
    suspend fun getPendingPackets(): List<EmergencyPacketEntity>

    @Query("UPDATE emergency_packets SET transmitted = 1 WHERE packetId = :packetId")
    suspend fun markAsTransmitted(packetId: String)

    @Query("SELECT * FROM emergency_packets WHERE packetId = :packetId LIMIT 1")
    suspend fun getPacket(packetId: String): EmergencyPacketEntity?

    @Query("DELETE FROM emergency_packets WHERE packetId = :packetId")
    suspend fun deletePacket(packetId: String)
}