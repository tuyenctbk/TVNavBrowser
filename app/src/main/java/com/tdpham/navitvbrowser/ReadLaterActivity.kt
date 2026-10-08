package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.data.entity.ReadLaterEntity
import com.tdpham.navitvbrowser.data.repository.ReadLaterRepository
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.ThemeHelper
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadLaterActivity : ComponentActivity() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var btnClearAll: Button
    private lateinit var repository: ReadLaterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_later)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        ThemeHelper.applyActivityThemeView(this, findViewById(android.R.id.content))

        FirebaseInitializer.logEvent("read_later_screen_opened")

        container = findViewById(R.id.readLaterContainer)
        emptyView = findViewById(R.id.tvEmpty)
        btnClearAll = findViewById(R.id.btnClearAllReadLater)

        val db = BrowserDatabase.getInstance(applicationContext)
        repository = ReadLaterRepository(db.readLaterDao())

        FocusAnimationHelper.apply(btnClearAll)

        btnClearAll.setOnClickListener {
            AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
                .setTitle(R.string.read_later_clear_all)
                .setMessage(R.string.read_later_cleared)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.clearAll() }
                        Toast.makeText(this@ReadLaterActivity, R.string.read_later_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllArticles().collectLatest { list ->
                    renderArticles(list)
                }
            }
        }
    }

    private fun renderArticles(list: List<ReadLaterEntity>) {
        container.removeAllViews()
        emptyView.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE

        val inflater = LayoutInflater.from(this)
        list.forEach { article ->
            val row = inflater.inflate(R.layout.item_read_later, container, false)
            val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
            val tvExcerpt = row.findViewById<TextView>(R.id.tvExcerpt)
            val tvUrl = row.findViewById<TextView>(R.id.tvUrl)
            val tvReadBadge = row.findViewById<TextView>(R.id.tvReadBadge)
            val btnDelete = row.findViewById<ImageButton>(R.id.btnDelete)

            tvTitle.text = article.title
            tvExcerpt.text = article.excerpt
            tvUrl.text = article.url
            tvReadBadge.text = if (article.isRead) getString(R.string.read_later_read) else getString(R.string.read_later_unread)
            tvReadBadge.setBackgroundColor(if (article.isRead) 0x20808080 else 0x2038BDF8)

            FocusAnimationHelper.apply(row)
            FocusAnimationHelper.apply(btnDelete)

            row.setOnClickListener {
                startActivity(
                    Intent(this, ReadLaterReaderActivity::class.java).apply {
                        putExtra(ReadLaterReaderActivity.EXTRA_ARTICLE_ID, article.id)
                    }
                )
            }

            btnDelete.setOnClickListener {
                confirmDeleteArticle(article)
            }

            container.addView(row)
        }
    }

    private fun confirmDeleteArticle(article: ReadLaterEntity) {
        AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
            .setTitle(R.string.read_later_confirm_delete)
            .setMessage(getString(R.string.read_later_delete_msg, article.title))
            .setPositiveButton(R.string.settings_remove_rule) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.deleteArticle(article) }
                    Toast.makeText(this@ReadLaterActivity, R.string.read_later_removed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
