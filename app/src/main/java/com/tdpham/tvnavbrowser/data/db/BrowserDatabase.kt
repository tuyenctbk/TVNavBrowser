package com.tdpham.tvnavbrowser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tdpham.tvnavbrowser.data.dao.BookmarkDao
import com.tdpham.tvnavbrowser.data.dao.HistoryDao
import com.tdpham.tvnavbrowser.data.entity.BookmarkEntity
import com.tdpham.tvnavbrowser.data.entity.HistoryEntity

@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class],
    version = 1
)
abstract class BrowserDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: BrowserDatabase? = null

        fun getInstance(context: Context): BrowserDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_database"
                ).build().also { instance = it }
            }
    }
}

