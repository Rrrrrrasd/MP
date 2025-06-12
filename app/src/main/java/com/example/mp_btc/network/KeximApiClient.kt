package com.example.mp_btc.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 한국수출입은행(KEXIM) 환율 정보 API 통신을 위한 Retrofit 클라이언트 객체.
 */
object KeximApiClient {
    private const val BASE_URL = "https://www.koreaexim.go.kr/"
    private const val AUTH_KEY = "myAppKey"

    val instance: KeximApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(KeximApiService::class.java)
    }

    /**
     * API 인증 키를 반환합니다.
     * @return 인증 키 문자열.
     */
    fun getApiKey(): String {
        return AUTH_KEY
    }

    /**
     * 현재 날짜를 "yyyyMMdd" 형식의 문자열로 반환합니다.
     * @return 포맷된 날짜 문자열.
     */
    fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}