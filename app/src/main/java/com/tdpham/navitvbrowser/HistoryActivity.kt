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
import com.tdpham.navitvbrowser.data.entity.HistoryEntity
import com.tdpham.navitvbrowser.data.repository.HistoryRepository
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : ComponentActivity() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var repository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        FirebaseInitializer.logEvent("history_screen_opened")

        container = findViewById(R.id.historyContainer)
        emptyView = findViewById(R.id.tvEmpty)
        val db = BrowserDatabase.getInstance(applicationContext)
        repository = HistoryRepository(db.historyDao())

        lifecycleScope.launch {
            repository.getRecentHistory(100).collectLatest { list ->
                renderHistory(list)
            }
        }
    }

    private fun renderHistory(list: List<HistoryEntity>) {
        container.removeAllViews()
        emptyView.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE

        val inflater = LayoutInflater.from(this)
        list.forEach { history ->
            val row = inflater.inflate(R.layout.item_url_row, container, false)
            val ivRowIcon = row.findViewById<ImageView>(R.id.ivRowIcon)
            val tvRowTitle = row.findViewById<TextView>(R.id.tvRowTitle)
            val tvRowUrl = row.findViewById<TextView>(R.id.tvRowUrl)

            ivRowIcon.setImageResource(R.drawable.ic_nav_history)
            tvRowTitle.text = history.title
            tvRowUrl.text = history.url

            FocusAnimationHelper.apply(row)
            row.setOnClickListener {
                FirebaseInitializer.logEvent("history_opened", mapOf("url" to history.url))
                openUrl(history.url)
            }
            row.setOnLongClickListener {
                FirebaseInitializer.logEvent("history_deleted", mapOf("url" to history.url))
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.removeFromHistory(history) }
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
