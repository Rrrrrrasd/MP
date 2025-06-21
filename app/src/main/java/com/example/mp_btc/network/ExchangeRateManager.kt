package com.example.mp_btc.network

import android.util.Log
import com.example.mp_btc.repository.BtcRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 앱 전체에서 사용할 환율 정보를 관리하는 싱글톤 객체.
// 앱 시작 시 한 번만 환율을 가져와 저장한다.
object ExchangeRateManager {

    // USD to KRW 환율 정보를 저장. 외부에서는 읽기만 가능.
    var usdToKrwRate: Double? = null
        private set

    private val repository = BtcRepository()
    private var isFetching = false

    // API를 호출하여 USD/KRW 환율 정보를 가져와 usdToKrwRate 변수에 저장한다.
    // 앱 시작 시 Application 클래스에서 호출된다.
    suspend fun initializeRate() {
        if (usdToKrwRate != null || isFetching) return

        isFetching = true
        try {
            Log.d("ExchangeRateManager", "환율 정보 가져오기 시작...")
            withContext(Dispatchers.IO) {
                repository.getExchangeRate()
                    .onSuccess { rate ->
                        usdToKrwRate = rate
                        Log.d("ExchangeRateManager", "환율 정보 가져오기 성공: $usdToKrwRate")
                    }
                    .onFailure { e -> Log.e("ExchangeRateManager", "환율 정보 로드 실패", e) }
            }
        } finally {
            isFetching = false
        }
    }
}