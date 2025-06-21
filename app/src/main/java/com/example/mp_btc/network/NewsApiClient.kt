package com.example.mp_btc.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NewsApiClient {
    private const val BASE_URL = "https://newsapi.org/"

    // 중요: 여기에 발급받은 NewsAPI 키를 입력하세요.
    private const val API_KEY = "6064ee4105e24d68b8fbbba660ecda29"

    val instance: NewsApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(NewsApiService::class.java)
    }

    fun getApiKey(): String {
        return API_KEY
    }
}