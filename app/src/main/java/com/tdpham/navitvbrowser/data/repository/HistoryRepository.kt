package com.tdpham.navitvbrowser.data.repository

import com.tdpham.navitvbrowser.data.dao.HistoryDao
import com.tdpham.navitvbrowser.data.entity.HistoryEntity
import com.tdpham.navitvbrowser.util.UrlUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository(private val historyDao: HistoryDao) {

    private val writeMutex = Mutex()

    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    fun getRecentHistory(limit: Int = 50): Flow<List<HistoryEntity>> =
        historyDao.getRecentHistory(limit)

    suspend fun searchHistory(query: String, limit: Int = 15): List<HistoryEntity> =
        historyDao.searchHistory(query, limit)

    suspend fun searchFrequentSites(query: String, limit: Int = 10): List<HistoryEntity> =
        historyDao.searchFrequentSites(query, limit)

    suspend fun getRecentHistoryList(limit: Int = 20): List<HistoryEntity> =
        historyDao.getRecentHistoryList(limit)

    suspend fun addToHistory(title: String, url: String) {
        val cleanUrl = url.trim()
        if (!UrlUtils.isBrowsableUrl(cleanUrl) || cleanUrl.startsWith("file:///", ignoreCase = true)) return
        val cleanTitle = title.trim().ifBlank { cleanUrl }
        writeMutex.withLock {
            historyDao.deleteByUrl(cleanUrl)
            historyDao.insertHistory(HistoryEntity(title = cleanTitle, url = cleanUrl))
        }
    }

    suspend fun removeFromHistory(history: HistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
