package com.example.mp_btc.ui.util

import android.content.Context
import android.widget.TextView
import com.example.mp_btc.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyMarkerView(context: Context, layoutResource: Int, private val usdToKrwRate: Double?) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val krwFormat = DecimalFormat("₩#,##0")
    private val usdFormat = DecimalFormat("$#,##0.00")
    private val sdf = SimpleDateFormat("yy/MM/dd HH:mm", Locale.getDefault())

    // Entry가 선택될 때마다 호출됩니다.
    override fun refreshContent(e: Entry, highlight: Highlight) {
        val date = sdf.format(Date(e.x.toLong()))
        val price = if (usdToKrwRate != null) krwFormat.format(e.y) else usdFormat.format(e.y)

        tvContent.text = "$date\n$price"
        super.refreshContent(e, highlight)
    }

    // 마커의 위치를 조정합니다.
    override fun getOffset(): MPPointF {
        // x축으로는 중앙, y축으로는 터치 포인트 위로 이동
        return MPPointF(-(width / 2f), -height.toFloat() - 10)
    }
}