package com.tdpham.navitvbrowser.data.repository

import com.tdpham.navitvbrowser.data.dao.ReadLaterDao
import com.tdpham.navitvbrowser.data.entity.ReadLaterEntity
import kotlinx.coroutines.flow.Flow

class ReadLaterRepository(private val readLaterDao: ReadLaterDao) {
    fun getAllArticles(): Flow<List<ReadLaterEntity>> = readLaterDao.getAllArticles()

    suspend fun getArticleById(id: Long): ReadLaterEntity? = readLaterDao.getArticleById(id)

    suspend fun isSaved(url: String): Boolean = readLaterDao.getArticleByUrl(url) != null

    suspend fun saveArticle(title: String, url: String, contentHtml: String, excerpt: String): Long {
        val existing = readLaterDao.getArticleByUrl(url)
        if (existing != null) {
            readLaterDao.insertArticle(existing.copy(title = title, contentHtml = contentHtml, excerpt = excerpt))
            return existing.id
        }
        return readLaterDao.insertArticle(
            ReadLaterEntity(
                title = title,
                url = url,
                contentHtml = contentHtml,
                excerpt = excerpt
            )
        )
    }

    suspend fun deleteArticle(article: ReadLaterEntity) = readLaterDao.deleteArticle(article)

    suspend fun markAsRead(id: Long, isRead: Boolean) = readLaterDao.markAsRead(id, isRead)

    suspend fun clearAll() = readLaterDao.clearAll()
}
