package com.example.mp_btc.network

import com.example.mp_btc.model.NewsApiResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService {
    @GET("v2/everything")
    fun getBitcoinNews(
        @Query("q") query: String = "bitcoin", //query 검색어
        @Query("pageSize") pageSize: Int = 20, //pageSize 페이지당 결과 수
        @Query("sortBy") sortBy: String = "publishedAt", //sortBy 정렬 기준 (예: "publishedAt" - 최신순)
        @Query("apiKey") apiKey: String //apiKey NewsAPI 인증을 위한 API 키
    ): Call<NewsApiResponse> // 뉴스 API 응답을 담은 Call 객체
}