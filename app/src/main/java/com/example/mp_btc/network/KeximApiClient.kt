package com.example.mp_btc.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KeximApiClient {
    private const val BASE_URL = "https://www.koreaexim.go.kr/"


    private const val AUTH_KEY = "Sn9Qz2sTJahXBfJMpkPd2wZbacciUjhl"

    val instance: KeximApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(KeximApiService::class.java)
    }

    fun getApiKey(): String {
        return AUTH_KEY
    }

    // 오늘 날짜를 YYYYMMDD 형식으로 반환하는 헬퍼 함수
    fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}