package com.example.mp_btc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.mp_btc.databinding.ActivityNewsBinding
import com.example.mp_btc.ui.adapter.NewsAdapter
import com.example.mp_btc.viewmodel.NewsViewModel

class NewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsBinding
    private val newsViewModel: NewsViewModel by viewModels()
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Bitcoin News"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        newsAdapter = NewsAdapter { article ->
            article.url.let { url ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
            }
        }
        binding.rvNews.adapter = newsAdapter
    }

    private fun observeViewModel() {
        newsViewModel.articles.observe(this) { articles ->
            newsAdapter.submitList(articles)
        }

        newsViewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        newsViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}