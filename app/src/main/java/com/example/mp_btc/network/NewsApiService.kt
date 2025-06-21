package com.example.mp_btc.network

import com.example.mp_btc.model.NewsApiResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService {
    @GET("v2/everything")
    fun getBitcoinNews(
        @Query("q") query: String = "bitcoin",
        @Query("pageSize") pageSize: Int = 20,
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("apiKey") apiKey: String
    ): Call<NewsApiResponse>
}