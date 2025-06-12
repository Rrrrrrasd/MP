package com.example.mp_btc.network

import com.example.mp_btc.model.Binance24hrTickerResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    /**
     * 바이낸스 API를 통해 특정 심볼(기본값: BTCUSDT)의 24시간 가격 변동 정보를 가져옵니다.
     * @param symbol 조회할 암호화폐 심볼.
     * @return 24시간 Ticker 정보를 담은 Call 객체.
     */
    @GET("https://api.binance.com/api/v3/ticker/24hr")
    fun getBinance24hrTicker(
        @Query("symbol") symbol: String = "BTCUSDT"
    ): Call<Binance24hrTickerResponse>

    /**
     * 바이낸스 API를 통해 K-line(캔들스틱) 데이터를 가져옵니다.
     * @param symbol 조회할 암호화폐 심볼.
     * @param interval 캔들 차트의 시간 간격 (예: "1d", "4h", "5m").
     * @param startTime 데이터를 가져올 시작 시간 (타임스탬프).
     * @param endTime 데이터를 가져올 종료 시간 (타임스탬프).
     * @param limit 가져올 데이터의 최대 개수.
     * @return K-line 데이터를 담은 리스트의 Call 객체.
     */
    @GET("https://api.binance.com/api/v3/klines")
    fun getBinanceKlines(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("interval") interval: String,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int? = null
    ): Call<List<List<Any>>>
}