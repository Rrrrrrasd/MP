package com.example.mp_btc

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.example.mp_btc.databinding.ActivityMainBinding
import com.example.mp_btc.ui.util.ChartManager
import com.example.mp_btc.viewmodel.MainViewModel
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chartManager: ChartManager
    private lateinit var toggle: ActionBarDrawerToggle
    private var currentSelectedPeriod: String = "1" // 기간 선택 변수 복원

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 내비게이션 드로어 설정
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, R.color.white)
        toggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(this)

        // 차트 매니저 초기화
        chartManager = ChartManager(binding.priceLineChart, this)

        setupButtonListeners()
        observeViewModel()

        // 기본 차트 데이터 로드 및 버튼 선택 상태 초기화 (복원)
        binding.btn1Day.post {
            updateButtonSelectionUI(binding.btn1Day)
            viewModel.fetchHistoricalData("1")
        }
    }

    // 시간 필터 버튼 리스너 포함하여 복원
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
            viewModel.fetchHistoricalData(currentSelectedPeriod) // 현재 선택된 기간으로 새로고침
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

        viewModel.chartData.observe(this) { chartUpdateData ->
            val currentRate = viewModel.usdToKrwRate.value
            chartManager.updateChartWithMA(chartUpdateData, currentRate, currentSelectedPeriod) // 변수 사용하도록 복원
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.predictionUiState.observe(this) { state ->
            state?.let {
                val intent = Intent(this, PredictionActivity::class.java).apply {
                    putExtra(PredictionActivity.EXTRA_PREDICTED_PRICE_USD, it.predictedUsdPrice)
                    putExtra(PredictionActivity.EXTRA_LAST_CLOSE_PRICE_USD, it.lastCloseUsdPrice)
                    putExtra(PredictionActivity.EXTRA_BASIS_TIMESTAMP, it.basisTimestamp)
                    it.usdToKrwRate?.let { rate ->
                        putExtra(PredictionActivity.EXTRA_USD_TO_KRW_RATE, rate)
                    }
                }
                startActivity(intent)
            }
        }
    }

    // 버튼 선택 UI 업데이트 함수 (복원)
    private fun updateButtonSelectionUI(selectedBtn: Button) {
        val allButtons = listOf(
            binding.btn1Day, binding.btn5Day, binding.btn1Month,
            binding.btn6Months, binding.btn1Year, binding.btnAll
        )
        allButtons.forEach { button ->
            if (button == selectedBtn) {
                button.isSelected = true
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary_dark))
            } else {
                button.isSelected = false
                button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary_dark))
            }
        }
    }

    // 드로어 메뉴 아이템 클릭 처리
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_portfolio -> {
                startActivity(Intent(this, PortfolioActivity::class.java))
            }
            R.id.nav_news -> {
                startActivity(Intent(this, NewsActivity::class.java))
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // 뒤로가기 버튼 눌렀을 때 드로어가 열려있으면 닫기
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}