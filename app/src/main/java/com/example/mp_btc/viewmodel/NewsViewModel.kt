package com.example.mp_btc.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mp_btc.model.Article
import com.example.mp_btc.repository.BtcRepository
import kotlinx.coroutines.launch

class NewsViewModel : ViewModel() {

    private val repository = BtcRepository()

    // 뉴스 기사 목록 LiveData
    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    // 토스트 메시지 LiveData
    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    // 로딩 상태 LiveData
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // ViewModel 생성 시 뉴스 데이터 로드
    init {
        fetchNews()
    }

    // NewsAPI를 통해 뉴스 데이터를 가져온다.
    fun fetchNews() {
        viewModelScope.launch {
            _isLoading.value = true // 로딩 시작
            repository.getBitcoinNews()
                .onSuccess { articleList ->
                    _articles.value = articleList // 성공 시 기사 목록 업데이트
                }
                .onFailure {
                    _toastMessage.value = "뉴스 로딩 실패: ${it.message}" // 실패 시 토스트 메시지 설정
                }
            _isLoading.value = false // 로딩 종료
        }
    }
}