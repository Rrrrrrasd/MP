package com.example.mp_btc.ui.util

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.mp_btc.R
import com.github.mikephil.charting.charts.LineChart
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
            legend.isEnabled = false

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
            color = ContextCompat.getColor(context, R.color.positive_green)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }

        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
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