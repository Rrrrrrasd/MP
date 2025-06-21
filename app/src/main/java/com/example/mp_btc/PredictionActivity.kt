package com.example.mp_btc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mp_btc.databinding.ActivityPredictionBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TFLite 모델의 가격 예측 결과를 보여주는 액티비티.
 * MainActivity로부터 Intent를 통해 데이터를 전달받는다.
 */
class PredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding

    // Intent에서 데이터를 주고받기 위한 키(Key) 정의
    companion object {
        const val EXTRA_PREDICTED_PRICE_USD = "PREDICTED_PRICE_USD"
        const val EXTRA_LAST_CLOSE_PRICE_USD = "LAST_CLOSE_PRICE_USD"
        const val EXTRA_BASIS_TIMESTAMP = "BASIS_TIMESTAMP"
        const val EXTRA_USD_TO_KRW_RATE = "USD_TO_KRW_RATE"
    }

    /**
     * 액티비티 생성 시 호출된다.
     * Intent로부터 전달받은 예측 관련 데이터를 화면의 각 UI 요소에 표시한다.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정
        supportActionBar?.title = getString(R.string.title_activity_prediction)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Intent에 예측 가격 데이터가 있는지 확인
        if (intent.hasExtra(EXTRA_PREDICTED_PRICE_USD)) {
            val predictedPriceUsd = intent.getDoubleExtra(EXTRA_PREDICTED_PRICE_USD, 0.0)
            val lastClosePriceUsd = intent.getDoubleExtra(EXTRA_LAST_CLOSE_PRICE_USD, 0.0)
            val basisTimestamp = intent.getLongExtra(EXTRA_BASIS_TIMESTAMP, 0L)
            // 환율 정보는 없을 수도 있으므로 기본값 -1.0으로 처리
            val usdToKrwRate = intent.getDoubleExtra(EXTRA_USD_TO_KRW_RATE, -1.0)

            setupUI(predictedPriceUsd, lastClosePriceUsd, basisTimestamp, usdToKrwRate)
        } else {
            // 데이터가 없으면 실패 메시지 표시
            binding.tvPredictedPriceDisplay.text = getString(R.string.prediction_failed)
        }
    }

    // 전달받은 데이터를 이용해 UI를 설정한다.
    private fun setupUI(predictedPriceUsd: Double, lastClosePriceUsd: Double, basisTimestamp: Long, usdToKrwRate: Double) {
        // 숫자 포맷터 및 환율 적용 여부 설정
        val krwFormat = DecimalFormat("₩#,##0")
        val usdFormat = DecimalFormat("$#,##0.00")
        val isKrw = usdToKrwRate != -1.0

        // 1. 예측 가격 설정 (환율에 따라 원화 또는 달러로 표시)
        binding.tvPredictedPriceDisplay.text = if (isKrw) {
            krwFormat.format(predictedPriceUsd * usdToKrwRate)
        } else {
            usdFormat.format(predictedPriceUsd)
        }

        // 2. 예측 기준일 설정 (타임스탬프를 날짜 형식으로 변환)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        binding.tvBasisDate.text = sdf.format(Date(basisTimestamp))

        // 3. 직전 종가 설정 (환율에 따라 원화 또는 달러로 표시)
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

            // 등락에 따라 텍스트 색상 변경
            val colorRes = if (changePercent >= 0) R.color.positive_green else R.color.negative_red
            binding.tvPredictedChange.setTextColor(ContextCompat.getColor(this, colorRes))
        } else {
            binding.tvPredictedChange.text = "N/A"
        }
    }

    /**
     * 툴바의 '뒤로가기' 버튼을 눌렀을 때의 동작을 처리한다.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}