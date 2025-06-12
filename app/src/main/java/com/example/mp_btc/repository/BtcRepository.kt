package com.example.mp_btc.repository

import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.model.KeximExchangeRate
import com.example.mp_btc.network.ApiClient
import com.example.mp_btc.network.KeximApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 앱의 데이터 소스를 관리하는 Repository.
 * 네트워크 API 호출을 담당하며, 추후 DB 캐싱 로직 추가 가능.
 */
class BtcRepository {

    // 네트워크 통신은 코루틴의 IO 컨텍스트에서 수행
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

    suspend fun getExchangeRate(): Result<List<KeximExchangeRate>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = KeximApiClient.getApiKey()
            val today = KeximApiClient.getTodayDateString()
            val response = KeximApiClient.instance.getExchangeRates(apiKey, today).execute()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}