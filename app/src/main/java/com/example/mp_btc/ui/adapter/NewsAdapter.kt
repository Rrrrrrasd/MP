package com.example.mp_btc.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mp_btc.R
import com.example.mp_btc.databinding.ItemNewsArticleBinding
import com.example.mp_btc.model.Article
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// 뉴스 기사 목록을 RecyclerView에 바인딩하는 어댑터
class NewsAdapter(private val onItemClicked: (Article) -> Unit) :
    ListAdapter<Article, NewsAdapter.NewsViewHolder>(DiffCallback) {

    // 뷰홀더를 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding)
    }

    // 뷰홀더에 데이터를 바인딩하고 클릭 리스너를 설정
    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val current = getItem(position)
        holder.itemView.setOnClickListener {
            onItemClicked(current)
        }
        holder.bind(current)
    }
    // 개별 뉴스 아이템의 뷰를 관리하는 뷰홀더
    class NewsViewHolder(private val binding: ItemNewsArticleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(article: Article) {
            binding.tvTitle.text = article.title
            binding.tvSource.text = "Source: ${article.source.name}"
            binding.tvPublishedAt.text = "Published: ${formatDate(article.publishedAt)}"

            Glide.with(binding.root.context)
                .load(article.urlToImage)
                .placeholder(R.drawable.ic_graph_placeholder) // 로딩 중 이미지
                .error(R.drawable.ic_graph_placeholder) // 에러 시 이미지
                .into(binding.ivArticleImage)
        }

        // UTC 시간 문자열을 "yyyy-MM-dd" 형식으로 변환한다.
        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                if (date != null) outputFormat.format(date) else dateString
            } catch (e: Exception) {
                dateString
            }
        }
    }

    // RecyclerView의 업데이트 효율을 높이기 위한 DiffUtil.ItemCallback
    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Article>() {
            override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
                return oldItem == newItem
            }
        }
    }
}