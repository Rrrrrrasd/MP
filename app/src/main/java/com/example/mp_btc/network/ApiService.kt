package com.example.mp_btc.network

import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.model.BinanceTickerResponse

import com.example.mp_btc.model.MarketChartResponse

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    // --- 새로운 바이낸스 24시간 Ticker API 함수 ---
    @GET("https://api.binance.com/api/v3/ticker/24hr")
    fun getBinance24hrTicker(
        @Query("symbol") symbol: String = "BTCUSDT"
    ): Call<Binance24hrTickerResponse> // 새로운 응답 모델 사용

    // 과거 시세 (K-lines) 데이터 함수는 그대로 유지
    @GET("https://api.binance.com/api/v3/klines")
    fun getBinanceKlines(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("interval") interval: String,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int? = null
    ): Call<List<List<Any>>>
}