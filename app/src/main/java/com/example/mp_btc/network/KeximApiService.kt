package com.example.mp_btc.network

import com.example.mp_btc.model.KeximExchangeRate
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface KeximApiService {
    /**
     * 한국수출입은행 API를 통해 특정 날짜의 환율 정보를 가져옵니다.
     * @param authKey API 인증 키.
     * @param searchDate 조회할 날짜 ("yyyyMMdd" 형식).
     * @param dataType 데이터 타입 (기본값: "AP01").
     * @return 환율 정보 리스트를 담은 Call 객체.
     */
    @GET("site/program/financial/exchangeJSON")
    fun getExchangeRates(
        @Query("authkey") authKey: String,
        @Query("searchdate") searchDate: String,
        @Query("data") dataType: String = "AP01"
    ): Call<List<KeximExchangeRate>>
}