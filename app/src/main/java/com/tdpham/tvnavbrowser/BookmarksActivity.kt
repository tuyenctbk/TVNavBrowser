package com.tdpham.tvnavbrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.tvnavbrowser.ui.FocusAnimationHelper
import com.tdpham.tvnavbrowser.data.db.BrowserDatabase
import com.tdpham.tvnavbrowser.data.entity.BookmarkEntity
import com.tdpham.tvnavbrowser.data.repository.BookmarkRepository
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
            val row = inflater.inflate(R.layout.item_url_row, container, false) as TextView
            row.text = getString(R.string.url_row_format, bookmark.title, bookmark.url)
            FocusAnimationHelper.apply(row)
            row.setOnClickListener { openUrl(bookmark.url) }
            row.setOnLongClickListener {
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
