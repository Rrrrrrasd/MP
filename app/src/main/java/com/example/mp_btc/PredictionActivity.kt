package com.example.mp_btc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mp_btc.databinding.ActivityPredictionBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding

    companion object {
        const val EXTRA_PREDICTED_PRICE_USD = "PREDICTED_PRICE_USD"
        const val EXTRA_LAST_CLOSE_PRICE_USD = "LAST_CLOSE_PRICE_USD"
        const val EXTRA_BASIS_TIMESTAMP = "BASIS_TIMESTAMP"
        const val EXTRA_USD_TO_KRW_RATE = "USD_TO_KRW_RATE"
    }

    /**
     * 액티비티 생성 시 호출됩니다.
     * Intent로부터 전달받은 예측 관련 데이터를 화면의 각 TextView에 표시합니다.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.title_activity_prediction)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (intent.hasExtra(EXTRA_PREDICTED_PRICE_USD)) {
            val predictedPriceUsd = intent.getDoubleExtra(EXTRA_PREDICTED_PRICE_USD, 0.0)
            val lastClosePriceUsd = intent.getDoubleExtra(EXTRA_LAST_CLOSE_PRICE_USD, 0.0)
            val basisTimestamp = intent.getLongExtra(EXTRA_BASIS_TIMESTAMP, 0L)
            val usdToKrwRate = if (intent.hasExtra(EXTRA_USD_TO_KRW_RATE)) {
                intent.getDoubleExtra(EXTRA_USD_TO_KRW_RATE, -1.0)
            } else -1.0

            setupUI(predictedPriceUsd, lastClosePriceUsd, basisTimestamp, usdToKrwRate)
        } else {
            binding.tvPredictedPriceDisplay.text = getString(R.string.prediction_failed)
        }
    }

    private fun setupUI(predictedPriceUsd: Double, lastClosePriceUsd: Double, basisTimestamp: Long, usdToKrwRate: Double) {
        val krwFormat = DecimalFormat("₩#,##0")
        val usdFormat = DecimalFormat("$#,##0.00")
        val isKrw = usdToKrwRate != -1.0

        // 1. 예측 가격 설정
        binding.tvPredictedPriceDisplay.text = if (isKrw) {
            krwFormat.format(predictedPriceUsd * usdToKrwRate)
        } else {
            usdFormat.format(predictedPriceUsd)
        }

        // 2. 예측 기준일 설정
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        binding.tvBasisDate.text = sdf.format(Date(basisTimestamp))

        // 3. 직전 종가 설정
        binding.tvLastClosePrice.text = if (isKrw) {
            krwFormat.format(lastClosePriceUsd * usdToKrwRate)
        } else {
            usdFormat.format(lastClosePriceUsd)
        }

        // 4. 예상 변동률 계산 및 설정
        if (lastClosePriceUsd > 0) {
            val changePercent = ((predictedPriceUsd - lastClosePriceUsd) / lastClosePriceUsd)
            val percentFormat = DecimalFormat("+#0.00%;-#0.00%")
            binding.tvPredictedChange.text = percentFormat.format(changePercent)

            val colorRes = if (changePercent >= 0) R.color.positive_green else R.color.negative_red
            binding.tvPredictedChange.setTextColor(ContextCompat.getColor(this, colorRes))
        } else {
            binding.tvPredictedChange.text = "N/A"
        }
    }

    /**
     * ActionBar의 뒤로가기 버튼을 눌렀을 때의 동작을 처리합니다.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}