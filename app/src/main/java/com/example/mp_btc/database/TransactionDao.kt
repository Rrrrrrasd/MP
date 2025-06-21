package com.example.mp_btc.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mp_btc.model.Transaction

/**
 * Transaction 테이블에 접근하기 위한 데이터베이스 작업들을 정의하는 인터페이스 (DAO).
 */
@Dao
interface TransactionDao {

    /**
     * 새로운 거래(Transaction) 내역을 데이터베이스에 추가한다.
     * 동일한 ID가 존재할 경우 덮어쓴다.
     *
     * @param transaction 추가할 거래 객체
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    /**
     * 저장된 모든 거래 내역을 시간순(오름차순)으로 조회한다.
     * LiveData를 사용하여 데이터 변경 시 자동으로 UI를 업데이트할 수 있다.
     *
     * @return 거래 내역 리스트를 담은 LiveData
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    fun getAllTransactions(): LiveData<List<Transaction>>
}