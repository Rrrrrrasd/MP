package com.example.mp_btc.network

import com.example.mp_btc.model.KeximExchangeRate
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface KeximApiService {
    @GET("site/program/financial/exchangeJSON")
    fun getExchangeRates(
        @Query("authkey") authKey: String,
        @Query("searchdate") searchDate: String, // YYYYMMDD 형식
        @Query("data") dataType: String = "AP01"
    ): Call<List<KeximExchangeRate>> // 응답이 JSON 배열이므로 List로 받음
}