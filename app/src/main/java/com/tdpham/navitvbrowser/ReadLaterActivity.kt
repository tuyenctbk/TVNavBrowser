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
    private var hasRenderedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_later)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)
        }

        ThemeHelper.applyActivityThemeView(this, findViewById(android.R.id.content))

        FirebaseInitializer.logEvent("read_later_screen_opened")

        container = findViewById(R.id.readLaterContainer)
        emptyView = findViewById(R.id.tvEmpty)
        btnClearAll = findViewById(R.id.btnClearAllReadLater)
        val btnReadLaterBack: ImageButton = findViewById(R.id.btnReadLaterBack)
        btnReadLaterBack.setBackgroundResource(ThemeHelper.getToolbarButtonBackground(this))
        btnReadLaterBack.imageTintList = android.content.res.ColorStateList.valueOf(ThemeHelper.getTextPrimaryColor(this))
        FocusAnimationHelper.apply(btnReadLaterBack)
        btnReadLaterBack.setOnClickListener { finish() }

        val db = BrowserDatabase.getInstance(applicationContext)
        repository = ReadLaterRepository(db.readLaterDao())

        FocusAnimationHelper.apply(btnClearAll)

        btnClearAll.setOnClickListener {
            AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
                .setTitle(R.string.read_later_clear_all)
                .setMessage(R.string.read_later_clear_all_confirm)
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
        val focusedIndex = (0 until container.childCount).firstOrNull { i ->
            val child = container.getChildAt(i)
            child.hasFocus() || child.findFocus() != null
        } ?: -1
        val hadRowFocus = focusedIndex >= 0

        container.removeAllViews()
        emptyView.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE
        btnClearAll.isEnabled = list.isNotEmpty()
        btnClearAll.alpha = if (list.isNotEmpty()) 1.0f else 0.4f

        val inflater = LayoutInflater.from(this)
        val textPrimary = ThemeHelper.getTextPrimaryColor(this)
        val textSecondary = ThemeHelper.getTextSecondaryColor(this)
        val accentColor = ThemeHelper.getAccentColor(this)
        val secondaryTint = android.content.res.ColorStateList.valueOf(textSecondary)
        val itemBg = ThemeHelper.getFocusableItemBackground(this)
        val btnBg = ThemeHelper.getToolbarButtonBackground(this)
        val badgeBg = ThemeHelper.getStepBadgeBackground(this)
        val readLabel = getString(R.string.read_later_read)
        val unreadLabel = getString(R.string.read_later_unread)
        list.forEachIndexed { index, article ->
            val row = inflater.inflate(R.layout.item_read_later, container, false)
            val rowContent = row.findViewById<android.view.View>(R.id.rowContent)
            val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
            val tvExcerpt = row.findViewById<TextView>(R.id.tvExcerpt)
            val tvUrl = row.findViewById<TextView>(R.id.tvUrl)
            val tvReadBadge = row.findViewById<TextView>(R.id.tvReadBadge)
            val btnDelete = row.findViewById<ImageButton>(R.id.btnDelete)

            tvTitle.text = article.title
            tvTitle.setTextColor(textPrimary)
            tvExcerpt.text = article.excerpt
            tvExcerpt.setTextColor(textSecondary)
            tvUrl.text = article.url
            tvUrl.setTextColor(textSecondary)
            rowContent.setBackgroundResource(itemBg)
            btnDelete.setBackgroundResource(btnBg)
            btnDelete.imageTintList = secondaryTint
            tvReadBadge.text = if (article.isRead) readLabel else unreadLabel
            tvReadBadge.setTextColor(accentColor)
            tvReadBadge.setBackgroundResource(badgeBg)

            FocusAnimationHelper.apply(rowContent)
            FocusAnimationHelper.apply(btnDelete)
            if (!hasRenderedOnce) {
                FocusAnimationHelper.animateEntrance(row, index)
            }

            rowContent.setOnClickListener {
                startActivity(
                    Intent(this, ReadLaterReaderActivity::class.java).apply {
                        putExtra(ReadLaterReaderActivity.EXTRA_ARTICLE_ID, article.id)
                    }
                )
            }
            rowContent.setOnLongClickListener {
                confirmDeleteArticle(article)
                true
            }

            btnDelete.setOnClickListener {
                confirmDeleteArticle(article)
            }

            container.addView(row)
        }
        hasRenderedOnce = true

        if (hadRowFocus) {
            if (container.childCount > 0) {
                val targetIdx = focusedIndex.coerceIn(0, container.childCount - 1)
                container.getChildAt(targetIdx)?.findViewById<android.view.View>(R.id.rowContent)?.requestFocus()
            } else {
                findViewById<android.view.View>(R.id.btnReadLaterBack)?.requestFocus()
            }
        }
    }

    private fun confirmDeleteArticle(article: ReadLaterEntity) {
        AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
            .setTitle(R.string.read_later_confirm_delete)
            .setMessage(getString(R.string.read_later_delete_msg, article.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.deleteArticle(article) }
                    Toast.makeText(this@ReadLaterActivity, R.string.read_later_removed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
