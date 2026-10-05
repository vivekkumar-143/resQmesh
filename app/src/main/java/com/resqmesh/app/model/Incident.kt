package com.resqmesh.app.model

data class Incident(
    val id: String,
    val type: String,
    val description: String,
    val latitude: Double?,
    val longitude: Double?,
    val severity: String,
    val timestamp: Long,
    val sourceDeviceId: String,
    val verified: Boolean = false,
    val expiresAt: Long? = null
)