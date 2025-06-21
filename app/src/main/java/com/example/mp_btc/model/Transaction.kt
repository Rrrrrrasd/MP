package com.example.mp_btc.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// "transactions" 테이블과 매핑되는 엔티티 클래스
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String, // "BUY" 또는 "SELL"
    val amount: Double, // BTC 수량
    val pricePerBtc: Double, // 1 BTC 당 가격 (USD 기준)
    val timestamp: Long // 거래 시간
)