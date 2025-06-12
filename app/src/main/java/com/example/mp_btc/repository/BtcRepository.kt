package com.example.mp_btc.repository

import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.model.KeximExchangeRate
import com.example.mp_btc.network.ApiClient
import com.example.mp_btc.network.KeximApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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