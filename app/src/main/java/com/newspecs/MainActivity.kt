package com.newspecs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.newspecs.data.NewsCache
import com.newspecs.data.NewsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: NewsAdapter
    private lateinit var updateTime: TextView
    private lateinit var storyCount: TextView
    private lateinit var nextRefresh: TextView
    private lateinit var reloadBtn: ImageView

    private var countdown: CountDownTimer? = null
    private val refreshIntervalMs = 5 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateTime  = findViewById(R.id.update_time)
        storyCount  = findViewById(R.id.story_count)
        nextRefresh = findViewById(R.id.next_refresh)
        reloadBtn   = findViewById(R.id.reload_btn)

        adapter = NewsAdapter(this) { item ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.news_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider)!!
        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        divider.setDrawable(dividerDrawable)
        recyclerView.addItemDecoration(divider)

        reloadBtn.setOnClickListener { refresh() }

        val cached = NewsCache.load(this)
        if (cached.isNotEmpty()) {
            adapter.submitList(cached)
            storyCount.text = "${cached.size} STORIES · <24H"
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        val cached = NewsCache.load(this)
        if (cached.isNotEmpty()) {
            adapter.submitList(cached)
            storyCount.text = "${cached.size} STORIES · <24H"
        }
        // Recalculate remaining time from stored refresh timestamp and restart timer
        val lastRefresh = NewsCache.getRefreshTime(this)
        if (lastRefresh > 0) {
            val remaining = ((lastRefresh + refreshIntervalMs) - System.currentTimeMillis())
                .coerceAtLeast(0L)
            if (remaining > 0) startCountdown(remaining) else refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        countdown?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
    }

    private fun refresh() {
        countdown?.cancel()
        lifecycleScope.launch(Dispatchers.IO) {
            val news = NewsFetcher.fetch()
            if (news.isNotEmpty()) {
                NewsCache.save(this@MainActivity, news)
                NewsCache.saveRefreshTime(this@MainActivity)
                withContext(Dispatchers.Main) {
                    adapter.submitList(news)
                    updateTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    storyCount.text = "${news.size} STORIES · <24H"
                    startCountdown(refreshIntervalMs)
                }
            }
        }
    }

    private fun startCountdown(fromMs: Long) {
        countdown?.cancel()
        countdown = object : CountDownTimer(fromMs, 1000) {
            override fun onTick(remaining: Long) {
                val m = remaining / 60_000
                val s = (remaining % 60_000) / 1000
                nextRefresh.text = "REFRESH IN %d:%02d".format(m, s)
            }
            override fun onFinish() {
                nextRefresh.text = "REFRESH IN 0:00"
                refresh()
            }
        }.start()
    }
}
