package com.example.mp_btc.model

import com.google.gson.annotations.SerializedName

data class BinanceTickerResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("price")
    val price: String // 바이낸스는 가격을 문자열로 반환하므로, 사용할 때 Double로 변환 필요
)

data class Binance24hrTickerResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("priceChange")
    val priceChange: String, // 예: "-94.91000000" (절대 변동액)
    @SerializedName("priceChangePercent")
    val priceChangePercent: String, // 예: "-0.095" (퍼센트 변동률, % 기호 없음)
    @SerializedName("lastPrice")
    val lastPrice: String, // 현재 가격 (이것을 사용하면 /ticker/price 호출 불필요)
    // 필요한 경우 다른 필드들(예: highPrice, lowPrice 등)도 추가 가능
)