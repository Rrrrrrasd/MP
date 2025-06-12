package com.example.mp_btc

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mp_btc.PredictionActivity
import com.example.mp_btc.R
import com.example.mp_btc.databinding.ActivityMainBinding
import com.example.mp_btc.ui.util.ChartManager
import com.example.mp_btc.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chartManager: ChartManager
    private var currentSelectedPeriod: String = "1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chartManager = ChartManager(binding.priceLineChart, this)

        setupButtonListeners()
        observeViewModel()

        binding.btn1Day.post {
            updateButtonSelectionUI(binding.btn1Day)
            viewModel.fetchHistoricalData("1")
        }
    }

    private fun setupButtonListeners() {
        val buttons = mapOf(
            binding.btn1Day to "1",
            binding.btn5Day to "5",
            binding.btn1Month to "30",
            binding.btn6Months to "180",
            binding.btn1Year to "365",
            binding.btnAll to "max"
        )
        buttons.forEach { (button, period) ->
            button.setOnClickListener {
                updateButtonSelectionUI(button)
                currentSelectedPeriod = period
                viewModel.fetchHistoricalData(period)
            }
        }
        binding.btnRefresh.setOnClickListener {
            viewModel.fetchInitialData()
            // 현재 선택된 기간의 차트 데이터도 다시 불러옵니다.
            val selectedButton = listOf(binding.btn1Day, binding.btn5Day, binding.btn1Month, binding.btn6Months, binding.btn1Year, binding.btnAll)
                .firstOrNull { (it as? com.google.android.material.button.MaterialButton)?.backgroundTintList?.defaultColor == ContextCompat.getColor(this, R.color.time_filter_button_selected_background) }

            val period = when (selectedButton?.id) {
                R.id.btn1Day -> "1"
                R.id.btn5Day -> "5"
                R.id.btn1Month -> "30"
                R.id.btn6Months -> "180"
                R.id.btn1Year -> "365"
                R.id.btnAll -> "max"
                else -> "1" // 기본값
            }
            viewModel.fetchHistoricalData(period)
        }

        binding.tvBottomPredict.setOnClickListener {
            viewModel.predictPrice()
        }
    }

    private fun observeViewModel() {
        viewModel.priceUiState.observe(this) { state ->
            binding.tvBitcoinPrice.text = state.priceText
            binding.tvPriceChange.text = state.changeText
            binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, state.changeTextColorRes))
        }

        // ✅ [수정됨] chartData를 관찰할 때, 환율 정보를 viewModel.usdToKrwRate에서 직접 가져옵니다.
        viewModel.chartData.observe(this) { chartUpdateData ->
            val currentRate = viewModel.usdToKrwRate.value
            // chartManager에 전체 데이터를 전달
            chartManager.updateChartWithMA(chartUpdateData, currentRate, currentSelectedPeriod)
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // ✅ [수정됨] predictionUiState를 관찰하여 Activity로 이동합니다.
        viewModel.predictionUiState.observe(this) { state ->
            // state가 null이 아닐 때만 Activity를 시작합니다. (버튼을 다시 눌렀을 때 재실행하기 위함)
            state?.let {
                val intent = Intent(this, PredictionActivity::class.java)
                intent.putExtra(PredictionActivity.EXTRA_PREDICTED_PRICE, it.displayString)
                startActivity(intent)
            }
        }
    }

    private fun updateButtonSelectionUI(selectedBtn: Button) {
        // ... 이 함수는 변경사항 없음 ...
        val allButtons = listOf(
            binding.btn1Day, binding.btn5Day, binding.btn1Month,
            binding.btn6Months, binding.btn1Year, binding.btnAll
        )
        allButtons.forEach { button ->
            val materialButton = button as? com.google.android.material.button.MaterialButton
            if (button == selectedBtn) {
                materialButton?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.time_filter_button_selected_background))
                button.setTextColor(Color.BLACK)
                button.setTypeface(null, Typeface.BOLD)
            } else {
                materialButton?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.time_filter_button_default_background))
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary_dark))
                button.setTypeface(null, Typeface.NORMAL)
            }
        }
    }
}