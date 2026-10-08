package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.data.entity.ReadLaterEntity
import com.tdpham.navitvbrowser.data.repository.ReadLaterRepository
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.ThemeHelper
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadLaterReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ARTICLE_ID = "extra_article_id"
    }

    private lateinit var repository: ReadLaterRepository
    private var currentArticle: ReadLaterEntity? = null

    private lateinit var tvArticleTitle: TextView
    private lateinit var tvArticleUrl: TextView
    private lateinit var tvArticleContent: TextView
    private lateinit var btnToggleReadStatus: Button
    private lateinit var btnOpenOnline: Button
    private lateinit var btnReaderBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_later_reader)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        ThemeHelper.applyActivityThemeView(this, findViewById(android.R.id.content))

        FirebaseInitializer.logEvent("read_later_reader_opened")

        val db = BrowserDatabase.getInstance(applicationContext)
        repository = ReadLaterRepository(db.readLaterDao())

        tvArticleTitle = findViewById(R.id.tvArticleTitle)
        tvArticleUrl = findViewById(R.id.tvArticleUrl)
        tvArticleContent = findViewById(R.id.tvArticleContent)
        btnToggleReadStatus = findViewById(R.id.btnToggleReadStatus)
        btnOpenOnline = findViewById(R.id.btnOpenOnline)
        btnReaderBack = findViewById(R.id.btnReaderBack)

        FocusAnimationHelper.applyAll(btnReaderBack, btnOpenOnline, btnToggleReadStatus)

        btnReaderBack.setOnClickListener { finish() }

        val articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L)
        if (articleId == -1L) {
            Toast.makeText(this, R.string.read_later_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val article = withContext(Dispatchers.IO) { repository.getArticleById(articleId) }
            if (article == null) {
                Toast.makeText(this@ReadLaterReaderActivity, R.string.read_later_empty, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentArticle = article
            displayArticle(article)

            // Mark as read automatically when opened
            if (!article.isRead) {
                withContext(Dispatchers.IO) { repository.markAsRead(article.id, true) }
            }
        }

        btnToggleReadStatus.setOnClickListener {
            val article = currentArticle ?: return@setOnClickListener
            val nextState = !article.isRead
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.markAsRead(article.id, nextState) }
                currentArticle = article.copy(isRead = nextState)
                updateReadButtonText(nextState)
            }
        }

        btnOpenOnline.setOnClickListener {
            val url = currentArticle?.url ?: return@setOnClickListener
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_URL, url)
                }
            )
            finish()
        }
    }

    private fun displayArticle(article: ReadLaterEntity) {
        tvArticleTitle.text = article.title
        tvArticleUrl.text = article.url

        val formattedText = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(article.contentHtml, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(article.contentHtml)
            }
        } catch (_: Exception) {
            article.excerpt
        }

        tvArticleContent.text = if (formattedText.isNullOrBlank()) article.excerpt else formattedText
        updateReadButtonText(true)
    }

    private fun updateReadButtonText(isRead: Boolean) {
        btnToggleReadStatus.text = if (isRead) getString(R.string.read_later_mark_unread) else getString(R.string.read_later_mark_read)
    }
}
