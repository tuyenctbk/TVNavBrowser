package com.tdpham.navitvbrowser.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tdpham.navitvbrowser.data.entity.ReadLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadLaterDao {
    @Query("SELECT * FROM read_later ORDER BY timestamp DESC")
    fun getAllArticles(): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: Long): ReadLaterEntity?

    @Query("SELECT * FROM read_later WHERE url = :url LIMIT 1")
    suspend fun getArticleByUrl(url: String): ReadLaterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ReadLaterEntity): Long

    @Delete
    suspend fun deleteArticle(article: ReadLaterEntity)

    @Query("UPDATE read_later SET isRead = :isRead WHERE id = :id")
    suspend fun markAsRead(id: Long, isRead: Boolean)

    @Query("DELETE FROM read_later")
    suspend fun clearAll()
}
