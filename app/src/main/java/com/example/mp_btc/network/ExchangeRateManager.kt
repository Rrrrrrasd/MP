package com.example.mp_btc.network

import android.util.Log
import com.example.mp_btc.repository.BtcRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 앱 전체에서 사용할 환율 정보를 관리하는 싱글톤 객체.
 * 앱 시작 시 한 번만 환율을 가져와 저장합니다.
 */
object ExchangeRateManager {

    // USD to KRW 환율 정보를 저장하는 변수. null일 경우 환율 정보가 없는 것으로 간주합니다.
    var usdToKrwRate: Double? = null
        private set // 외부에서는 값을 변경할 수 없도록 private set으로 설정

    private val repository = BtcRepository()
    private var isFetching = false // 중복 호출 방지를 위한 플래그

    /**
     * 한국수출입은행 API를 호출하여 USD/KRW 환율 정보를 가져와 usdToKrwRate 변수에 저장합니다.
     * 앱 시작 시 Application 클래스에서 호출됩니다.
     */
    suspend fun initializeRate() {
        // 이미 환율 정보가 있거나, 현재 가져오는 중이면 다시 호출하지 않음
        if (usdToKrwRate != null || isFetching) {
            return
        }

        isFetching = true
        try {
            Log.d("ExchangeRateManager", "환율 정보 가져오기 시작...")
            // IO 스레드에서 네트워크 작업 수행
            withContext(Dispatchers.IO) {
                repository.getExchangeRate()
                    .onSuccess { rates ->
                        // API 호출 성공 시
                        if (rates.isNotEmpty() && rates.first().result == 1) {
                            val rate = rates.find { it.currencyUnit == "USD" }
                                ?.dealBaseRate?.replace(",", "")?.toDoubleOrNull()

                            usdToKrwRate = rate
                            Log.d("ExchangeRateManager", "환율 정보 가져오기 성공: $usdToKrwRate")
                        } else {
                            // API에서 에러 코드를 반환한 경우
                            Log.e("ExchangeRateManager", "환율 정보 API 오류: ${rates.firstOrNull()?.result}")
                        }
                    }
                    .onFailure { e ->
                        // 네트워크 오류 등 예외 발생 시
                        Log.e("ExchangeRateManager", "환율 정보 로드 실패", e)
                    }
            }
        } finally {
            isFetching = false
        }
    }
}