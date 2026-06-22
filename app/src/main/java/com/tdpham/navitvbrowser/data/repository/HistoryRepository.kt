package com.tdpham.navitvbrowser.data.repository

import com.tdpham.navitvbrowser.data.dao.HistoryDao
import com.tdpham.navitvbrowser.data.entity.HistoryEntity
import com.tdpham.navitvbrowser.util.UrlUtils
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {

    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    fun getRecentHistory(limit: Int = 50): Flow<List<HistoryEntity>> =
        historyDao.getRecentHistory(limit)

    suspend fun addToHistory(title: String, url: String) {
        if (!UrlUtils.isBrowsableUrl(url)) return
        historyDao.deleteByUrl(url)
        historyDao.insertHistory(HistoryEntity(title = title, url = url))
    }

    suspend fun removeFromHistory(history: HistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}

