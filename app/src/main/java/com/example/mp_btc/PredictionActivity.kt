package com.example.mp_btc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mp_btc.databinding.ActivityPredictionBinding // ViewBinding import

class PredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding // ViewBinding 변수

    companion object {
        const val EXTRA_PREDICTED_PRICE = "PREDICTED_PRICE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater) // ViewBinding 초기화
        setContentView(binding.root)

        // ActionBar 타이틀 설정 (선택 사항)
        supportActionBar?.title = getString(R.string.title_activity_prediction)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 뒤로가기 버튼

        val predictedPriceString = intent.getStringExtra(EXTRA_PREDICTED_PRICE)

        if (predictedPriceString != null) {
            binding.tvPredictedPriceDisplay.text = predictedPriceString
        } else {
            binding.tvPredictedPriceDisplay.text = getString(R.string.prediction_failed)
        }
    }

    // ActionBar 뒤로가기 버튼 동작
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}