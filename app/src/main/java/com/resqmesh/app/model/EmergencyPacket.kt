package com.resqmesh.app.model


data class EmergencyPacket(
    val packetId: String,
    val senderDeviceId: String,
    val messageType: String,
    val latitude: Double?,
    val longitude: Double?,
    val severity: String,
    val message: String,
    val timestamp: Long,
    val hopCount: Int = 0,
    val ttl: Int = 5
)