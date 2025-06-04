package com.example.mp_btc.model

import com.google.gson.annotations.SerializedName

data class KeximExchangeRate(
    @SerializedName("result") // 실제 JSON 키: result
    val result: Int,          // 조회 결과 (1: 성공, 2: DATA코드 오류, 3: 인증코드 오류, 4: 일일제한횟수 마감)

    @SerializedName("cur_unit") // 실제 JSON 키: cur_unit
    val currencyUnit: String,   // 통화코드

    @SerializedName("cur_nm") // 실제 JSON 키: cur_nm
    val currencyName: String,   // 국가/통화명

    @SerializedName("ttb") // 실제 JSON 키: ttb
    val transferRateBuy: String,  // 전신환(송금) 받으실때

    @SerializedName("tts") // 실제 JSON 키: tts
    val transferRateSell: String, // 전신환(송금) 보내실때

    @SerializedName("deal_bas_r") // 실제 JSON 키: deal_bas_r
    val dealBaseRate: String,  // 매매 기준율 (이것을 사용)

    @SerializedName("bkpr") // 실제 JSON 키: bkpr
    val bookPrice: String,      // 장부가격

    @SerializedName("yy_efee_r") // 실제 JSON 키: yy_efee_r
    val yearExchangeFeeRate: String, // 년환가료율

    @SerializedName("ten_dd_efee_r") // 실제 JSON 키: ten_dd_efee_r
    val tenDayExchangeFeeRate: String, // 10일환가료율

    @SerializedName("kftc_deal_bas_r") // 실제 JSON 키: kftc_deal_bas_r
    val kftcDealBaseRate: String,   // 서울외국환중개 매매기준율

    @SerializedName("kftc_bkpr") // 실제 JSON 키: kftc_bkpr
    val kftcBookPrice: String       // 서울외국환중개 장부가격
)