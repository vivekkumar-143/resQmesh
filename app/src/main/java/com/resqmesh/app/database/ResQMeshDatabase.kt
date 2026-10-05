package com.resqmesh.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.resqmesh.app.model.EmergencyPacketEntity
import com.resqmesh.app.model.IncidentEntity
import com.resqmesh.app.model.MeshMessageEntity

@Database(
    entities = [
        IncidentEntity::class,
        EmergencyPacketEntity::class,
        MeshMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ResQMeshDatabase : RoomDatabase() {

    abstract fun incidentDao(): IncidentDao

    abstract fun emergencyPacketDao(): EmergencyPacketDao

    abstract fun meshMessageDao(): MeshMessageDao

    companion object {

        @Volatile
        private var INSTANCE: ResQMeshDatabase? = null

        fun getDatabase(
            context: Context
        ): ResQMeshDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            ResQMeshDatabase::class.java,
                            "resqmesh_database"
                        )
                            .fallbackToDestructiveMigration()
                            .build()

                    INSTANCE = instance

                    instance
                }
        }
    }
}