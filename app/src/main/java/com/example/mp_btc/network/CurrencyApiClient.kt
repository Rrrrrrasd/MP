package com.example.mp_btc.network

import com.example.mp_btc.model.FrankfurterResponse
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Frankfurter API 통신을 위한 Retrofit 클라이언트
object CurrencyApiClient {
    private const val BASE_URL = "https://api.frankfurter.app/"

    val instance: CurrencyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CurrencyApiService::class.java)
    }
}

// Frankfurter API 엔드포인트 정의
interface CurrencyApiService {
    @GET("latest")
    fun getExchangeRate(
        @Query("from") from: String = "USD",
        @Query("to") to: String = "KRW"
    ): Call<FrankfurterResponse>
}