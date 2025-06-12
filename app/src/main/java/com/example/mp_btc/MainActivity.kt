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
import com.example.mp_btc.databinding.ActivityMainBinding
import com.example.mp_btc.ui.util.ChartManager
import com.example.mp_btc.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chartManager: ChartManager
    private var currentSelectedPeriod: String = "1"

    /**
     * 액티비티 생성 시 호출되며, 뷰 바인딩, 차트 매니저 초기화,
     * 버튼 리스너 설정 및 ViewModel 관찰을 시작합니다.
     */
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

    /**
     * 차트 기간 선택 버튼(1일, 5일 등), 새로고침 버튼, 예측 버튼에 대한
     * 클릭 리스너를 설정합니다.
     */
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
            val selectedButton = listOf(binding.btn1Day, binding.btn5Day, binding.btn1Month, binding.btn6Months, binding.btn1Year, binding.btnAll)
                .firstOrNull { (it as? com.google.android.material.button.MaterialButton)?.backgroundTintList?.defaultColor == ContextCompat.getColor(this, R.color.time_filter_button_selected_background) }

            val period = when (selectedButton?.id) {
                R.id.btn1Day -> "1"
                R.id.btn5Day -> "5"
                R.id.btn1Month -> "30"
                R.id.btn6Months -> "180"
                R.id.btn1Year -> "365"
                R.id.btnAll -> "max"
                else -> "1"
            }
            viewModel.fetchHistoricalData(period)
        }

        binding.tvBottomPredict.setOnClickListener {
            viewModel.predictPrice()
        }
    }

    /**
     * ViewModel의 LiveData를 관찰하여 UI를 업데이트합니다.
     * 가격, 차트 데이터, 토스트 메시지, 예측 결과에 대한 변경사항을 감지하고 처리합니다.
     */
    private fun observeViewModel() {
        viewModel.priceUiState.observe(this) { state ->
            binding.tvBitcoinPrice.text = state.priceText
            binding.tvPriceChange.text = state.changeText
            binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, state.changeTextColorRes))
        }

        viewModel.chartData.observe(this) { chartUpdateData ->
            val currentRate = viewModel.usdToKrwRate.value
            chartManager.updateChartWithMA(chartUpdateData, currentRate, currentSelectedPeriod)
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.predictionUiState.observe(this) { state ->
            state?.let {
                val intent = Intent(this, PredictionActivity::class.java)
                intent.putExtra(PredictionActivity.EXTRA_PREDICTED_PRICE, it.displayString)
                startActivity(intent)
            }
        }
    }

    /**
     * 사용자가 선택한 기간 버튼의 UI를 활성화 상태로 변경하고,
     * 나머지 버튼들은 비활성화 상태로 되돌립니다.
     * @param selectedBtn 사용자가 선택한 버튼 객체
     */
    private fun updateButtonSelectionUI(selectedBtn: Button) {
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