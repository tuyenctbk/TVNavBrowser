package com.tdpham.navitvbrowser.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_later")
data class ReadLaterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val contentHtml: String,
    val excerpt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
