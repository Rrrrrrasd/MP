package com.example.mp_btc.repository

import android.icu.text.SimpleDateFormat
import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.model.KeximExchangeRate
import com.example.mp_btc.network.ApiClient
import com.example.mp_btc.network.KeximApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * 네트워크 API 호출 등 앱의 데이터 소스를 관리하는 Repository.
 */
class BtcRepository {

    /**
     * 바이낸스에서 24시간 Ticker 정보를 비동기적으로 가져옵니다.
     * @return API 호출 결과를 담은 Result 객체.
     */
    suspend fun get24hrTicker(): Result<Binance24hrTickerResponse> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.instance.getBinance24hrTicker().execute()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 바이낸스에서 과거 시세(K-line) 데이터를 비동기적으로 가져옵니다.
     * @param interval 데이터 간격 (e.g., "1d").
     * @param startTime 조회 시작 타임스탬프.
     * @param limit 가져올 데이터 개수.
     * @return API 호출 결과를 담은 Result 객체.
     */
    suspend fun getHistoricalData(interval: String, startTime: Long?, limit: Int?): Result<List<List<Any>>> = withContext(Dispatchers.IO) {
        try {
            val endTime = System.currentTimeMillis()
            val response = ApiClient.instance.getBinanceKlines("BTCUSDT", interval, startTime, endTime, limit).execute()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 한국수출입은행에서 현재 환율 정보를 비동기적으로 가져옵니다.
     * @return API 호출 결과를 담은 Result 객체.
     */
    suspend fun getExchangeRate(): Result<List<KeximExchangeRate>> = withContext(Dispatchers.IO) {
        val apiKey = KeximApiClient.getApiKey()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        // 최대 5번까지 재시도 (오늘, 어제, 그제...)
        for (i in 0..4) {
            try {
                val searchDate = dateFormat.format(calendar.time)
                val response = KeximApiClient.instance.getExchangeRates(apiKey, searchDate).execute()

                if (response.isSuccessful && response.body() != null) {
                    val rates = response.body()!!
                    // 데이터가 비어있지 않고, 결과 코드가 성공(1)이면 성공으로 간주하고 반환
                    if (rates.isNotEmpty() && rates.first().result == 1) {
                        return@withContext Result.success(rates)
                    }
                }
                // 하루 전으로 날짜를 변경하여 다음 루프에서 재시도
                calendar.add(Calendar.DAY_OF_YEAR, -1)

            } catch (e: Exception) {
                // 네트워크 오류 등 예외 발생 시 마지막 시도였다면 실패 반환
                if (i == 4) {
                    return@withContext Result.failure(e)
                }
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        // 5번의 시도 모두 실패 시
        return@withContext Result.failure(Exception("Failed to get exchange rate data in the last 5 days."))
    }
}