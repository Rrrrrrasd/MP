package com.example.mp_btc.repository

import com.example.mp_btc.model.Article
import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.network.ApiClient
import com.example.mp_btc.network.CurrencyApiClient
import com.example.mp_btc.network.NewsApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 데이터 소스(네트워크, DB 등)를 관리하고 필요한 데이터를 제공.
class BtcRepository {

    // 바이낸스 24시간 Ticker 정보 조회.
    suspend fun get24hrTicker(): Result<Binance24hrTickerResponse> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.instance.getBinance24hrTicker().execute()
            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)
            else Result.failure(Exception("API Error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 바이낸스 과거 시세(K-line) 데이터 조회.
    suspend fun getHistoricalData(interval: String, startTime: Long?, limit: Int?): Result<List<List<Any>>> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.instance.getBinanceKlines("BTCUSDT", interval, startTime, System.currentTimeMillis(), limit).execute()
            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)
            else Result.failure(Exception("API Error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // USD/KRW 환율 정보 조회.
    suspend fun getExchangeRate(): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val response = CurrencyApiClient.instance.getExchangeRate().execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.rates["KRW"]?.let { Result.success(it) }
                    ?: Result.failure(Exception("KRW rate not found"))
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 비트코인 관련 최신 뉴스 조회.
    suspend fun getBitcoinNews(): Result<List<Article>> = withContext(Dispatchers.IO) {
        try {
            val response = NewsApiClient.instance.getBitcoinNews(apiKey = NewsApiClient.getApiKey()).execute()
            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!.articles)
            else Result.failure(Exception("News API Error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}