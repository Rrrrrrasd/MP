package com.example.mp_btc // 적절한 패키지명으로 변경

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mp_btc.databinding.ActivityMainBinding
import com.example.mp_btc.model.Binance24hrTickerResponse
import com.example.mp_btc.model.BinanceTickerResponse
import com.example.mp_btc.model.MarketChartResponse
import com.example.mp_btc.network.ApiClient
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // View Binding
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis: Long = 3000000 // 30분마다 현재 가격 업데이트 - API 요청제한문제


    private lateinit var lineChart: LineChart
    //private var selectedButton: Button? = null // 현재 선택된 버튼을 추적

    private val updatePriceRunnable = object : Runnable {
        override fun run() {
            fetchBitcoinPrice() // 현재 가격 가져오기
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

        binding.btn1Day.post {
            // 초기 선택: 1일 버튼을 선택된 상태로 만들고 데이터 로드
            updateButtonSelectionUI(binding.btn1Day)
            fetchHistoricalData("1")
        }
        fetchBitcoinPrice()
    }

    private fun setupChart() {
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

    /**
     * 모든 기간 버튼의 UI 상태를 업데이트합니다.
     * 선택된 버튼만 활성화된 스타일로, 나머지는 기본 스타일로 설정합니다.
     */
    private fun updateButtonSelectionUI(selectedBtn: Button) {
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

    // ... (fetchHistoricalData, updateLineChart, ValueFormatters, onResume, onPause, fetchBitcoinPrice, updateUI, showError 함수는 이전과 거의 동일하게 유지) ...
    // 아래는 이전 함수들을 간략히 표시한 것이며, 실제로는 이전 답변의 내용을 사용하시면 됩니다.

    private fun fetchHistoricalData(daysPeriod: String) {
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
        val entries = ArrayList<Entry>()
        if (klines.isEmpty()) {
            lineChart.clear()
            lineChart.data?.clearValues()
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            Toast.makeText(this, "해당 기간의 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        for (klineData in klines) {
            try {
                // klineData 구조: [0:Open time, 1:Open, 2:High, 3:Low, 4:Close, 5:Volume, ...]
                val timestamp = (klineData[0] as Double).toLong() // Open time (타임스탬프 ms)
                val closePrice = (klineData[4] as String).toFloat() // Close price
                entries.add(Entry(timestamp.toFloat(), closePrice))
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing kline data: $klineData", e)
                // 파싱 오류가 있는 데이터는 건너뛸 수 있습니다.
            }
        }

        // entries를 시간순으로 정렬 (API가 정렬해서 줄 것이지만, 안전을 위해)
        entries.sortBy { it.x }

        val dataSet: LineDataSet
        if (lineChart.data != null && lineChart.data.dataSetCount > 0) {
            dataSet = lineChart.data.getDataSetByIndex(0) as LineDataSet
            dataSet.values = entries
            dataSet.label = "Bitcoin Price (USD)" // 범례 레이블
            lineChart.data.notifyDataChanged()
            lineChart.notifyDataSetChanged()
        } else {
            dataSet = LineDataSet(entries, "Bitcoin Price (USD)")
            dataSet.color = ContextCompat.getColor(this, R.color.positive_green)
            dataSet.valueTextColor = ContextCompat.getColor(this, R.color.text_primary_dark)
            dataSet.setDrawCircles(false)
            dataSet.setDrawValues(false)
            dataSet.lineWidth = 2f
            val lineData = LineData(dataSet)
            lineChart.data = lineData
        }
        lineChart.invalidate() // 차트 갱신
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
        private val priceFormat = DecimalFormat("$#,##0")
        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return priceFormat.format(value)
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
        binding.tvPriceChange.text = "" // 변동률도 로딩 중 잠시 비움

        ApiClient.instance.getBinance24hrTicker().enqueue(object : Callback<Binance24hrTickerResponse> {
            override fun onResponse(call: Call<Binance24hrTickerResponse>, response: Response<Binance24hrTickerResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        updateUIWithBinance24hrData(it) // 새로운 UI 업데이트 함수 호출
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
            val currentPrice = data.lastPrice.toDouble()
            val absoluteChange = data.priceChange.toDouble() // 절대 변동액 문자열을 Double로 변환
            val priceChangePercent = data.priceChangePercent.toDouble() // 퍼센트 변동률 문자열을 Double로 변환

            // 현재 가격 표시
            val priceFormat = DecimalFormat("$,##0.00")
            binding.tvBitcoinPrice.text = priceFormat.format(currentPrice)

            // 가격 변동률 표시 (-278.4 (-0.67%) 형태)
            val absoluteChangeFormat = DecimalFormat("+#,##0.0;-#,##0.0") // 부호 포함, 소수점 한 자리
            val percentageChangeFormat = DecimalFormat("+#0.00'%';-#0.00'%'") // 부호 포함, 소수점 두 자리, % 기호

            val formattedAbsoluteChange = absoluteChangeFormat.format(absoluteChange)
            val formattedPercentageChange = percentageChangeFormat.format(priceChangePercent) // API가 % 단위로 주므로 100 곱할 필요 없음

            binding.tvPriceChange.text = String.format(Locale.getDefault(), "%s (%s)",
                formattedAbsoluteChange,
                formattedPercentageChange
            )

            // 변동률에 따른 텍스트 색상 변경
            if (priceChangePercent >= 0) {
                binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, R.color.positive_green))
            } else {
                binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light)) // 또는 R.color.negative_red
            }

        } catch (e: NumberFormatException) {
            showError("가격/변동률 데이터 형식 오류 (Binance)")
            Log.e("MainActivity", "Error parsing Binance 24hr ticker data: $data", e)
            binding.tvPriceChange.text = "변동률 오류" // 오류 시 표시
        } catch (e: Exception) {
            showError("UI 업데이트 중 알 수 없는 오류 발생")
            Log.e("MainActivity", "Unknown error in updateUIWithBinance24hrData", e)
            binding.tvPriceChange.text = "업데이트 오류" // 오류 시 표시
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
}