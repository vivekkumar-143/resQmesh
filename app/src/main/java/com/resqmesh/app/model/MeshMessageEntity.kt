package com.resqmesh.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_messages")
data class MeshMessageEntity(

    @PrimaryKey
    val messageId: String,

    val senderDeviceId: String,

    val message: String,

    val timestamp: Long,

    val hopCount: Int = 0,

    val ttl: Int = 5,

    val transmitted: Boolean = false
)