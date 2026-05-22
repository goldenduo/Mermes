package com.mermes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mermes.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastMessageAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY lastMessageAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessions(limit: Int, offset: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE title LIKE '%' || :query || '%' ORDER BY lastMessageAt DESC LIMIT :limit")
    suspend fun searchSessions(query: String, limit: Int): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("UPDATE sessions SET messageCount = :count, lastMessageAt = :timestamp WHERE id = :id")
    suspend fun updateSessionStats(id: String, count: Int, timestamp: Long)
}
