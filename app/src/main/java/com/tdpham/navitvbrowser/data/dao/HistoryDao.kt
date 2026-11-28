package com.tdpham.navitvbrowser.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.tdpham.navitvbrowser.data.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchHistory(query: String, limit: Int = 15): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' GROUP BY url ORDER BY COUNT(*) DESC, MAX(timestamp) DESC LIMIT :limit")
    suspend fun searchFrequentSites(query: String, limit: Int = 10): List<HistoryEntity>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryList(limit: Int = 20): List<HistoryEntity>

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT url, MAX(timestamp) AS timestamp FROM history GROUP BY url")
    suspend fun getLatestVisitTimestamps(): List<HistoryVisitProjection>

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

data class HistoryVisitProjection(
    val url: String,
    val timestamp: Long
)

