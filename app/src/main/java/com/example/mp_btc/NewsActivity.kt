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

    // 뷰 바인딩, 뷰모델, 리사이클러뷰 어댑터
    private lateinit var binding: ActivityNewsBinding
    private val newsViewModel: NewsViewModel by viewModels()
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정
        supportActionBar?.title = "Bitcoin News"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // UI 및 데이터 관련 초기화
        setupRecyclerView()
        observeViewModel()
    }

    // 툴바의 '뒤로가기' 버튼 클릭 시 동작
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // RecyclerView와 Adapter를 설정한다.
    private fun setupRecyclerView() {
        // 어댑터 초기화. 아이템 클릭 시 URL을 브라우저로 여는 동작을 정의한다.
        newsAdapter = NewsAdapter { article ->
            article.url.let { url ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
            }
        }
        binding.rvNews.adapter = newsAdapter
    }

    // ViewModel의 LiveData를 관찰하여 UI를 업데이트한다.
    private fun observeViewModel() {
        // 뉴스 기사 목록이 변경되면 어댑터에 데이터를 전달한다.
        newsViewModel.articles.observe(this) { articles ->
            newsAdapter.submitList(articles)
        }

        // 토스트 메시지가 발생하면 화면에 표시한다.
        newsViewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        // 로딩 상태가 변경되면 ProgressBar의 노출 여부를 제어한다.
        newsViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}