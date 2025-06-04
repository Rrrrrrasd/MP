package com.example.mp_btc // 적절한 패키지명으로 변경

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
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

    private lateinit var lineChart: LineChart

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

        lineChart = binding.priceLineChart
        setupChart()
        setupButtonListeners()

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
            fetchHistoricalData("1")
        }
        fetchExchangeRate() // 앱 시작 시 환율 정보 가져오기 (fetchBitcoinPrice는 환율 로드 후 호출됨)

        binding.tvBottomPredict.setOnClickListener {
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

    private fun fetchExchangeRate() {
        val todayDate = KeximApiClient.getTodayDateString()
        val apiKey = KeximApiClient.getApiKey()

        if (apiKey == "YOUR_KEXIM_AUTH_KEY" || apiKey.isBlank()) { // 실제 키로 변경했는지 확인
            Log.e("KeximExchangeRate", "API Key is not set in KeximApiClient.")
            showError("환율 API 키가 설정되지 않았습니다.")
            // API 키가 없어도 비트코인 가격은 USD로 표시되도록 fetchBitcoinPrice() 호출
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
                                    usdToKrwRate = null // 파싱 실패 시 null로 설정
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
                    // 환율 정보 로드 시도 후 (성공이든 실패든) 비트코인 가격 로드
                    fetchBitcoinPrice()
                }

                override fun onFailure(call: Call<List<KeximExchangeRate>>, t: Throwable) {
                    Log.e("KeximExchangeRate", "Error fetching KEXIM exchange rate", t)
                    showError("환율 정보 네트워크 오류")
                    usdToKrwRate = null
                    // 환율 정보 로드 실패 후에도 비트코인 가격 로드
                    fetchBitcoinPrice()
                }
            })
    }


    private fun updateCurrentPriceDisplayAfterPrediction() {
        fetchBitcoinPrice()
    }

    private fun setupChart() {
        // ... (기존 코드와 동일)
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setDrawGridBackground(false)

        // --- X축 설정 ---
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = DateAxisValueFormatter() // 기존 포맷터 유지
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false) // 그리드 라인 제거 (이전 설정 유지)

        // X축 레이블 스타일 변경
        xAxis.textColor = Color.WHITE // X축 텍스트 색상을
        xAxis.textSize = 12f          // X축 텍스트 크기를
        xAxis.setDrawAxisLine(true) // X축 선을 표시
        xAxis.axisLineColor = Color.WHITE // X축 선 색상
        xAxis.setLabelCount(5, false);

        // --- 왼쪽 Y축 설정 ---
        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.valueFormatter = YAxisValueFormatter() // 기존 포맷터 유지
        yAxisLeft.setDrawGridLines(false) // 그리드 라인 제거 (이전 설정 유지)

        // Y축 레이블 스타일 변경
        yAxisLeft.textColor = Color.WHITE // Y축 텍스트 색상을
        yAxisLeft.textSize = 12f          // Y축 텍스트 크기를
        yAxisLeft.setDrawAxisLine(true) // Y축 선을 표시
        yAxisLeft.axisLineColor = Color.WHITE // Y축 선 색상

        //yAxisLeft.granularity = ... // Y축 간격 조절
        // yAxisLeft.axisMinimum = ... // Y축 최소값 설정


        // --- 오른쪽 Y축 사용 안 함 ---
        lineChart.axisRight.isEnabled = false

        // --- 범례(Legend) 설정 ---
        val legend = lineChart.legend
        legend.isEnabled = false
    }

    private fun setupButtonListeners() {
        // ... (기존 코드와 동일)
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
                fetchHistoricalData(daysValue)
                updateButtonSelectionUI(button) // UI 업데이트 함수 호출
            }
        }
    }

    private fun updateButtonSelectionUI(selectedBtn: Button) {
        // ... (기존 코드와 동일)
        val allButtons = listOf(
            binding.btn1Day, binding.btn5Day, binding.btn1Month,
            binding.btn6Months, binding.btn1Year, binding.btnAll
        )

        allButtons.forEach { button ->
            val materialButton = button as? com.google.android.material.button.MaterialButton

            if (button == selectedBtn) {
                // --- 선택된 버튼 스타일 ---
                // 배경색: 연한 파란색 (colors.xml의 time_filter_button_selected_background 값)
                val selectedBackgroundColor = ContextCompat.getColor(this, R.color.time_filter_button_selected_background)
                if (materialButton != null) {
                    materialButton.backgroundTintList = ColorStateList.valueOf(selectedBackgroundColor)
                } else {
                    button.setBackgroundColor(selectedBackgroundColor)
                }

                // 텍스트 색상: 검은색
                button.setTextColor(Color.BLACK) // android.graphics.Color.BLACK 사용
                // 또는 colors.xml에 <color name="black">#000000</color> 정의 후
                // button.setTextColor(ContextCompat.getColor(this, R.color.black)) 사용 가능

                // 텍스트 굵기: 굵게 (선택 사항, 필요 없다면 이 줄 삭제 또는 주석 처리)
                button.setTypeface(null, Typeface.BOLD)

            } else {
                // --- 선택되지 않은 버튼 스타일 ---
                // 배경색: 앱 기본 배경색 (colors.xml의 time_filter_button_default_background 값)
                val defaultBackgroundColor = ContextCompat.getColor(this, R.color.time_filter_button_default_background)
                if (materialButton != null) {
                    materialButton.backgroundTintList = ColorStateList.valueOf(defaultBackgroundColor)
                } else {
                    button.setBackgroundColor(defaultBackgroundColor)
                }

                // 텍스트 색상: 흰색 (colors.xml의 text_primary_dark 값)
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary_dark))

                // 텍스트 굵기: 보통 (선택 사항, 필요 없다면 이 줄 삭제 또는 주석 처리)
                button.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun fetchHistoricalData(daysPeriod: String) {
        // ... (기존 코드와 동일)
        val symbol = "BTCUSDT"
        var interval: String
        var limit: Int? = null
        var startTime: Long? = null
        val endTime = System.currentTimeMillis() // 항상 현재 시간까지 데이터 요청

        when (daysPeriod) {
            "1" -> { // 1일: 5분봉, 288개 (24 * 12)
                interval = "5m"
                startTime = endTime - (1 * 24 * 60 * 60 * 1000L)
                // limit = 288 // startTime, endTime 사용 시 limit은 자동 계산되거나 최대 1000개
            }
            "5" -> { // 5일: 30분봉, 240개 (5 * 24 * 2)
                interval = "30m"
                startTime = endTime - (5 * 24 * 60 * 60 * 1000L)
            }
            "30" -> { // 1개월(30일): 4시간봉, 180개 (30 * 6)
                interval = "4h"
                startTime = endTime - (30 * 24 * 60 * 60 * 1000L)
            }
            "180" -> { // 6개월(180일): 1일봉, 180개
                interval = "1d"
                startTime = endTime - (180 * 24 * 60 * 60 * 1000L)
            }
            "365" -> { // 1년(365일): 1일봉, 365개
                interval = "1d"
                startTime = endTime - (365 * 24 * 60 * 60 * 1000L)
            }
            "max" -> { // 최대: 1일봉, 1000개 (바이낸스 kline API 최대 limit)
                interval = "1d"
                limit = 1000 // startTime 없이 가장 최근 1000일치 데이터
            }
            else -> {
                showError("알 수 없는 기간입니다: $daysPeriod")
                return
            }
        }

        ApiClient.instance.getBinanceKlines(
            symbol = symbol,
            interval = interval,
            startTime = startTime, // "max"가 아닐 때만 startTime 사용
            endTime = endTime,     // "max"가 아닐 때도 endTime은 현재 시간으로 고정 가능
            limit = if (daysPeriod == "max") limit else null // "max"일 때만 limit 사용, 나머지는 startTime/endTime으로 범위 지정
        ).enqueue(object : Callback<List<List<Any>>> {
            override fun onResponse(call: Call<List<List<Any>>>, response: Response<List<List<Any>>>) {
                if (response.isSuccessful) {
                    response.body()?.let { klines ->
                        updateLineChartWithBinanceData(klines) // 새로운 차트 업데이트 함수
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

    private fun updateLineChartWithBinanceData(klines: List<List<Any>>) {
        // ... (기존 코드와 동일)
        val entries = ArrayList<Entry>()
        if (klines.isEmpty()) {
            lineChart.clear()
            lineChart.data?.clearValues()
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            Toast.makeText(this, "해당 기간의 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        var minPriceKrw = Float.MAX_VALUE
        var maxPriceKrw = Float.MIN_VALUE

        for (klineData in klines) {
            try {
                val timestamp = (klineData[0] as Double).toLong()
                val closePriceUsd = (klineData[4] as String).toFloat() // USD 가격

                // 환율 정보가 있을 경우 KRW로 변환, 없으면 USD 그대로 사용 (또는 오류 처리)
                val displayPrice = if (usdToKrwRate != null) {
                    val priceKrw = (closePriceUsd * usdToKrwRate!!).toFloat()
                    minPriceKrw = minOf(minPriceKrw, priceKrw)
                    maxPriceKrw = maxOf(maxPriceKrw, priceKrw)
                    priceKrw
                } else {
                    // 환율 정보 없을 시 차트 Y축을 어떻게 할지 결정 필요
                    // 1. USD로 그대로 표시 (YAxisValueFormatter도 USD 유지)
                    // 2. 차트 표시 안 함 또는 오류 메시지
                    // 여기서는 USD로 그대로 표시한다고 가정 (YAxisValueFormatter는 아래에서 KRW용으로 변경할 것이므로,
                    // 이 경우 YAxisValueFormatter도 동적으로 변경하거나, 환율 없을 시 차트 업데이트를 막는 것이 나을 수 있음)
                    // 우선은 KRW 변환을 시도하고, 실패 시 USD를 넣도록 하되, 포맷터는 KRW 기준으로 가정합니다.
                    // 더 나은 방법은 환율이 있을 때만 차트를 KRW로 그리고, 없을 땐 USD로 그리거나 안 그리는 것입니다.
                    // 여기서는 환율이 있다고 가정하고 KRW로 변환합니다. 실제 사용 시 usdToKrwRate null 체크 강화 필요.
                    (closePriceUsd * (usdToKrwRate ?: 1.0)).toFloat() // 임시로 환율 없으면 1.0 곱함 (실제로는 다른 처리 필요)
                }
                entries.add(Entry(timestamp.toFloat(), displayPrice))
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing kline data for chart: $klineData", e)
            }
        }

        entries.sortBy { it.x }

        // Y축 최소/최대값 자동 조정을 위한 로직 (선택 사항, 데이터에 따라 보기 좋게)
        if (entries.isNotEmpty() && usdToKrwRate != null) { // 환율이 있을 때만 KRW 기준으로 조정
            // 약간의 여백을 줌
            val padding = (maxPriceKrw - minPriceKrw) * 0.05f
            lineChart.axisLeft.axisMinimum = if (minPriceKrw != Float.MAX_VALUE) minPriceKrw - padding else lineChart.axisLeft.axisMinimum
            lineChart.axisLeft.axisMaximum = if (maxPriceKrw != Float.MIN_VALUE) maxPriceKrw + padding else lineChart.axisLeft.axisMaximum
        }


        val dataSet: LineDataSet
        if (lineChart.data != null && lineChart.data.dataSetCount > 0) {
            dataSet = lineChart.data.getDataSetByIndex(0) as LineDataSet
            dataSet.values = entries
            dataSet.label = if (usdToKrwRate != null) "Bitcoin Price (KRW)" else "Bitcoin Price (USD)" // 레이블 변경
            lineChart.data.notifyDataChanged()
            lineChart.notifyDataSetChanged()
        } else {
            dataSet = LineDataSet(entries, if (usdToKrwRate != null) "Bitcoin Price (KRW)" else "Bitcoin Price (USD)")
            dataSet.color = ContextCompat.getColor(this, R.color.positive_green)
            dataSet.valueTextColor = ContextCompat.getColor(this, R.color.text_primary_dark)
            dataSet.setDrawCircles(false)
            dataSet.setDrawValues(false)
            dataSet.lineWidth = 2f
            val lineData = LineData(dataSet)
            lineChart.data = lineData
        }
        lineChart.invalidate()
    }

    inner class DateAxisValueFormatter : ValueFormatter() {
        // ... (기존 코드와 동일)
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
        // KRW용 포맷터 (환율 정보가 있을 때 사용)
        private val krwPriceFormat = DecimalFormat("₩#,##0")
        // USD용 포맷터 (환율 정보가 없을 때의 대비용 또는 기본값)
        private val usdPriceFormat = DecimalFormat("$#,##0")

        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return if (usdToKrwRate != null) { // 환율 정보가 있으면 KRW로 포맷
                krwPriceFormat.format(value)
            } else { // 없으면 USD로 포맷 (또는 다른 기본값)
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
                        updateUIWithBinance24hrData(it) // 수정된 함수 호출
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

    // updateUIWithBinance24hrData 함수 수정 (환율 적용)
    private fun updateUIWithBinance24hrData(data: Binance24hrTickerResponse) {
        try {
            val currentPriceUsd = data.lastPrice.toDouble()
            val absoluteChangeUsd = data.priceChange.toDouble()
            val priceChangePercent = data.priceChangePercent.toDouble()

            // KRW 표시를 위한 포맷 (소수점 없음, 쉼표, 원화 기호)
            val krwPriceFormat = DecimalFormat("'₩',##0")
            val krwChangeFormat = DecimalFormat("'₩',##0")

            // USD 단독 표시 또는 괄호 안 USD 표시(정수)를 위한 포맷
            val usdPriceInParenthesesFormat = DecimalFormat("'$',##0") // 괄호 안 USD는 소수점 없이
            val usdPriceDefaultFormat = DecimalFormat("'$',##0.00") // 환율 없을 때 기본 USD 포맷

            var displayChange: String // 변동률 표시는 Spannable 없이 String으로 처리

            if (usdToKrwRate != null) {
                val currentPriceKrw = currentPriceUsd * usdToKrwRate!!
                val absoluteChangeKrw = absoluteChangeUsd * usdToKrwRate!!

                // SpannableString으로 가격 표시 스타일링
                val krwStr = krwPriceFormat.format(currentPriceKrw)
                val usdStrInParentheses = " (${usdPriceInParenthesesFormat.format(currentPriceUsd)})" // 괄호, 공백 포함

                val spannablePrice = SpannableString(krwStr + usdStrInParentheses)

                // USD 부분 (괄호 포함) 스타일 적용: 작은 글꼴, 다른 색상
                val usdPartStartIndex = krwStr.length
                val usdPartEndIndex = krwStr.length + usdStrInParentheses.length

                // 글꼴 크기 작게 (예: 기본 크기의 70%)
                spannablePrice.setSpan(
                    RelativeSizeSpan(0.7f),
                    usdPartStartIndex,
                    usdPartEndIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // 글꼴 색상 변경 (예: text_secondary_dark 색상 사용)
                // colors.xml에 정의된 색상을 사용합니다. 예: <color name="text_secondary_dark">#B3FFFFFF</color>
                spannablePrice.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary_dark)),
                    usdPartStartIndex,
                    usdPartEndIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.tvBitcoinPrice.text = spannablePrice

                // 변동률 표시 (원화 기준)
                val formattedChangeKrw = krwChangeFormat.format(absoluteChangeKrw)
                val percentageChangeFormat = DecimalFormat("+#0.00'%';-#0.00'%'")
                val formattedPercentageChange = percentageChangeFormat.format(priceChangePercent)
                displayChange = String.format(Locale.getDefault(), "%s (%s)", formattedChangeKrw, formattedPercentageChange)

            } else {
                // 환율 정보 없을 시: USD만 기본 포맷으로 표시
                binding.tvBitcoinPrice.text = usdPriceDefaultFormat.format(currentPriceUsd)

                // 변동률 표시 (USD 기준)
                val absoluteChangeFormat = DecimalFormat("+#,##0.0;-#,##0.0") // USD 변동폭 포맷
                val percentageChangeFormat = DecimalFormat("+#0.00'%';-#0.00'%'")
                val formattedAbsoluteChangeUsd = absoluteChangeFormat.format(absoluteChangeUsd)
                val formattedPercentageChange = percentageChangeFormat.format(priceChangePercent)
                displayChange = String.format(Locale.getDefault(), "%s (%s)", formattedAbsoluteChangeUsd, formattedPercentageChange)
            }

            // 변동률 텍스트 설정
            binding.tvPriceChange.text = displayChange

            // 변동률에 따른 텍스트 색상 변경
            if (priceChangePercent >= 0) {
                binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, R.color.positive_green))
            } else {
                binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, R.color.negative_red))
            }

        } catch (e: NumberFormatException) {
            showError("가격/변동률 데이터 형식 오류 (Binance)")
            Log.e("MainActivity", "Error parsing Binance 24hr ticker data: $data", e)
            binding.tvBitcoinPrice.text = "가격 오류" // 환율 로드 실패 시 기본 가격 표시도 오류일 수 있으므로 간결하게
            binding.tvPriceChange.text = "변동률 오류"
        } catch (e: Exception) {
            showError("UI 업데이트 중 알 수 없는 오류 발생")
            Log.e("MainActivity", "Unknown error in updateUIWithBinance24hrData", e)
            binding.tvBitcoinPrice.text = "업데이트 오류"
            binding.tvPriceChange.text = "업데이트 오류"
        }
    }


    private fun showError(message: String) {
        // ... (기존 코드와 동일)
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
        // ... (기존 코드와 동일)
        val fileDescriptor = assets.openFd("btc_price_predictor_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadFeatureColumns(): List<String> {
        // ... (기존 코드와 동일)
        val inputStream = assets.open("feature_columns.json")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val gson = Gson()
        return gson.fromJson(reader, object : TypeToken<List<String>>() {}.type)
    }

    private fun loadScalerParams(): ScalerParams? {
        // ... (기존 코드와 동일)
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

    // fetchDataForPrediction 콜백 시그니처 변경 (String -> Double?)
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
                            onPredictionReady(null) // 실패 시 null 전달
                        }
                    } ?: run {
                        showError("예측용 데이터가 없습니다 (Binance).")
                        onPredictionReady(null) // 실패 시 null 전달
                    }
                } else {
                    showError("예측용 API 응답 실패 (Binance): ${response.code()} ${response.message()}")
                    onPredictionReady(null) // 실패 시 null 전달
                }
            }

            override fun onFailure(call: Call<List<List<Any>>>, t: Throwable) {
                showError("예측용 네트워크 오류 (Binance): ${t.message}")
                onPredictionReady(null) // 실패 시 null 전달
            }
        })
    }

    // processDataForPrediction 콜백 시그니처 변경 (String -> Double?) 및 반환값 수정
    private fun processDataForPrediction(klines: List<List<Any>>, onPredictionReady: (Double?) -> Unit) {
        if (scalerParams == null || featureColumns.isEmpty() || tflite == null) {
            showError("스케일러, 피처 정보 또는 모델이 준비되지 않았습니다.")
            onPredictionReady(null) // 실패 시 null 전달
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
                    ?: run { // 해당 키가 없을 경우의 처리
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
        val predictedPriceUsd = if (targetRange != 0.0) { // USD 단위 예측값
            scaledPrediction * targetRange + targetMin
        } else {
            targetMin
        }
        Log.d("PredictionLogic", "Step 7: Prediction inverse-scaled. Predicted Price USD: $predictedPriceUsd")

        onPredictionReady(predictedPriceUsd) // Double? 타입의 USD 예측값 전달
    }


    fun calculateSMA(data: List<Double>, window: Int): List<Double> {
        // ... (기존 코드와 동일)
        val result = MutableList(data.size) { Double.NaN } // 초기값을 NaN으로 설정
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
        // ... (이전 답변에서 수정된 로직 적용)
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
                // result[i-1]은 이전 단계에서 값이 할당되었으므로 NaN이 아님 (또는 평균으로 채워짐)
                result[i] = (currentPrice - result[i-1]) * multiplier + result[i-1]
            }
        }
        Log.d("TechIndicator", "EMA($window) calculated. Example last value: ${result.lastOrNull()}")
        return result
    }


    fun calculateATR(highs: List<Double>, lows: List<Double>, closes: List<Double>, window: Int): List<Double> {
        // ... (기존 코드와 동일)
        val trList = MutableList(highs.size) { 0.0 }
        if (highs.isEmpty()) return MutableList(highs.size) { Double.NaN }

        // Calculate True Range (TR)
        trList[0] = highs[0] - lows[0] // First TR
        for (i in 1 until highs.size) {
            val hl = highs[i] - lows[i]
            val hpc = abs(highs[i] - closes[i-1])
            val lpc = abs(lows[i] - closes[i-1])
            trList[i] = maxOf(hl, hpc, lpc)
        }

        // Calculate ATR (smoothed average of TR)
        // Python `ta` 라이브러리의 ATR은 Wilder's smoothing을 사용 (EMA와 유사한 방식)
        val atrList = MutableList(highs.size) { Double.NaN }
        if (trList.size < window) return atrList // 데이터 부족

        // 첫 ATR은 window 기간 동안의 TR의 단순 평균
        atrList[window - 1] = trList.subList(0, window).average()

        // 이후 ATR은 Wilder's smoothing 적용
        for (i in window until highs.size) {
            atrList[i] = (atrList[i-1] * (window - 1) + trList[i]) / window
        }
        Log.d("TechIndicator", "ATR($window) calculated. Example last value: ${atrList.lastOrNull()}")
        return atrList
    }


    fun calculateRSI(data: List<Double>, window: Int): List<Double> {
        // ... (기존 코드와 동일)
        val result = MutableList(data.size) { Double.NaN }
        if (window <= 0 || data.size <= window) return result // RSI는 최소 window+1개의 데이터 필요

        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()

        // 첫 window 기간 동안의 평균 상승폭과 하락폭 계산
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

        if (gains.isEmpty() && losses.isEmpty() && window > 0) { // 모든 가격이 동일한 극단적 경우
            for(i in window until data.size) result[i] = 50.0 // 중립 RSI
            return result
        }


        var avgGain = if (gains.isNotEmpty()) gains.average() else 0.0
        var avgLoss = if (losses.isNotEmpty()) losses.average() else 0.0

        if (avgLoss == 0.0) {
            result[window] = 100.0 // 계속 상승한 경우
        } else {
            val rs = avgGain / avgLoss
            result[window] = 100.0 - (100.0 / (1.0 + rs))
        }


        // 이후 RSI 값 계산 (지수 이동 평균 사용과 유사)
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
}