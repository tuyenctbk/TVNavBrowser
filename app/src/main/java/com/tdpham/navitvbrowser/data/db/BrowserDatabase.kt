package com.tdpham.navitvbrowser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tdpham.navitvbrowser.data.dao.BookmarkDao
import com.tdpham.navitvbrowser.data.dao.HistoryDao
import com.tdpham.navitvbrowser.data.dao.ReadLaterDao
import com.tdpham.navitvbrowser.data.entity.BookmarkEntity
import com.tdpham.navitvbrowser.data.entity.HistoryEntity
import com.tdpham.navitvbrowser.data.entity.ReadLaterEntity

@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class, ReadLaterEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun readLaterDao(): ReadLaterDao

    companion object {
        @Volatile
        private var instance: BrowserDatabase? = null

        fun getInstance(context: Context): BrowserDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_database"
                )
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
    }
}

