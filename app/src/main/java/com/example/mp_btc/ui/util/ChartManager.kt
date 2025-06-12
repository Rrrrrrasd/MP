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
 * MPAndroidChart의 설정 및 데이터 업데이트를 관리하는 헬퍼 클래스.
 * @param lineChart 관리할 LineChart 객체.
 * @param context 리소스 접근을 위한 컨텍스트.
 */
class ChartManager(
    private val lineChart: LineChart,
    private val context: Context
) {
    /**
     * 클래스 초기화 시 차트의 기본 설정을 수행합니다.
     */
    init {
        setupChart()
    }

    /**
     * 차트의 외형 및 기본 속성(범례, 축 등)을 설정합니다.
     */
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
                isEnabled = true
                form = Legend.LegendForm.LINE
                textColor = Color.WHITE
                textSize = 12f
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
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

    /**
     * 가격 데이터와 이동평균선 데이터를 사용하여 차트를 업데이트하고,
     * 선택된 기간에 맞게 줌 레벨을 조절합니다.
     * @param data 차트 업데이트에 필요한 데이터 (K-lines, 이동평균선 값).
     * @param usdToKrwRate USD/KRW 환율. null일 경우 USD로 표시.
     * @param period 사용자가 선택한 기간 문자열 (e.g., "1", "30").
     */
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

        val priceEntries = data.klines.mapIndexedNotNull { _, klineData ->
            try {
                val timestamp = (klineData[0] as Double).toLong()
                val closePriceUsd = (klineData[4] as String).toFloat()
                val displayPrice = usdToKrwRate?.let { (closePriceUsd * it).toFloat() } ?: closePriceUsd
                Entry(timestamp.toFloat(), displayPrice)
            } catch (e: Exception) { null }
        }.sortedBy { it.x }

        val sma5Entries = createMaEntries(data.klines, data.sma5, usdToKrwRate)
        val sma20Entries = createMaEntries(data.klines, data.sma20, usdToKrwRate)
        val sma60Entries = createMaEntries(data.klines, data.sma60, usdToKrwRate)

        val priceDataSet = LineDataSet(priceEntries, "Price").apply {
            color = ContextCompat.getColor(context, R.color.positive_green)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2.5f
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(context, R.drawable.chart_gradient)
            isHighlightEnabled = true
            setDrawHorizontalHighlightIndicator(false)
            highLightColor = Color.GRAY
        }

        val sma5DataSet = createMaDataSet(sma5Entries, "SMA 5", Color.YELLOW)
        val sma20DataSet = createMaDataSet(sma20Entries, "SMA 20", Color.CYAN)
        val sma60DataSet = createMaDataSet(sma60Entries, "SMA 60", Color.MAGENTA)

        val lineData = LineData(priceDataSet, sma5DataSet, sma20DataSet, sma60DataSet)
        lineChart.data = lineData

        lineChart.axisLeft.resetAxisMinimum()
        lineChart.axisLeft.resetAxisMaximum()

        lineChart.notifyDataSetChanged()
        lineChart.fitScreen()

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

    /**
     * 이동평균선(MA) 데이터 리스트를 차트에 표시할 Entry 리스트로 변환합니다.
     * @param klines 타임스탬프 정보를 얻기 위한 K-line 데이터.
     * @param maData 변환할 이동평균선 값 리스트 (NaN 포함 가능).
     * @param rate 환율.
     * @return 차트에 사용될 Entry 리스트.
     */
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

    /**
     * 이동평균선(MA) Entry 리스트를 받아 스타일이 적용된 LineDataSet을 생성합니다.
     * @param entries MA Entry 리스트.
     * @param label 범례에 표시될 이름.
     * @param color 라인 색상.
     * @return 생성된 LineDataSet 객체.
     */
    private fun createMaDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            this.color = color
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 1f
            isHighlightEnabled = false
        }
    }

    /**
     * X축의 타임스탬프 값을 "MM/dd" 형식의 날짜 문자열로 변환하는 Formatter.
     */
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

    /**
     * Y축의 가격 값을 원화(₩) 또는 달러($) 형식의 문자열로 변환하는 Formatter.
     */
    private class YAxisValueFormatter(private val usdToKrwRate: Double?) : ValueFormatter() {
        private val krwFormat = DecimalFormat("₩#,##0")
        private val usdFormat = DecimalFormat("$#,##0")
        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            return if (usdToKrwRate != null) krwFormat.format(value) else usdFormat.format(value)
        }
    }
}