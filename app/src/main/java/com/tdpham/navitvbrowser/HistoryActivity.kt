package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.ThemeHelper
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.data.entity.HistoryEntity
import com.tdpham.navitvbrowser.data.repository.HistoryRepository
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import com.tdpham.navitvbrowser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : ComponentActivity() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var repository: HistoryRepository
    private var hasRenderedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)
        }

        ThemeHelper.applyActivityThemeView(this, findViewById(android.R.id.content))

        FirebaseInitializer.logEvent("history_screen_opened")

        container = findViewById(R.id.historyContainer)
        emptyView = findViewById(R.id.tvEmpty)
        val btnHistoryBack: ImageButton = findViewById(R.id.btnHistoryBack)
        btnHistoryBack.setBackgroundResource(ThemeHelper.getToolbarButtonBackground(this))
        btnHistoryBack.imageTintList = android.content.res.ColorStateList.valueOf(ThemeHelper.getTextPrimaryColor(this))
        FocusAnimationHelper.apply(btnHistoryBack)
        btnHistoryBack.setOnClickListener { finish() }

        val db = BrowserDatabase.getInstance(applicationContext)
        repository = HistoryRepository(db.historyDao())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getRecentHistory(100).collectLatest { list ->
                    renderHistory(list)
                }
            }
        }
    }

    private fun renderHistory(list: List<HistoryEntity>) {
        val focusedIndex = (0 until container.childCount).firstOrNull { i ->
            val child = container.getChildAt(i)
            child.hasFocus() || child.findFocus() != null
        } ?: -1
        val hadRowFocus = focusedIndex >= 0

        container.removeAllViews()
        emptyView.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE

        val inflater = LayoutInflater.from(this)
        val accentTint = android.content.res.ColorStateList.valueOf(ThemeHelper.getAccentColor(this))
        val textPrimary = ThemeHelper.getTextPrimaryColor(this)
        val textSecondary = ThemeHelper.getTextSecondaryColor(this)
        val secondaryTint = android.content.res.ColorStateList.valueOf(textSecondary)
        val itemBg = ThemeHelper.getFocusableItemBackground(this)
        val btnBg = ThemeHelper.getToolbarButtonBackground(this)
        list.forEachIndexed { index, history ->
            val row = inflater.inflate(R.layout.item_url_row, container, false)
            val rowContent = row.findViewById<android.view.View>(R.id.rowContent)
            val ivRowIcon = row.findViewById<ImageView>(R.id.ivRowIcon)
            val tvRowTitle = row.findViewById<TextView>(R.id.tvRowTitle)
            val tvRowUrl = row.findViewById<TextView>(R.id.tvRowUrl)
            val btnDeleteRow = row.findViewById<ImageButton>(R.id.btnDeleteRow)

            ivRowIcon.setImageResource(R.drawable.ic_nav_history)
            ivRowIcon.imageTintList = accentTint
            tvRowTitle.text = history.title
            tvRowTitle.setTextColor(textPrimary)
            tvRowUrl.text = history.url
            tvRowUrl.setTextColor(textSecondary)
            rowContent.setBackgroundResource(itemBg)
            btnDeleteRow.setBackgroundResource(btnBg)
            btnDeleteRow.imageTintList = secondaryTint

            FocusAnimationHelper.apply(rowContent)
            FocusAnimationHelper.apply(btnDeleteRow)
            if (!hasRenderedOnce) {
                FocusAnimationHelper.animateEntrance(row, index)
            }

            rowContent.setOnClickListener {
                FirebaseInitializer.logEvent("history_opened", mapOf("host" to UrlUtils.hostOf(history.url)))
                openUrl(history.url)
            }
            rowContent.setOnLongClickListener {
                confirmDeleteHistory(history)
                true
            }
            btnDeleteRow.setOnClickListener {
                confirmDeleteHistory(history)
            }
            container.addView(row)
        }
        hasRenderedOnce = true

        if (hadRowFocus) {
            if (container.childCount > 0) {
                val targetIdx = focusedIndex.coerceIn(0, container.childCount - 1)
                container.getChildAt(targetIdx)?.findViewById<android.view.View>(R.id.rowContent)?.requestFocus()
            } else {
                findViewById<android.view.View>(R.id.btnHistoryBack)?.requestFocus()
            }
        }
    }

    private fun confirmDeleteHistory(history: HistoryEntity) {
        AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
            .setTitle(R.string.history)
            .setMessage(getString(R.string.url_row_format, history.title, history.url))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                FirebaseInitializer.logEvent("history_deleted", mapOf("host" to UrlUtils.hostOf(history.url)))
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.removeFromHistory(history) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
