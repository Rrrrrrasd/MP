package com.example.mp_btc.model

import com.google.gson.annotations.SerializedName

// 바이낸스 24시간 Ticker API (/api/v3/ticker/24hr) 응답 구조
data class Binance24hrTickerResponse(
    @SerializedName("symbol")
    val symbol: String, // 심볼 (예: "BTCUSDT")
    @SerializedName("priceChange")
    val priceChange: String,  // 24시간 동안의 가격 변동 절대값
    @SerializedName("priceChangePercent")
    val priceChangePercent: String, // 24시간 동안의 가격 변동률 (%)
    @SerializedName("lastPrice")
    val lastPrice: String, // 가장 마지막에 체결된 가격 (현재가)
)