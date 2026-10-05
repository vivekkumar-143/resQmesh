package com.resqmesh.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqmesh.app.model.IncidentEntity

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    suspend fun getAllIncidents(): List<IncidentEntity>

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getIncidentCount(): Int

    @Query("SELECT * FROM incidents WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedIncidents(): List<IncidentEntity>

    @Query("UPDATE incidents SET synced = 1 WHERE id = :incidentId")
    suspend fun markAsSynced(incidentId: String)

    @Query("UPDATE incidents SET verified = 1 WHERE id = :incidentId")
    suspend fun markAsVerified(incidentId: String)

    @Query("DELETE FROM incidents WHERE id = :incidentId")
    suspend fun deleteIncident(incidentId: String)

    @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
    suspend fun getIncidentById(incidentId: String): IncidentEntity?
}