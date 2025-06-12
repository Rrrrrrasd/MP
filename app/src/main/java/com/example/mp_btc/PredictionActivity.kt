package com.example.mp_btc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mp_btc.databinding.ActivityPredictionBinding

class PredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding

    companion object {
        const val EXTRA_PREDICTED_PRICE = "PREDICTED_PRICE"
    }

    /**
     * 액티비티 생성 시 호출됩니다.
     * Intent로부터 전달받은 예측 가격을 화면의 TextView에 표시합니다.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.title_activity_prediction)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val predictedPriceString = intent.getStringExtra(EXTRA_PREDICTED_PRICE)

        if (predictedPriceString != null) {
            binding.tvPredictedPriceDisplay.text = predictedPriceString
        } else {
            binding.tvPredictedPriceDisplay.text = getString(R.string.prediction_failed)
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