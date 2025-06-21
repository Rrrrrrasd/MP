package com.example.mp_btc.network

import com.example.mp_btc.model.Binance24hrTickerResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    //바이낸스 API를 통해 특정 심볼(기본값: BTCUSDT)의 24시간 가격 변동 정보를 가져옵니다.
    @GET("api/v3/ticker/24hr")
    fun getBinance24hrTicker(
        @Query("symbol") symbol: String = "BTCUSDT" // 조회할 암호화폐 심볼.
    ): Call<Binance24hrTickerResponse> //24시간 Ticker 정보를 담은 Call 객체.


    // 바이낸스 API를 통해 K-line(캔들스틱) 데이터를 가져옵니다.
    @GET("api/v3/klines")
    fun getBinanceKlines(
        @Query("symbol") symbol: String = "BTCUSDT", // 조회할 암호화폐 심볼
        @Query("interval") interval: String, // 캔들 차트의 시간 간격 (예: "1d", "4h", "5m")
        @Query("startTime") startTime: Long? = null, // 데이터를 가져올 시작 시간
        @Query("endTime") endTime: Long? = null, //데이터를 가져올 종료 시간
        @Query("limit") limit: Int? = null //데이터를 담은 리스트의 Call 객체
    ): Call<List<List<Any>>>
}