package com.example.mp_btc.ui.util

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.mp_btc.R
import com.example.mp_btc.viewmodel.ChartUpdateData
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MPAndroidChart의 설정 및 업데이트를 관리하는 헬퍼 클래스
 */
class ChartManager(
    private val lineChart: LineChart,
    private val context: Context
) {
    init {
        setupChart()
    }

    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            axisRight.isEnabled = false

            legend.apply {
                isEnabled = true // 범례 활성화
                form = Legend.LegendForm.LINE // 범례 모양을 선으로
                textColor = Color.WHITE // 글자 색상
                textSize = 12f
                verticalAlignment = Legend.LegendVerticalAlignment.TOP // 수직 상단 정렬
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER // 수평 가운데 정렬
                orientation = Legend.LegendOrientation.HORIZONTAL // 수평 방향으로 나열
                setDrawInside(false) // 차트 바깥에 그리기
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = DateAxisValueFormatter()
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.WHITE
                textSize = 12f
                setDrawAxisLine(true)
                axisLineColor = Color.WHITE
                setLabelCount(5, true)
            }

            axisLeft.apply {
                setDrawGridLines(false)
                textColor = Color.WHITE
                textSize = 12f
                setDrawAxisLine(true)
                axisLineColor = Color.WHITE
            }
        }
    }

    fun updateChart(klines: List<List<Any>>, usdToKrwRate: Double?) {
        if (klines.isEmpty()) {
            lineChart.clear()
            lineChart.setNoDataText("차트 데이터를 불러오는 중입니다...")
            lineChart.invalidate()
            return
        }

        // Y축 포맷터를 환율 정보에 따라 동적으로 설정
        lineChart.axisLeft.valueFormatter = YAxisValueFormatter(usdToKrwRate)

        val entries = klines.mapNotNull { klineData ->
            try {
                val timestamp = (klineData[0] as Double).toLong()
                val closePriceUsd = (klineData[4] as String).toFloat()
                val displayPrice = usdToKrwRate?.let { (closePriceUsd * it).toFloat() } ?: closePriceUsd
                Entry(timestamp.toFloat(), displayPrice)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.x }

        val dataSet = LineDataSet(entries, "Price").apply {
            color = ContextCompat.getColor(context, R.color.positive_green) // 라인 색상
            setDrawCircles(false) // 데이터 포인트에 원 그리지 않기
            setDrawValues(false) // 데이터 값 텍스트 표시 안함
            lineWidth = 2.5f // 라인 두께
            setDrawFilled(true) // 라인 아래 영역 채우기 활성화

            // 그라데이션 설정
            fillDrawable = ContextCompat.getDrawable(context, R.drawable.chart_gradient)

            // 하이라이트 기능 비활성화 (선택 사항)
            isHighlightEnabled = false
        }

        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
    }
    fun updateChartWithMA(data: ChartUpdateData, usdToKrwRate: Double?, period: String) {
        val marker = MyMarkerView(context, R.layout.marker_view, usdToKrwRate)
        lineChart.marker = marker
        if (data.klines.isEmpty()) {
            lineChart.clear()
            lineChart.setNoDataText("차트 데이터를 불러오는 중입니다...")
            lineChart.invalidate()
            return
        }

        lineChart.axisLeft.valueFormatter = YAxisValueFormatter(usdToKrwRate)

        // 1. 주 가격 라인 데이터 생성
        val priceEntries = data.klines.mapIndexedNotNull { index, klineData ->
            try {
                val timestamp = (klineData[0] as Double).toLong()
                val closePriceUsd = (klineData[4] as String).toFloat()
                val displayPrice = usdToKrwRate?.let { (closePriceUsd * it).toFloat() } ?: closePriceUsd
                Entry(timestamp.toFloat(), displayPrice)
            } catch (e: Exception) { null }
        }.sortedBy { it.x }

        // 2. 이동평균선 데이터 생성 (NaN 값은 제외)
        val sma5Entries = createMaEntries(data.klines, data.sma5, usdToKrwRate)
        val sma20Entries = createMaEntries(data.klines, data.sma20, usdToKrwRate)
        val sma60Entries = createMaEntries(data.klines, data.sma60, usdToKrwRate)

        // 3. 각 데이터에 대한 DataSet 생성 및 스타일링
        val priceDataSet = LineDataSet(priceEntries, "Price").apply {
            color = ContextCompat.getColor(context, R.color.positive_green)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2.5f
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(context, R.drawable.chart_gradient)
            isHighlightEnabled = true // 마커를 위해 하이라이트 활성화
            setDrawHorizontalHighlightIndicator(false) // 수평 하이라이트 선 끄기
            highLightColor = Color.GRAY
        }

        val sma5DataSet = createMaDataSet(sma5Entries, "SMA 5", Color.YELLOW)
        val sma20DataSet = createMaDataSet(sma20Entries, "SMA 20", Color.CYAN)
        val sma60DataSet = createMaDataSet(sma60Entries, "SMA 60", Color.MAGENTA)

        // 4. 모든 DataSet을 LineData에 합치기
        val lineData = LineData(priceDataSet, sma5DataSet, sma20DataSet, sma60DataSet)
        lineChart.data = lineData

        // 1. Y축(가격) 범위를 데이터에 맞게 자동으로 다시 계산하도록 강제합니다.
        lineChart.axisLeft.resetAxisMinimum()
        lineChart.axisLeft.resetAxisMaximum()

        // 2. 데이터가 변경되었음을 차트에 알리고, 뷰포트(보이는 영역)를 완전히 초기화합니다.
        // 이 과정이 없으면 이전의 확대/축소 상태가 다음 차트에 영향을 줍니다.
        lineChart.notifyDataSetChanged()
        lineChart.fitScreen()

        // 3. 이제 깨끗한 상태에서 기간에 맞는 새로운 확대를 적용합니다.
        if (priceEntries.isNotEmpty()) {
            val numberOfVisiblePoints = when (period) {
                "1" -> 60
                "5" -> 48
                "30" -> 30
                "180" -> 60
                "365" -> 120
                "max" -> 730
                else -> priceEntries.size
            }
            val pointsToShow = numberOfVisiblePoints.coerceAtMost(priceEntries.size)
            val scaleX = if (pointsToShow > 0) priceEntries.size.toFloat() / pointsToShow.toFloat() else 1f

            lineChart.zoom(scaleX.coerceAtLeast(1f), 1f, 0f, 0f)
            lineChart.moveViewToX(priceEntries.last().x)
        }

        lineChart.invalidate()
    }

    // ✨ [추가] 이동평균선 Entry 리스트를 만드는 헬퍼 함수
    private fun createMaEntries(klines: List<List<Any>>, maData: List<Double>, rate: Double?): List<Entry> {
        return maData.mapIndexedNotNull { index, value ->
            if (!value.isNaN() && index < klines.size) {
                val timestamp = (klines[index][0] as Double).toLong()
                val displayValue = rate?.let { (value * it).toFloat() } ?: value.toFloat()
                Entry(timestamp.toFloat(), displayValue)
            } else {
                null
            }
        }
    }

    // ✨ [추가] 이동평균선 DataSet을 만드는 헬퍼 함수
    private fun createMaDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            this.color = color
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 1f
            isHighlightEnabled = false // MA 라인은 하이라이트 안되게 설정
        }
    }


    private class DateAxisValueFormatter : ValueFormatter() {
        private val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return try {
                sdf.format(Date(value.toLong()))
            } catch (e: Exception) {
                ""
            }
        }
    }

    private class YAxisValueFormatter(private val usdToKrwRate: Double?) : ValueFormatter() {
        private val krwFormat = DecimalFormat("₩#,##0")
        private val usdFormat = DecimalFormat("$#,##0")
        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return if (usdToKrwRate != null) krwFormat.format(value) else usdFormat.format(value)
        }
    }
}