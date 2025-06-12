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

/**
 * TFLite 모델을 사용하여 가격 예측을 담당하는 클래스
 */
class PricePredictor(private val context: Context) {

    // 원본 MainActivity의 내부 데이터 클래스들을 이 클래스 안으로 가져오거나,
    // 별도 model 파일로 분리할 수 있습니다. 여기서는 내부에 정의합니다.
    private data class ScalerParams(
        val feature_data_min: List<Double>,
        val feature_data_max: List<Double>,
        val target_close_data_min: Double,
        val target_close_data_max: Double
    )

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
    private val LOOK_BACK = 5

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

    /**
     * k-line 데이터를 받아 가격을 예측합니다.
     * @param klines 예측에 필요한 과거 데이터
     * @return 예측된 USD 가격, 실패 시 null
     */
    fun predict(klines: List<List<Any>>): Double? {
        if (klines.size < LOOK_BACK + 19) {
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

    // 원본의 processDataForPrediction 로직을 이 클래스에 맞게 수정
    private fun processDataAndPredict(klines: List<List<Any>>): Double? {
        Log.d("PredictionLogic", "Starting data processing for prediction. Klines received: ${klines.size}")

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
        Log.d("PredictionLogic", "Step 1: Parsed ${parsedKlines.size} klines.")

        val openPrices = parsedKlines.map { it.open }
        val highPrices = parsedKlines.map { it.high }
        val lowPrices = parsedKlines.map { it.low }
        val closePrices = parsedKlines.map { it.close }
        val volumes = parsedKlines.map { it.volume }

        // TechnicalIndicatorCalculator 사용
        val sma5 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 5)
        val sma10 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 10)
        val sma20 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 20)
        val ema5 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 5)
        val ema10 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 10)
        val ema20 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 20)
        val atr14 = TechnicalIndicatorCalculator.calculateATR(highPrices, lowPrices, closePrices, 14)
        val rsi14 = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14)

        val lastHalvingDate = LocalDate.of(2024, 4, 20)
        val daysSinceHalvingList = parsedKlines.map {
            val openDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.DAYS.between(lastHalvingDate, openDate).toDouble()
        }
        Log.d("PredictionLogic", "Step 2: Technical indicators calculated.")

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
        Log.d("PredictionLogic", "Step 3: Final sequence of ${finalSequenceRaw.size} prepared.")

        // ... (이하 스케일링, 버퍼 생성, 추론, 역스케일링 로직은 원본과 동일)
        // ... 최종적으로 Double 타입의 predictedPriceUsd를 반환

        val scaledInput2D = Array(LOOK_BACK) { FloatArray(featureColumns.size) }
        for (i in 0 until LOOK_BACK) {
            val currentDayFeatures = finalSequenceRaw[i]
            for (j in featureColumns.indices) {
                val featureName = featureColumns[j]
                val rawValue = currentDayFeatures[featureName] ?: return null
                if(rawValue.isNaN()) {
                    Log.e("PredictionLogic", "NaN found in final sequence for feature: $featureName.")
                    return null
                }
                val min = scalerParams.feature_data_min[j]
                val max = scalerParams.feature_data_max[j]
                val range = max - min
                scaledInput2D[i][j] = if (range != 0.0) ((rawValue - min) / range).toFloat() else 0.0f
            }
        }
        Log.d("PredictionLogic", "Step 4: Data scaled.")

        val inputBuffer = ByteBuffer.allocateDirect(1 * LOOK_BACK * featureColumns.size * 4).apply {
            order(ByteOrder.nativeOrder())
            for (i in 0 until LOOK_BACK) {
                for (j in 0 until featureColumns.size) {
                    putFloat(scaledInput2D[i][j])
                }
            }
        }
        Log.d("PredictionLogic", "Step 5: Input ByteBuffer prepared.")

        val outputBuffer = ByteBuffer.allocateDirect(1 * 1 * 4).apply { order(ByteOrder.nativeOrder()) }
        tflite!!.run(inputBuffer, outputBuffer)
        Log.d("PredictionLogic", "Step 6: TFLite model run completed.")

        outputBuffer.rewind()
        val scaledPrediction = outputBuffer.float
        val targetMin = scalerParams.target_close_data_min
        val targetMax = scalerParams.target_close_data_max
        val targetRange = targetMax - targetMin
        val predictedPriceUsd = if (targetRange != 0.0) {
            scaledPrediction * targetRange + targetMin
        } else {
            targetMin
        }
        Log.d("PredictionLogic", "Step 7: Prediction inverse-scaled. Predicted Price USD: $predictedPriceUsd")

        return predictedPriceUsd
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("btc_price_predictor_model.tflite")
        return FileInputStream(fileDescriptor.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun loadFeatureColumns(): List<String> {
        context.assets.open("feature_columns.json").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return Gson().fromJson(reader, object : TypeToken<List<String>>() {}.type)
            }
        }
    }

    private fun loadScalerParams(): ScalerParams {
        context.assets.open("scaler_params.json").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return Gson().fromJson(reader, ScalerParams::class.java)
            }
        }
    }
}