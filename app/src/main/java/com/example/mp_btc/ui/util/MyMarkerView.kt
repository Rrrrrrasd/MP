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

    /**
     * 차트에서 특정 데이터 포인트가 선택될 때마다 호출되어 마커의 내용을 업데이트합니다.
     * @param e 선택된 데이터 Entry.
     * @param highlight 선택된 하이라이트 정보.
     */
    override fun refreshContent(e: Entry, highlight: Highlight) {
        val date = sdf.format(Date(e.x.toLong()))
        val price = if (usdToKrwRate != null) krwFormat.format(e.y) else usdFormat.format(e.y)

        tvContent.text = "$date\n$price"
        super.refreshContent(e, highlight)
    }

    /**
     * 화면에 표시될 마커의 위치 오프셋을 결정합니다.
     * @return 마커의 x, y 오프셋을 담은 MPPointF 객체.
     */
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 10)
    }
}