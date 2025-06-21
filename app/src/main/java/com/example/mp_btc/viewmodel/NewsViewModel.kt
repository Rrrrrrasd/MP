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

    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        fetchNews()
    }

    fun fetchNews() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getBitcoinNews()
                .onSuccess { articleList ->
                    _articles.value = articleList
                }
                .onFailure {
                    _toastMessage.value = "뉴스 로딩 실패: ${it.message}"
                }
            _isLoading.value = false
        }
    }
}