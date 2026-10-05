package com.resqmesh.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_packets")
data class EmergencyPacketEntity(

    @PrimaryKey
    val packetId: String,

    val senderDeviceId: String,

    val messageType: String,

    val latitude: Double?,

    val longitude: Double?,

    val severity: String,

    val message: String,

    val timestamp: Long,

    val hopCount: Int,

    val ttl: Int,

    val transmitted: Boolean = false
)