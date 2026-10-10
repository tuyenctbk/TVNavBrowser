package com.tdpham.navitvbrowser.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tdpham.navitvbrowser.data.entity.BookmarkEntity
import com.tdpham.navitvbrowser.data.repository.BookmarkRepository
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object BookmarkSyncManager {
    private const val TAG = "BookmarkSyncManager"

    private fun getDocumentId(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.replace("[^a-zA-Z0-9]".toRegex(), "_").take(32)
        }
    }

    suspend fun syncBookmarks(context: Context, repository: BookmarkRepository): Result<Int> = withContext(Dispatchers.IO) {
        if (!AppPreferences.isFirestoreSyncEnabled(context)) {
            return@withContext Result.success(0)
        }
        val userId = AppPreferences.getSyncUserId(context)
        try {
            val db = FirebaseFirestore.getInstance()
            val collectionRef = db.collection("users").document(userId).collection("bookmarks")
            
            // 1. Fetch remote bookmarks
            val snapshot = collectionRef.get().await()
            var newCount = 0
            for (doc in snapshot.documents) {
                val title = doc.getString("title") ?: continue
                val url = doc.getString("url") ?: continue
                // Remote data is untrusted (the sync id is shared); never import javascript:/data: URLs
                if (!UrlUtils.isBrowsableUrl(url)) continue

                val added = repository.addBookmark(title, url)
                if (added) newCount++
            }

            // 2. Upload any local bookmarks not yet in Firestore
            val localBookmarks = repository.getAllBookmarksList()
            for (local in localBookmarks) {
                val docId = getDocumentId(local.url)
                val data = mapOf(
                    "title" to local.title,
                    "url" to local.url,
                    "timestamp" to local.timestamp,
                    "userId" to userId
                )
                collectionRef.document(docId).set(data, SetOptions.merge()).await()
            }

            Log.d(TAG, "Synced bookmarks successfully. New added: $newCount")
            Result.success(newCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing bookmarks to Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun pushBookmark(context: Context, bookmark: BookmarkEntity) = withContext(Dispatchers.IO) {
        if (!AppPreferences.isFirestoreSyncEnabled(context)) return@withContext
        try {
            val userId = AppPreferences.getSyncUserId(context)
            val db = FirebaseFirestore.getInstance()
            val docId = getDocumentId(bookmark.url)
            val data = mapOf(
                "title" to bookmark.title,
                "url" to bookmark.url,
                "timestamp" to bookmark.timestamp,
                "userId" to userId
            )
            db.collection("users").document(userId).collection("bookmarks")
                .document(docId)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing bookmark to Firestore", e)
        }
    }

    suspend fun deleteBookmark(context: Context, bookmark: BookmarkEntity) = withContext(Dispatchers.IO) {
        if (!AppPreferences.isFirestoreSyncEnabled(context)) return@withContext
        try {
            val userId = AppPreferences.getSyncUserId(context)
            val db = FirebaseFirestore.getInstance()
            val docId = getDocumentId(bookmark.url)
            db.collection("users").document(userId).collection("bookmarks")
                .document(docId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting bookmark from Firestore", e)
        }
    }

    /** Deletes every bookmark stored in the cloud for this sync id. */
    suspend fun clearRemoteBookmarks(context: Context) = withContext(Dispatchers.IO) {
        if (!AppPreferences.isFirestoreSyncEnabled(context)) return@withContext
        try {
            val userId = AppPreferences.getSyncUserId(context)
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("users").document(userId).collection("bookmarks")
                .get().await()
            // Firestore batches are limited to 500 operations
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing remote bookmarks", e)
        }
    }
}
