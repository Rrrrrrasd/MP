package com.example.mp_btc.model

import com.google.gson.annotations.SerializedName

// Frankfurter API의 응답을 담을 데이터 클래스
data class FrankfurterResponse(
    @SerializedName("rates")
    val rates: Map<String, Double> // {"KRW": 1385.32} 와 같은 환율 정보를 담는 맵
)