package com.example.mp_btc.repository

import androidx.lifecycle.LiveData
import com.example.mp_btc.database.TransactionDao
import com.example.mp_btc.model.Transaction

class PortfolioRepository(private val transactionDao: TransactionDao) {
    val allTransactions: LiveData<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun insert(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }
}