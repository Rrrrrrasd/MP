package com.example.mp_btc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.mp_btc.database.AppDatabase
import com.example.mp_btc.model.Transaction
import com.example.mp_btc.repository.PortfolioRepository
import kotlinx.coroutines.launch

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {
    // 데이터베이스와 상호작용하는 레포지토리
    private val repository: PortfolioRepository
    // 모든 거래 내역을 관찰하는 LiveData
    val allTransactions: LiveData<List<Transaction>>

    // ViewModel 생성 시 레포지토리와 LiveData 초기화
    init {
        val transactionDao = AppDatabase.getDatabase(application).transactionDao()
        repository = PortfolioRepository(transactionDao)
        allTransactions = repository.allTransactions
    }

    // 새로운 거래 내역을 데이터베이스에 추가한다 (백그라운드 스레드에서 실행).
    fun insert(transaction: Transaction) = viewModelScope.launch {
        repository.insert(transaction)
    }
}