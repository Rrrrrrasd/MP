package com.example.mp_btc.model

import com.google.gson.annotations.SerializedName

// 개별 뉴스 기사의 상세 정보를 나타내는 데이터 클래스
data class Article(
    @SerializedName("source")
    val source: Source, // 뉴스 출처
    @SerializedName("author")
    val author: String?, // 기자 이름
    @SerializedName("title")
    val title: String, // 기사 제목
    @SerializedName("description")
    val description: String?, // 기사 요약
    @SerializedName("url")
    val url: String, // 기사 원문 URL
    @SerializedName("urlToImage")
    val urlToImage: String?, // 기사 이미지 URL
    @SerializedName("publishedAt")
    val publishedAt: String, // 기사 발행 시간
    @SerializedName("content")
    val content: String? // 기사 내용
)
// 뉴스의 출처 정보를 나타내는 데이터 클래스
data class Source(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String // 출처 이름
)