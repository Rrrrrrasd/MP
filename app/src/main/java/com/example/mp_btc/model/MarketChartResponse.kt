package com.example.mp_btc.model


import com.google.gson.annotations.SerializedName

data class MarketChartResponse(
    @SerializedName("prices")
    val prices: List<List<Double>> // [ [timestamp, price], ... ]
    // 필요하다면 market_caps, total_volumes 등도 추가
)