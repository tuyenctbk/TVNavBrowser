package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.data.entity.BookmarkEntity
import com.tdpham.navitvbrowser.data.repository.BookmarkRepository
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksActivity : ComponentActivity() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var repository: BookmarkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        FirebaseInitializer.logEvent("bookmarks_screen_opened")

        container = findViewById(R.id.bookmarksContainer)
        emptyView = findViewById(R.id.tvEmpty)
        val db = BrowserDatabase.getInstance(applicationContext)
        repository = BookmarkRepository(db.bookmarkDao())

        lifecycleScope.launch {
            repository.getAllBookmarks().collectLatest { list ->
                renderBookmarks(list)
            }
        }
    }

    private fun renderBookmarks(list: List<BookmarkEntity>) {
        container.removeAllViews()
        emptyView.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE

        val inflater = LayoutInflater.from(this)
        list.forEach { bookmark ->
            val row = inflater.inflate(R.layout.item_url_row, container, false)
            val ivRowIcon = row.findViewById<ImageView>(R.id.ivRowIcon)
            val tvRowTitle = row.findViewById<TextView>(R.id.tvRowTitle)
            val tvRowUrl = row.findViewById<TextView>(R.id.tvRowUrl)

            ivRowIcon.setImageResource(R.drawable.ic_nav_bookmark)
            tvRowTitle.text = bookmark.title
            tvRowUrl.text = bookmark.url

            FocusAnimationHelper.apply(row)
            row.setOnClickListener {
                FirebaseInitializer.logEvent("bookmark_opened", mapOf("url" to bookmark.url))
                openUrl(bookmark.url)
            }
            row.setOnLongClickListener {
                FirebaseInitializer.logEvent("bookmark_deleted", mapOf("url" to bookmark.url))
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.removeBookmark(bookmark) }
                }
                true
            }
            container.addView(row)
        }
    }

    private fun openUrl(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_URL, url)
            }
        )
        finish()
    }
}
