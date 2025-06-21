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

    // 뷰 바인딩, 뷰모델, 차트 매니저 등 주요 객체 선언
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chartManager: ChartManager
    private lateinit var toggle: ActionBarDrawerToggle
    private var currentSelectedPeriod: String = "1" // 현재 선택된 차트 기간 (기본값: 1일)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 툴바 및 내비게이션 드로어 설정
        setupToolbarAndDrawer()

        // 2. 차트 매니저 초기화
        chartManager = ChartManager(binding.priceLineChart, this)

        // 3. 버튼 클릭 리스너 설정
        setupButtonListeners()

        // 4. ViewModel의 LiveData 관찰 설정
        observeViewModel()

        // 5. 앱 시작 시 기본 데이터 로드 (1일치 차트)
        binding.btn1Day.post {
            updateButtonSelectionUI(binding.btn1Day)
            viewModel.fetchHistoricalData("1")
        }
    }

    // 툴바와 내비게이션 드로어(햄버거 메뉴)를 설정한다.
    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

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
    }

    // 화면의 주요 버튼들에 대한 클릭 리스너를 설정한다.
    private fun setupButtonListeners() {
        // 시간 필터 버튼 (1D, 5D 등) 설정
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
                updateButtonSelectionUI(button) // 선택된 버튼의 UI 변경
                currentSelectedPeriod = period // 현재 선택된 기간 저장
                viewModel.fetchHistoricalData(period) // 해당 기간의 데이터 요청
            }
        }

        // 새로고침 버튼
        binding.btnRefresh.setOnClickListener {
            viewModel.fetchInitialData() // 현재 가격 정보 새로고침
            viewModel.fetchHistoricalData(currentSelectedPeriod) // 현재 차트 데이터 새로고침
        }

        // 가격 예측 버튼
        binding.tvBottomPredict.setOnClickListener {
            viewModel.predictPrice()
        }
    }

    // ViewModel의 LiveData를 관찰하여 UI를 업데이트한다.
    private fun observeViewModel() {
        // 현재 가격 및 변동률 UI 업데이트
        viewModel.priceUiState.observe(this) { state ->
            binding.tvBitcoinPrice.text = state.priceText
            binding.tvPriceChange.text = state.changeText
            binding.tvPriceChange.setTextColor(ContextCompat.getColor(this, state.changeTextColorRes))
        }

        // 차트 데이터 UI 업데이트
        viewModel.chartData.observe(this) { chartUpdateData ->
            val currentRate = viewModel.usdToKrwRate.value
            chartManager.updateChartWithMA(chartUpdateData, currentRate, currentSelectedPeriod)
        }

        // 토스트 메시지 표시
        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // 가격 예측 결과가 오면 PredictionActivity를 시작
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

    // 시간 필터 버튼들 중에서 선택된 버튼의 UI를 활성화 상태로 변경한다.
    private fun updateButtonSelectionUI(selectedBtn: Button) {
        val allButtons = listOf(
            binding.btn1Day, binding.btn5Day, binding.btn1Month,
            binding.btn6Months, binding.btn1Year, binding.btnAll
        )
        allButtons.forEach { button ->
            button.isSelected = (button == selectedBtn) // 선택된 버튼만 isSelected = true
            val color = if (button.isSelected) R.color.text_primary_dark else R.color.text_secondary_dark
            button.setTextColor(ContextCompat.getColor(this, color))
        }
    }

    // 내비게이션 드로어 메뉴 아이템 클릭 시 호출된다.
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_portfolio -> {
                startActivity(Intent(this, PortfolioActivity::class.java))
            }
            R.id.nav_news -> {
                startActivity(Intent(this, NewsActivity::class.java))
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START) // 메뉴 클릭 후 드로어 닫기
        return true
    }

    // 뒤로가기 버튼을 눌렀을 때의 동작을 정의한다.
    override fun onBackPressed() {
        // 드로어가 열려있으면 닫고, 그렇지 않으면 기본 동작(앱 종료 등)을 수행
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}