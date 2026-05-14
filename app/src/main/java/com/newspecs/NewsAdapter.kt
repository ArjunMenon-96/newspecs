package com.newspecs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.newspecs.data.FaviconLoader
import com.newspecs.data.NewsItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewsAdapter(
    private val context: Context,
    private val onItemClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    private var items: List<NewsItem> = emptyList()

    fun submitList(list: List<NewsItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.list_item_news, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.headline.text = item.title
        holder.sourceTag.text = item.shortSource
        holder.sourceTag.setTextColor(item.sourceColor)
        holder.timeAgo.text = item.timeAgo

        // Colored outlined tag: semi-transparent fill + colored stroke, matching widget look
        val c = item.sourceColor
        holder.sourceTag.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 3f.dp(context)
            setColor(Color.argb(22, Color.red(c), Color.green(c), Color.blue(c)))
            setStroke(1f.dp(context).toInt(), Color.argb(110, Color.red(c), Color.green(c), Color.blue(c)))
        }

        // Set placeholder immediately; replace with real favicon when loaded
        holder.favicon.setImageBitmap(FaviconLoader.createPlaceholder(item.source, item.sourceColor))
        holder.favicon.tag = item.source

        val domain = NewsItem.toDomainForFavicon(item.source)
        CoroutineScope(Dispatchers.IO).launch {
            val bmp = FaviconLoader.get(context, domain)
            withContext(Dispatchers.Main) {
                if (holder.favicon.tag == item.source && bmp != null) {
                    holder.favicon.setImageBitmap(bmp)
                }
            }
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    private fun Float.dp(ctx: Context) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, ctx.resources.displayMetrics)

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val favicon: ImageView = v.findViewById(R.id.favicon)
        val sourceTag: TextView = v.findViewById(R.id.source_tag)
        val timeAgo: TextView = v.findViewById(R.id.time_ago)
        val headline: TextView = v.findViewById(R.id.headline)
    }
}
