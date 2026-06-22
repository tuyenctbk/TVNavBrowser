package com.tdpham.navitvbrowser.data.repository

import com.tdpham.navitvbrowser.data.dao.BookmarkDao
import com.tdpham.navitvbrowser.data.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    fun getAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    suspend fun addBookmark(title: String, url: String): Boolean {
        if (bookmarkDao.getBookmarkByUrl(url) != null) return false
        bookmarkDao.insertBookmark(BookmarkEntity(title = title, url = url))
        return true
    }

    suspend fun removeBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun clearAll() {
        bookmarkDao.clearAll()
    }
}

