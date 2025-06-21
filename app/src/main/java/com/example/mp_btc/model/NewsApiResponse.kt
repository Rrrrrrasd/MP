package com.example.mp_btc.model

import com.google.gson.annotations.SerializedName

// News API의 최상위 응답 구조를 나타내는 데이터 클래스
data class NewsApiResponse(
    @SerializedName("status")
    val status: String, // 응답 상태 (예: "ok")
    @SerializedName("totalResults")
    val totalResults: Int, // 전체 검색 결과 수
    @SerializedName("articles")
    val articles: List<Article> // 기사 목록
)