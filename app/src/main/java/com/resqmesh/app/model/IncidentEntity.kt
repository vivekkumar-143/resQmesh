package com.resqmesh.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(

    @PrimaryKey
    val id: String,

    val type: String,

    val description: String,

    val latitude: Double?,

    val longitude: Double?,

    val severity: String,

    val timestamp: Long,

    val sourceDeviceId: String,

    val verified: Boolean = false,

    val synced: Boolean = false,

    val expiresAt: Long? = null
)