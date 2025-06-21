package com.example.mp_btc.domain

import kotlin.math.abs

// 각종 기술적 지표 계산 유틸리티.
object TechnicalIndicatorCalculator {

    // 단순 이동 평균(SMA) 계산.
    fun calculateSMA(data: List<Double>, window: Int): List<Double> {
        val result = MutableList(data.size) { Double.NaN }
        if (window <= 0 || data.isEmpty()) return result
        for (i in data.indices) {
            if (i >= window - 1) {
                result[i] = data.subList(i - window + 1, i + 1).average()
            }
        }
        return result
    }

    // 지수 이동 평균(EMA) 계산.
    fun calculateEMA(data: List<Double>, window: Int): List<Double> {
        val result = MutableList(data.size) { Double.NaN }
        if (window <= 0 || data.isEmpty()) return result
        val multiplier = 2.0 / (window + 1)
        for (i in data.indices) {
            if (i < window - 1) {
                result[i] = data.subList(0, i + 1).average()
            } else if (i == window - 1) {
                result[i] = data.subList(0, window).average()
            } else {
                result[i] = (data[i] - result[i - 1]) * multiplier + result[i - 1]
            }
        }
        return result
    }

    // 평균 실제 범위(ATR) 계산.
    fun calculateATR(highs: List<Double>, lows: List<Double>, closes: List<Double>, window: Int): List<Double> {
        if (highs.isEmpty()) return MutableList(highs.size) { Double.NaN }
        val trList = (0 until highs.size).map { i ->
            val hl = highs[i] - lows[i]
            if (i == 0) return@map hl
            val hpc = abs(highs[i] - closes[i - 1])
            val lpc = abs(lows[i] - closes[i - 1])
            maxOf(hl, hpc, lpc)
        }

        val atrList = MutableList(highs.size) { Double.NaN }
        if (trList.size < window) return atrList

        atrList[window - 1] = trList.subList(0, window).average()
        for (i in window until highs.size) {
            atrList[i] = (atrList[i - 1] * (window - 1) + trList[i]) / window
        }
        return atrList
    }

    // 상대 강도 지수(RSI) 계산.
    fun calculateRSI(data: List<Double>, window: Int): List<Double> {
        val result = MutableList(data.size) { Double.NaN }
        if (window <= 0 || data.size <= window) return result

        val changes = data.zipWithNext { a, b -> b - a }
        val gains = changes.map { if (it > 0) it else 0.0 }
        val losses = changes.map { if (it < 0) abs(it) else 0.0 }

        if (gains.size < window) return result

        var avgGain = gains.subList(0, window).average()
        var avgLoss = losses.subList(0, window).average()

        result[window] = 100.0 - (100.0 / (1.0 + if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss))

        for (i in window until changes.size) {
            avgGain = (avgGain * (window - 1) + gains[i]) / window
            avgLoss = (avgLoss * (window - 1) + losses[i]) / window
            val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
            result[i + 1] = 100.0 - (100.0 / (1.0 + rs))
        }
        return result
    }
}