package com.example.mp_btc.network

import com.example.mp_btc.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// NewsAPI 통신을 위한 Retrofit 인스턴스를 관리하는 싱글턴 객체
object NewsApiClient {
    private const val BASE_URL = "https://newsapi.org/"


    private const val API_KEY = BuildConfig.NEWS_API_KEY

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