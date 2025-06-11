package com.example.mp_btc // 적절한 패키지명으로 변경

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mp_btc.databinding.ActivityMainBinding
import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.model.KeximExchangeRate // 한국수출입은행 API 응답 모델
import com.example.mp_btc.network.ApiClient
import com.example.mp_btc.network.KeximApiClient // 한국수출입은행 API 클라이언트
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.tensorflow.lite.Interpreter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis: Long = 3000000

    // LineChart -> CombinedChart로 변경
    private lateinit var chart: CombinedChart
    private var currentDaysPeriod: String = "1" // 현재 선택된 기간 저장 변수

    private var tflite: Interpreter? = null
    private val LOOK_BACK = 5
    private var featureColumns: List<String> = listOf()
    private var scalerParams: ScalerParams? = null

    // 원/달러 환율 저장 변수
    private var usdToKrwRate: Double? = null

    data class ScalerParams(
        val feature_data_min: List<Double>,
        val feature_data_max: List<Double>,
        val target_close_data_min: Double,
        val target_close_data_max: Double
    )

    data class CandleData(
        val timestamp: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double
    )

    private val updatePriceRunnable = object : Runnable {
        override fun run() {
            fetchBitcoinPrice()
            handler.postDelayed(this, updateIntervalMillis)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 차트 ID 변경에 따라 바인딩 수정
        chart = binding.priceChart
        setupChart()
        setupButtonListeners()

        // 새로고침 버튼 리스너 추가
        binding.btnRefresh.setOnClickListener {
            Toast.makeText(this, "새로고침 중...", Toast.LENGTH_SHORT).show()
            fetchBitcoinPrice()
            fetchHistoricalData(currentDaysPeriod)
        }

        try {
            tflite = Interpreter(loadModelFile())
            featureColumns = loadFeatureColumns()
            scalerParams = loadScalerParams()
        } catch (ex: Exception) {
            Log.e("MainActivity_TFLite", "Error loading TFLite model or JSON files", ex)
            showError("AI 모델 또는 설정 파일 로드 실패")
        }

        binding.btn1Day.post {
            updateButtonSelectionUI(binding.btn1Day)
            fetchHistoricalData(currentDaysPeriod)
        }
        fetchExchangeRate()

        binding.tvBottomInfo.setOnClickListener {
            if (tflite != null && scalerParams != null && featureColumns.isNotEmpty()) {
                binding.tvBitcoinPrice.text = getString(R.string.prediction_loading)

                fetchDataForPrediction { predictedUsdPrice -> // 콜백으로 Double? (USD 예측값) 받음
                    updateCurrentPriceDisplayAfterPrediction()

                    val intent = Intent(this@MainActivity, PredictionActivity::class.java)
                    val predictedPriceStringForDisplay: String

                    if (predictedUsdPrice != null) {
                        if (usdToKrwRate != null) {
                            val predictedKrwPrice = predictedUsdPrice * usdToKrwRate!!
                            val krwFormat = DecimalFormat("#,##0") // 원화는 보통 소수점 없이 표시
                            predictedPriceStringForDisplay = getString(R.string.krw_currency_symbol) + krwFormat.format(predictedKrwPrice)
                        } else {
                            val usdFormat = DecimalFormat("'$',##0.00")
                            predictedPriceStringForDisplay = usdFormat.format(predictedUsdPrice) + getString(R.string.exchange_rate_unavailable)
                        }
                    } else {
                        predictedPriceStringForDisplay = getString(R.string.prediction_failed)
                    }
                    intent.putExtra(PredictionActivity.EXTRA_PREDICTED_PRICE, predictedPriceStringForDisplay)
                    startActivity(intent)
                }
            } else {
                showError("AI 모델이 준비되지 않았습니다.")
            }
        }
    }

    private fun setupChart() {
        // CombinedChart 설정
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.drawOrder = arrayOf(CombinedChart.DrawOrder.CANDLE, CombinedChart.DrawOrder.LINE) // 캔들 먼저 그리고 라인 그리기

        // --- X축 설정 ---
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = DateAxisValueFormatter()
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.WHITE
        xAxis.textSize = 12f
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineColor = Color.WHITE
        xAxis.setLabelCount(5, true)

        // --- 왼쪽 Y축 설정 ---
        val yAxisLeft = chart.axisLeft
        yAxisLeft.valueFormatter = YAxisValueFormatter()
        yAxisLeft.setDrawGridLines(false)
        yAxisLeft.textColor = Color.WHITE
        yAxisLeft.textSize = 12f
        yAxisLeft.setDrawAxisLine(true)
        yAxisLeft.axisLineColor = Color.WHITE

        // --- 오른쪽 Y축 사용 안 함 ---
        chart.axisRight.isEnabled = false

        // --- 범례(Legend) 설정 ---
        val legend = chart.legend
        legend.isEnabled = true // 범례 표시 (SMA 구분을 위해)
        legend.textColor = Color.WHITE
        legend.textSize = 12f
    }

    private fun setupButtonListeners() {
        val buttonsAndDays = listOf(
            binding.btn1Day to "1",
            binding.btn5Day to "5",
            binding.btn1Month to "30",
            binding.btn6Months to "180",
            binding.btn1Year to "365",
            binding.btnAll to "max"
        )

        buttonsAndDays.forEach { (button, daysValue) ->
            button.setOnClickListener {
                currentDaysPeriod = daysValue // 현재 기간 저장
                fetchHistoricalData(daysValue)
                updateButtonSelectionUI(button)
            }
        }
    }

    private fun updateButtonSelectionUI(selectedBtn: Button) {
        val allButtons = listOf(
            binding.btn1Day, binding.btn5Day, binding.btn1Month,
            binding.btn6Months, binding.btn1Year, binding.btnAll
        )

        allButtons.forEach { button ->
            val materialButton = button as? com.google.android.material.button.MaterialButton

            if (button == selectedBtn) {
                val selectedBackgroundColor = ContextCompat.getColor(this, R.color.time_filter_button_selected_background)
                if (materialButton != null) {
                    materialButton.backgroundTintList = ColorStateList.valueOf(selectedBackgroundColor)
                } else {
                    button.setBackgroundColor(selectedBackgroundColor)
                }
                button.setTextColor(Color.BLACK)
                button.setTypeface(null, Typeface.BOLD)

            } else {
                val defaultBackgroundColor = ContextCompat.getColor(this, R.color.time_filter_button_default_background)
                if (materialButton != null) {
                    materialButton.backgroundTintList = ColorStateList.valueOf(defaultBackgroundColor)
                } else {
                    button.setBackgroundColor(defaultBackgroundColor)
                }
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary_dark))
                button.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun fetchHistoricalData(daysPeriod: String) {
        val symbol = "BTCUSDT"
        var interval: String
        var limit: Int? = null
        var startTime: Long? = null
        val endTime = System.currentTimeMillis()

        when (daysPeriod) {
            "1" -> {
                interval = "5m"
                startTime = endTime - (1 * 24 * 60 * 60 * 1000L)
            }
            "5" -> {
                interval = "30m"
                startTime = endTime - (5 * 24 * 60 * 60 * 1000L)
            }
            "30" -> {
                interval = "4h"
                startTime = endTime - (30 * 24 * 60 * 60 * 1000L)
            }
            "180" -> {
                interval = "1d"
                startTime = endTime - (180 * 24 * 60 * 60 * 1000L)
            }
            "365" -> {
                interval = "1d"
                startTime = endTime - (365 * 24 * 60 * 60 * 1000L)
            }
            "max" -> {
                interval = "1d"
                limit = 1000
            }
            else -> {
                showError("알 수 없는 기간입니다: $daysPeriod")
                return
            }
        }

        ApiClient.instance.getBinanceKlines(
            symbol = symbol,
            interval = interval,
            startTime = startTime,
            endTime = endTime,
            limit = if (daysPeriod == "max") limit else null
        ).enqueue(object : Callback<List<List<Any>>> {
            override fun onResponse(call: Call<List<List<Any>>>, response: Response<List<List<Any>>>) {
                if (response.isSuccessful) {
                    response.body()?.let { klines ->
                        updateCombinedChartWithBinanceData(klines)
                    } ?: showError("과거 시세 데이터가 없습니다 (Binance).")
                } else {
                    showError("과거 시세 API 응답 실패 (Binance): ${response.code()} ${response.message()}")
                }
            }

            override fun onFailure(call: Call<List<List<Any>>>, t: Throwable) {
                showError("과거 시세 네트워크 오류 (Binance): ${t.message}")
                Log.e("MainActivity", "Binance Klines API Call Failed", t)
            }
        })
    }

    private fun updateCombinedChartWithBinanceData(klines: List<List<Any>>) {
        if (klines.isEmpty()) {
            chart.clear()
            chart.invalidate()
            Toast.makeText(this, "해당 기간의 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val candleEntries = ArrayList<CandleEntry>()
        val closePrices = mutableListOf<Double>()
        val timestamps = mutableListOf<Long>()

        for (klineData in klines) {
            try {
                val timestamp = (klineData[0] as Double).toLong()
                val openUsd = (klineData[1] as String).toFloat()
                val highUsd = (klineData[2] as String).toFloat()
                val lowUsd = (klineData[3] as String).toFloat()
                val closeUsd = (klineData[4] as String).toFloat()

                val displayRate = usdToKrwRate ?: 1.0
                val open = (openUsd * displayRate).toFloat()
                val high = (highUsd * displayRate).toFloat()
                val low = (lowUsd * displayRate).toFloat()
                val close = (closeUsd * displayRate).toFloat()

                candleEntries.add(CandleEntry(timestamp.toFloat(), high, low, open, close))
                closePrices.add(close.toDouble())
                timestamps.add(timestamp)

            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing kline data for chart: $klineData", e)
            }
        }

        val candleDataSet = CandleDataSet(candleEntries, "BTC/KRW").apply {
            increasingColor = ContextCompat.getColor(this@MainActivity, R.color.negative_red)
            increasingPaintStyle = Paint.Style.FILL
            decreasingColor = ContextCompat.getColor(this@MainActivity, R.color.chart_blue)
            decreasingPaintStyle = Paint.Style.FILL
            shadowColorSameAsCandle = true
            shadowWidth = 0.7f
            setDrawValues(false)
        }
        val candleData = CandleData(candleDataSet)

        val sma5 = calculateSMA(closePrices, 5)
        val sma20 = calculateSMA(closePrices, 20)

        val sma5Entries = ArrayList<Entry>()
        val sma20Entries = ArrayList<Entry>()

        for (i in sma5.indices) {
            if (!sma5[i].isNaN()) {
                sma5Entries.add(Entry(timestamps[i].toFloat(), sma5[i].toFloat()))
            }
            if (!sma20[i].isNaN()) {
                sma20Entries.add(Entry(timestamps[i].toFloat(), sma20[i].toFloat()))
            }
        }

        val sma5DataSet = LineDataSet(sma5Entries, "SMA 5").apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.sma_5_color)
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
        }

        val sma20DataSet = LineDataSet(sma20Entries, "SMA 20").apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.sma_20_color)
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
        }

        val lineData = LineData(sma5DataSet, sma20DataSet)

        val combinedData = CombinedData()
        combinedData.setData(candleData)
        combinedData.setData(lineData)

        chart.data = combinedData
        chart.invalidate()
    }

    inner class DateAxisValueFormatter : ValueFormatter() {
        private val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return try {
                sdf.format(Date(value.toLong()))
            } catch (e: Exception) {
                value.toString()
            }
        }
    }

    inner class YAxisValueFormatter : ValueFormatter() {
        private val krwPriceFormat = DecimalFormat("₩#,##0")
        private val usdPriceFormat = DecimalFormat("$#,##0")

        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return if (usdToKrwRate != null) {
                krwPriceFormat.format(value)
            } else {
                usdPriceFormat.format(value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updatePriceRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updatePriceRunnable)
    }

    private fun fetchBitcoinPrice() {
        binding.tvBitcoinPrice.text = getString(R.string.loading_price)
        binding.tvPriceChange.text = ""

        ApiClient.instance.getBinance24hrTicker().enqueue(object : Callback<Binance24hrTickerResponse> {
            override fun onResponse(call: Call<Binance24hrTickerResponse>, response: Response<Binance24hrTickerResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        updateUIWithBinance24hrData(it)
                    } ?: showError("현재 가격/변동률 데이터 파싱 오류 (Binance)")
                } else {
                    showError("현재 가격/변동률 API 응답 실패 (Binance): ${response.code()} ${response.message()}")
                }
            }

            override fun onFailure(call: Call<Binance24hrTickerResponse>, t: Throwable) {
                showError("현재 가격/변동률 네트워크 오류 (Binance): ${t.message}")
                Log.e("MainActivity", "Binance 24hr Ticker API Call Failed", t)
            }
        })
    }

    private fun updateUIWithBinance24hrData(data: Binance24hrTickerResponse) {
        try {
            val currentPriceUsd = data.lastPrice.toDouble()
            val absoluteChangeUsd = data.priceChange.toDouble()
            val priceChangePercent = data.priceChangePercent.toDouble()

            val krwPriceFormat = DecimalFormat("'₩',##0")
            val krwChangeFormat = DecimalFormat("'₩',##0")
            val usdPriceInParenthesesFormat = DecimalFormat("'$',##0")
            val usdPriceDefaultFormat = DecimalFormat("'$',##0.00")

            var displayChange: String

            if (usdToKrwRate != null) {
                val currentPriceKrw = currentPriceUsd * usdToKrwRate!!
                val absoluteChangeKrw = absoluteChangeUsd * usdToKrwRate!!

                val krwStr = krwPriceFormat.format(currentPriceKrw)
                val usdStrInParentheses = " (${usdPriceInParenthesesFormat.format(currentPriceUsd)})"

                val spannablePrice = SpannableString(krwStr + usdStrInParentheses)
                val usdPartStartIndex = krwStr.length
                val usdPartEndIndex = krwStr.length + usdStrInParentheses.length

                spannablePrice.setSpan(
                    RelativeSizeSpan(0.7f),
                    usdPartStartIndex,
                    usdPartEndIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannablePrice.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary_dark)),
                    usdPartStartIndex,
                    usdPartEndIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.tvBitcoinPrice.text = spannablePrice

                val formattedChangeKrw = krwChangeFormat.format(absoluteChangeKrw)
                val percentageChangeFormat = DecimalFormat("+#0.00'%';-#0.00'%'")
                val formattedPercentageChange = percentageChangeFormat.format(priceChangePercent)
                displayChange = String.format(Locale.getDefault(), "%s (%s)", formattedChangeKrw, formattedPercentageChange)

            } else {
                binding.tvBitcoinPrice.text = usdPriceDefaultFormat.format(currentPriceUsd)

                val absoluteChangeFormat = DecimalFormat("+#,##0.0;-#,##0.0")
                val percentageChangeFormat = DecimalFormat("+#0.00'%';-#0.00'%'")
                val formattedAbsoluteChangeUsd = absoluteChangeFormat.format(absoluteChangeUsd)
                val formattedPercentageChange = percentageChangeFormat.format(priceChangePercent)
                displayChange = String.format(Locale.getDefault(), "%s (%s)", formattedAbsoluteChangeUsd, formattedPercentageChange)
            }

            binding.tvPriceChange.text = displayChange

            if (priceChangePercent >= 0) {
                binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, R.color.positive_green))
            } else {
                binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, R.color.negative_red))
            }

        } catch (e: NumberFormatException) {
            showError("가격/변동률 데이터 형식 오류 (Binance)")
            Log.e("MainActivity", "Error parsing Binance 24hr ticker data: $data", e)
            binding.tvBitcoinPrice.text = "가격 오류"
            binding.tvPriceChange.text = "변동률 오류"
        } catch (e: Exception) {
            showError("UI 업데이트 중 알 수 없는 오류 발생")
            Log.e("MainActivity", "Unknown error in updateUIWithBinance24hrData", e)
            binding.tvBitcoinPrice.text = "업데이트 오류"
            binding.tvPriceChange.text = "업데이트 오류"
        }
    }


    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (binding.tvBitcoinPrice.text == getString(R.string.loading_price) || binding.tvBitcoinPrice.text.contains("오류")) {
            binding.tvBitcoinPrice.text = "가격 로드 실패"
        }
        if (binding.tvPriceChange.text.isBlank() || binding.tvPriceChange.text.contains("오류")) {
            binding.tvPriceChange.text = ""
        }
        Log.e("MainActivity", "Error: $message")
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("btc_price_predictor_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadFeatureColumns(): List<String> {
        val inputStream = assets.open("feature_columns.json")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val gson = Gson()
        return gson.fromJson(reader, object : TypeToken<List<String>>() {}.type)
    }

    private fun loadScalerParams(): ScalerParams? {
        return try {
            val inputStream = assets.open("scaler_params.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val gson = Gson()
            gson.fromJson(reader, ScalerParams::class.java)
        } catch (e: Exception) {
            Log.e("MainActivity_TFLite", "Error loading scaler_params.json", e)
            null
        }
    }

    private fun fetchDataForPrediction(onPredictionReady: (Double?) -> Unit) {
        val symbol = "BTCUSDT"
        val interval = "1d"
        val limit = LOOK_BACK + 25

        ApiClient.instance.getBinanceKlines(
            symbol = symbol,
            interval = interval,
            limit = limit
        ).enqueue(object : Callback<List<List<Any>>> {
            override fun onResponse(call: Call<List<List<Any>>>, response: Response<List<List<Any>>>) {
                if (response.isSuccessful) {
                    response.body()?.let { klines ->
                        if (klines.size >= LOOK_BACK + 19) {
                            processDataForPrediction(klines, onPredictionReady)
                        } else {
                            showError("예측을 위한 충분한 데이터가 없습니다. (가져온 데이터 수: ${klines.size})")
                            onPredictionReady(null)
                        }
                    } ?: run {
                        showError("예측용 데이터가 없습니다 (Binance).")
                        onPredictionReady(null)
                    }
                } else {
                    showError("예측용 API 응답 실패 (Binance): ${response.code()} ${response.message()}")
                    onPredictionReady(null)
                }
            }

            override fun onFailure(call: Call<List<List<Any>>>, t: Throwable) {
                showError("예측용 네트워크 오류 (Binance): ${t.message}")
                onPredictionReady(null)
            }
        })
    }

    private fun processDataForPrediction(klines: List<List<Any>>, onPredictionReady: (Double?) -> Unit) {
        if (scalerParams == null || featureColumns.isEmpty() || tflite == null) {
            showError("스케일러, 피처 정보 또는 모델이 준비되지 않았습니다.")
            onPredictionReady(null)
            return
        }

        Log.d("PredictionLogic", "Starting data processing for prediction. Klines received: ${klines.size}")

        val parsedKlines = mutableListOf<CandleData>()
        try {
            for (kline in klines) {
                parsedKlines.add(
                    CandleData(
                        timestamp = (kline[0] as Double).toLong(),
                        open = (kline[1] as String).toDouble(),
                        high = (kline[2] as String).toDouble(),
                        low = (kline[3] as String).toDouble(),
                        close = (kline[4] as String).toDouble(),
                        volume = (kline[5] as String).toDouble()
                    )
                )
            }
            Log.d("PredictionLogic", "Step 1: Parsed ${parsedKlines.size} klines.")
        } catch (e: Exception) {
            Log.e("PredictionLogic", "Error parsing klines", e)
            showError("K-line 데이터 파싱 오류")
            onPredictionReady(null)
            return
        }

        if (parsedKlines.size < LOOK_BACK + 19) {
            Log.e("PredictionLogic", "Not enough parsed klines for feature calculation: ${parsedKlines.size}")
            onPredictionReady(null)
            return
        }

        val openPrices = parsedKlines.map { it.open }
        val highPrices = parsedKlines.map { it.high }
        val lowPrices = parsedKlines.map { it.low }
        val closePrices = parsedKlines.map { it.close }
        val volumes = parsedKlines.map { it.volume }

        val sma5 = calculateSMA(closePrices, 5)
        val sma10 = calculateSMA(closePrices, 10)
        val sma20 = calculateSMA(closePrices, 20)
        val ema5 = calculateEMA(closePrices, 5)
        val ema10 = calculateEMA(closePrices, 10)
        val ema20 = calculateEMA(closePrices, 20)
        val atr14 = calculateATR(highPrices, lowPrices, closePrices, 14)
        val rsi14 = calculateRSI(closePrices, 14)

        val lastHalvingDate = LocalDate.of(2024, 4, 20)
        val daysSinceHalvingList = parsedKlines.map {
            val openDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.DAYS.between(lastHalvingDate, openDate).toDouble()
        }
        Log.d("PredictionLogic", "Step 2: Technical indicators calculated.")

        val allFeaturesData = mutableListOf<Map<String, Double>>()
        for (i in parsedKlines.indices) {
            val featuresMap = mutableMapOf<String, Double>()
            featuresMap["Open"] = openPrices[i]
            featuresMap["High"] = highPrices[i]
            featuresMap["Low"] = lowPrices[i]
            featuresMap["Close"] = closePrices[i]
            featuresMap["Volume"] = volumes[i]
            featuresMap["SMA_5"] = sma5[i]
            featuresMap["SMA_10"] = sma10[i]
            featuresMap["SMA_20"] = sma20[i]
            featuresMap["EMA_5"] = ema5[i]
            featuresMap["EMA_10"] = ema10[i]
            featuresMap["EMA_20"] = ema20[i]
            featuresMap["ATR_14"] = atr14[i]
            featuresMap["RSI_14"] = rsi14[i]
            featuresMap["Days_Since_Last_Halving"] = daysSinceHalvingList[i]
            allFeaturesData.add(featuresMap)
        }

        if (allFeaturesData.size < LOOK_BACK) {
            Log.e("PredictionLogic", "Not enough data after feature calculation for LOOK_BACK sequence.")
            onPredictionReady(null)
            return
        }
        val finalSequenceRaw = allFeaturesData.takeLast(LOOK_BACK)
        Log.d("PredictionLogic", "Step 3: Final sequence of ${finalSequenceRaw.size} prepared.")

        for (dayData in finalSequenceRaw) {
            for (featureName in featureColumns) {
                if (dayData[featureName]?.isNaN() == true) {
                    Log.e("PredictionLogic", "NaN found in final sequence for feature: $featureName. Aborting.")
                    showError("데이터 준비 중 오류 발생 (NaN 값 감지)")
                    onPredictionReady(null)
                    return
                }
            }
        }

        val scaledInput2D = Array(LOOK_BACK) { FloatArray(featureColumns.size) }
        for (i in 0 until LOOK_BACK) {
            val currentDayFeatures = finalSequenceRaw[i]
            for (j in featureColumns.indices) {
                val featureName = featureColumns[j]
                val rawValue = currentDayFeatures[featureName]
                    ?: run {
                        Log.e("PredictionLogic", "Feature $featureName not found in prepared data for day $i")
                        onPredictionReady(null)
                        return
                    }

                val min = scalerParams!!.feature_data_min[j]
                val max = scalerParams!!.feature_data_max[j]
                val range = max - min
                scaledInput2D[i][j] = if (range != 0.0) {
                    ((rawValue - min) / range).toFloat()
                } else {
                    0.0f
                }
            }
        }
        Log.d("PredictionLogic", "Step 4: Data scaled.")

        val inputBuffer = ByteBuffer.allocateDirect(1 * LOOK_BACK * featureColumns.size * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until LOOK_BACK) {
            for (j in 0 until featureColumns.size) {
                inputBuffer.putFloat(scaledInput2D[i][j])
            }
        }
        Log.d("PredictionLogic", "Step 5: Input ByteBuffer prepared.")

        val outputBuffer = ByteBuffer.allocateDirect(1 * 1 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        try {
            tflite!!.run(inputBuffer, outputBuffer)
            Log.d("PredictionLogic", "Step 6: TFLite model run completed.")
        } catch (e: Exception) {
            Log.e("PredictionLogic", "Error running TFLite model", e)
            showError("AI 모델 추론 중 오류 발생")
            onPredictionReady(null)
            return
        }

        outputBuffer.rewind()
        val scaledPrediction = outputBuffer.float
        val targetMin = scalerParams!!.target_close_data_min
        val targetMax = scalerParams!!.target_close_data_max
        val targetRange = targetMax - targetMin
        val predictedPriceUsd = if (targetRange != 0.0) {
            scaledPrediction * targetRange + targetMin
        } else {
            targetMin
        }
        Log.d("PredictionLogic", "Step 7: Prediction inverse-scaled. Predicted Price USD: $predictedPriceUsd")

        onPredictionReady(predictedPriceUsd)
    }


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
                val currentPrice = data[i]
                result[i] = (currentPrice - result[i-1]) * multiplier + result[i-1]
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
            val hpc = abs(highs[i] - closes[i-1])
            val lpc = abs(lows[i] - closes[i-1])
            trList[i] = maxOf(hl, hpc, lpc)
        }

        val atrList = MutableList(highs.size) { Double.NaN }
        if (trList.size < window) return atrList

        atrList[window - 1] = trList.subList(0, window).average()

        for (i in window until highs.size) {
            atrList[i] = (atrList[i-1] * (window - 1) + trList[i]) / window
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
            val difference = data[i] - data[i-1]
            if (difference > 0) {
                gains.add(difference)
                losses.add(0.0)
            } else {
                gains.add(0.0)
                losses.add(abs(difference))
            }
        }

        if (gains.isEmpty() && losses.isEmpty() && window > 0) {
            for(i in window until data.size) result[i] = 50.0
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
            val difference = data[i] - data[i-1]
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

    // 이전에 있던 fetchExchangeRate, updateCurrentPriceDisplayAfterPrediction 함수는 여기에 포함되어 있지 않지만,
    // 실제 코드에서는 그대로 존재해야 합니다.
    private fun fetchExchangeRate() {
        val todayDate = KeximApiClient.getTodayDateString()
        val apiKey = KeximApiClient.getApiKey()

        if (apiKey == "YOUR_KEXIM_AUTH_KEY" || apiKey.isBlank()) {
            Log.e("KeximExchangeRate", "API Key is not set in KeximApiClient.")
            showError("환율 API 키가 설정되지 않았습니다.")
            fetchBitcoinPrice()
            return
        }

        KeximApiClient.instance.getExchangeRates(apiKey, todayDate)
            .enqueue(object : Callback<List<KeximExchangeRate>> {
                override fun onResponse(
                    call: Call<List<KeximExchangeRate>>,
                    response: Response<List<KeximExchangeRate>>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let { rates ->
                            val usdRateInfo = rates.find { it.currencyUnit == "USD" }
                            if (usdRateInfo != null && usdRateInfo.result == 1) {
                                try {
                                    usdToKrwRate = usdRateInfo.dealBaseRate.replace(",", "").toDouble()
                                    Log.d("KeximExchangeRate", "USD to KRW rate (deal_bas_r): $usdToKrwRate")
                                } catch (e: NumberFormatException) {
                                    Log.e("KeximExchangeRate", "Error parsing deal_bas_r for USD", e)
                                    showError("USD 환율 값 형식 오류")
                                    usdToKrwRate = null
                                }
                            } else {
                                val errorMsg = usdRateInfo?.let { "USD 환율 정보 Result: ${it.result} (1이 아니면 오류 또는 데이터 없음)" } ?: "USD 환율 정보를 찾을 수 없음 (응답 목록에 USD 없음 또는 result 코드 확인 필요)"
                                Log.e("KeximExchangeRate", errorMsg)
                                showError(errorMsg)
                                usdToKrwRate = null
                            }
                        } ?: run {
                            showError("환율 응답 데이터 없음")
                            usdToKrwRate = null
                        }
                    } else {
                        Log.e("KeximExchangeRate", "Failed to fetch KEXIM exchange rate: ${response.code()} ${response.message()}")
                        showError("환율 정보 로드 실패: ${response.code()}")
                        usdToKrwRate = null
                    }
                    fetchBitcoinPrice()
                    fetchHistoricalData(currentDaysPeriod)
                }

                override fun onFailure(call: Call<List<KeximExchangeRate>>, t: Throwable) {
                    Log.e("KeximExchangeRate", "Error fetching KEXIM exchange rate", t)
                    showError("환율 정보 네트워크 오류")
                    usdToKrwRate = null
                    fetchBitcoinPrice()
                    fetchHistoricalData(currentDaysPeriod)
                }
            })
    }

    private fun updateCurrentPriceDisplayAfterPrediction() {
        fetchBitcoinPrice()
    }
}