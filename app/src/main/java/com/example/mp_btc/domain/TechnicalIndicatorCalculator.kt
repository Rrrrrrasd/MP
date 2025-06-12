package com.example.mp_btc.domain

import android.util.Log
import kotlin.math.abs

/**
 * 기술적 지표 계산을 담당하는 순수 로직 객체
 */
object TechnicalIndicatorCalculator {

    fun calculateSMA(data: List<Double>, window: Int): List<Double> {
        val result = MutableList(data.size) { Double.NaN }
        if (window <= 0 || data.isEmpty()) return result
        for (i in data.indices) {
            if (i >= window - 1) {
                val sum = data.subList(i - window + 1, i + 1).sum()
                result[i] = sum / window
            }
        }
        Log.d("TechIndicator", "SMA($window) calculated. Example last value: ${result.lastOrNull()}")
        return result
    }

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
        Log.d("TechIndicator", "EMA($window) calculated. Example last value: ${result.lastOrNull()}")
        return result
    }

    fun calculateATR(highs: List<Double>, lows: List<Double>, closes: List<Double>, window: Int): List<Double> {
        val trList = MutableList(highs.size) { 0.0 }
        if (highs.isEmpty()) return MutableList(highs.size) { Double.NaN }
        trList[0] = highs[0] - lows[0]
        for (i in 1 until highs.size) {
            val hl = highs[i] - lows[i]
            val hpc = abs(highs[i] - closes[i - 1])
            val lpc = abs(lows[i] - closes[i - 1])
            trList[i] = maxOf(hl, hpc, lpc)
        }
        val atrList = MutableList(highs.size) { Double.NaN }
        if (trList.size < window) return atrList
        atrList[window - 1] = trList.subList(0, window).average()
        for (i in window until highs.size) {
            atrList[i] = (atrList[i - 1] * (window - 1) + trList[i]) / window
        }
        Log.d("TechIndicator", "ATR($window) calculated. Example last value: ${atrList.lastOrNull()}")
        return atrList
    }

    fun calculateRSI(data: List<Double>, window: Int): List<Double> {
        val result = MutableList(data.size) { Double.NaN }
        if (window <= 0 || data.size <= window) return result
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1..window) {
            val difference = data[i] - data[i - 1]
            if (difference > 0) {
                gains.add(difference)
                losses.add(0.0)
            } else {
                gains.add(0.0)
                losses.add(abs(difference))
            }
        }
        if (gains.isEmpty() && losses.isEmpty() && window > 0) {
            for (i in window until data.size) result[i] = 50.0
            return result
        }
        var avgGain = if (gains.isNotEmpty()) gains.average() else 0.0
        var avgLoss = if (losses.isNotEmpty()) losses.average() else 0.0
        if (avgLoss == 0.0) {
            result[window] = 100.0
        } else {
            val rs = avgGain / avgLoss
            result[window] = 100.0 - (100.0 / (1.0 + rs))
        }
        for (i in window + 1 until data.size) {
            val difference = data[i] - data[i - 1]
            val currentGain = if (difference > 0) difference else 0.0
            val currentLoss = if (difference < 0) abs(difference) else 0.0
            avgGain = (avgGain * (window - 1) + currentGain) / window
            avgLoss = (avgLoss * (window - 1) + currentLoss) / window
            if (avgLoss == 0.0) {
                result[i] = 100.0
            } else {
                val rs = avgGain / avgLoss
                result[i] = 100.0 - (100.0 / (1.0 + rs))
            }
        }
        Log.d("TechIndicator", "RSI($window) calculated. Example last value: ${result.lastOrNull()}")
        return result
    }
}