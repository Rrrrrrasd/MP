package com.example.mp_btc.domain

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// TFLite 모델을 사용한 비트코인 가격 예측 수행.
class PricePredictor(private val context: Context) {

    // 정규화/역정규화에 필요한 파라미터.
    private data class ScalerParams(
        val feature_data_min: List<Double>,
        val feature_data_max: List<Double>,
        val target_close_data_min: Double,
        val target_close_data_max: Double
    )

    // 캔들 데이터 구조 정의.
    private data class CandleData(
        val timestamp: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double
    )

    private var tflite: Interpreter? = null
    private val featureColumns: List<String>
    private val scalerParams: ScalerParams
    private val LOOK_BACK = 5 // 예측에 사용할 과거 데이터 일수.

    // 초기화 시 모델과 파라미터 로드.
    init {
        try {
            tflite = Interpreter(loadModelFile())
            featureColumns = loadFeatureColumns()
            scalerParams = loadScalerParams()
        } catch (ex: Exception) {
            Log.e("PricePredictor", "Failed to initialize", ex)
            throw IllegalStateException("PricePredictor initialization failed", ex)
        }
    }

    // K-line(시계열) 데이터로 다음 날 종가 예측.
    fun predict(klines: List<List<Any>>): Double? {
        if (klines.size < LOOK_BACK + 19) { // 예측에 필요한 최소 데이터 확인
            Log.e("PredictionLogic", "Not enough data for prediction. Required: ${LOOK_BACK + 19}, Got: ${klines.size}")
            return null
        }
        try {
            return processDataAndPredict(klines)
        } catch (e: Exception) {
            Log.e("PredictionLogic", "Prediction failed", e)
            return null
        }
    }

    // 데이터 전처리, 예측, 후처리 수행.
    private fun processDataAndPredict(klines: List<List<Any>>): Double? {
        // 1. 원본 데이터를 CandleData 객체로 파싱.
        val parsedKlines = klines.map {
            CandleData(
                timestamp = (it[0] as Double).toLong(),
                open = (it[1] as String).toDouble(),
                high = (it[2] as String).toDouble(),
                low = (it[3] as String).toDouble(),
                close = (it[4] as String).toDouble(),
                volume = (it[5] as String).toDouble()
            )
        }

        // 2. 기술적 지표 계산.
        val openPrices = parsedKlines.map { it.open }
        val highPrices = parsedKlines.map { it.high }
        val lowPrices = parsedKlines.map { it.low }
        val closePrices = parsedKlines.map { it.close }
        val volumes = parsedKlines.map { it.volume }

        val sma5 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 5)
        val sma10 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 10)
        val sma20 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 20)
        val ema5 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 5)
        val ema10 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 10)
        val ema20 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 20)
        val atr14 = TechnicalIndicatorCalculator.calculateATR(highPrices, lowPrices, closePrices, 14)
        val rsi14 = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14)

        // 반감기 이후 경과일 계산.
        val lastHalvingDate = LocalDate.of(2024, 4, 20)
        val daysSinceHalvingList = parsedKlines.map {
            val openDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.DAYS.between(lastHalvingDate, openDate).toDouble()
        }

        // 3. 모델 입력 데이터(Feature) 생성.
        val allFeaturesData = parsedKlines.indices.map { i ->
            mapOf(
                "Open" to openPrices[i], "High" to highPrices[i], "Low" to lowPrices[i],
                "Close" to closePrices[i], "Volume" to volumes[i], "SMA_5" to sma5[i],
                "SMA_10" to sma10[i], "SMA_20" to sma20[i], "EMA_5" to ema5[i],
                "EMA_10" to ema10[i], "EMA_20" to ema20[i], "ATR_14" to atr14[i],
                "RSI_14" to rsi14[i], "Days_Since_Last_Halving" to daysSinceHalvingList[i]
            )
        }
        val finalSequenceRaw = allFeaturesData.takeLast(LOOK_BACK)

        // 4. 데이터 정규화(Scaling).
        val scaledInput2D = Array(LOOK_BACK) { FloatArray(featureColumns.size) }
        for (i in 0 until LOOK_BACK) {
            val currentDayFeatures = finalSequenceRaw[i]
            for (j in featureColumns.indices) {
                val featureName = featureColumns[j]
                val rawValue = currentDayFeatures[featureName] ?: return null
                if(rawValue.isNaN()) return null
                val min = scalerParams.feature_data_min[j]
                val max = scalerParams.feature_data_max[j]
                val range = max - min
                scaledInput2D[i][j] = if (range != 0.0) ((rawValue - min) / range).toFloat() else 0.0f
            }
        }

        // 5. 모델 입력을 위한 ByteBuffer 생성.
        val inputBuffer = ByteBuffer.allocateDirect(1 * LOOK_BACK * featureColumns.size * 4).apply {
            order(ByteOrder.nativeOrder())
            scaledInput2D.forEach { row -> row.forEach { putFloat(it) } }
        }

        // 6. 모델 추론 실행.
        val outputBuffer = ByteBuffer.allocateDirect(1 * 1 * 4).apply { order(ByteOrder.nativeOrder()) }
        tflite!!.run(inputBuffer, outputBuffer)

        // 7. 예측 결과 역정규화(Inverse-scaling) 후 반환.
        outputBuffer.rewind()
        val scaledPrediction = outputBuffer.float
        val targetMin = scalerParams.target_close_data_min
        val targetMax = scalerParams.target_close_data_max
        val targetRange = targetMax - targetMin
        val predictedPriceUsd = if (targetRange != 0.0) (scaledPrediction * targetRange + targetMin) else targetMin

        return predictedPriceUsd
    }

    // assets에서 tflite 모델 파일 로드.
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("btc_price_predictor_model.tflite")
        return FileInputStream(fileDescriptor.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    // assets에서 feature 목록(JSON) 로드.
    private fun loadFeatureColumns(): List<String> {
        context.assets.open("feature_columns.json").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return Gson().fromJson(reader, object : TypeToken<List<String>>() {}.type)
            }
        }
    }

    // assets에서 scaler 파라미터(JSON) 로드.
    private fun loadScalerParams(): ScalerParams {
        context.assets.open("scaler_params.json").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return Gson().fromJson(reader, ScalerParams::class.java)
            }
        }
    }
}