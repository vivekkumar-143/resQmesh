package com.resqmesh.app.model

data class MeshMessage(
    val messageId: String,
    val senderDeviceId: String,
    val message: String,
    val timestamp: Long,
    val hopCount: Int = 0,
    val ttl: Int = 5
)