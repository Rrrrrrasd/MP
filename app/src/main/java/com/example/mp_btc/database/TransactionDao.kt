package com.example.mp_btc.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mp_btc.model.Transaction

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    fun getAllTransactions(): LiveData<List<Transaction>>

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}