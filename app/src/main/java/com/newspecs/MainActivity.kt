package com.newspecs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var reloadBtn: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateTime = findViewById(R.id.update_time)
        storyCount = findViewById(R.id.story_count)
        reloadBtn  = findViewById(R.id.reload_btn)

        adapter = NewsAdapter(this) { item ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.news_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(divider)

        reloadBtn.setOnClickListener { refresh() }

        // Show cached news immediately, then fetch fresh in background
        val cached = NewsCache.load(this)
        if (cached.isNotEmpty()) {
            adapter.submitList(cached)
            storyCount.text = "${cached.size} stories · <24h"
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Refresh time labels on each resume so relative times stay accurate
        val cached = NewsCache.load(this)
        if (cached.isNotEmpty()) adapter.submitList(cached)
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val news = NewsFetcher.fetch()
            if (news.isNotEmpty()) {
                NewsCache.save(this@MainActivity, news)
                withContext(Dispatchers.Main) {
                    adapter.submitList(news)
                    updateTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    storyCount.text = "${news.size} stories · <24h"
                }
            }
        }
    }
}
