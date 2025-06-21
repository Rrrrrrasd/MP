package com.example.mp_btc

import android.os.Bundle
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mp_btc.databinding.ActivityPortfolioBinding
import com.example.mp_btc.model.Transaction
import com.example.mp_btc.ui.adapter.TransactionAdapter
import com.example.mp_btc.viewmodel.MainViewModel
import com.example.mp_btc.viewmodel.PortfolioViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DecimalFormat

class PortfolioActivity : AppCompatActivity() {

    // 뷰 바인딩 및 뷰모델 선언
    private lateinit var binding: ActivityPortfolioBinding
    private val portfolioViewModel: PortfolioViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels() // 현재 가격, 환율 정보 필요
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정
        supportActionBar?.title = "내 포트폴리오"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // UI 및 데이터 관련 초기화
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    // 툴바의 '뒤로가기' 버튼 클릭 시 동작
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // 거래 내역을 보여줄 RecyclerView를 설정한다.
    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(mainViewModel.usdToKrwRate.value)
        binding.rvTransactions.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(this@PortfolioActivity)
        }
    }

    // 새 거래 추가를 위한 플로팅 액션 버튼(FAB)을 설정한다.
    private fun setupFab() {
        binding.fabAddTransaction.setOnClickListener {
            showAddTransactionDialog()
        }
    }

    // 뷰모델의 LiveData를 관찰하여 UI를 업데이트한다.
    private fun observeViewModel() {
        // 데이터베이스의 거래 내역이 변경될 때마다 호출된다.
        portfolioViewModel.allTransactions.observe(this) { transactions ->
            transactionAdapter.submitList(transactions)
            // 현재 가격과 환율 정보가 있을 때만 포트폴리오 요약 정보를 업데이트한다.
            val currentPriceUsd = mainViewModel.priceUiState.value?.let {
                // "₩94M ($70k)" 같은 텍스트에서 USD 가격 파싱
                it.priceText.toString().substringAfter("(").substringBefore(")").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            } ?: 0.0
            val krwRate = mainViewModel.usdToKrwRate.value

            if (currentPriceUsd > 0) {
                updatePortfolioSummary(transactions, currentPriceUsd, krwRate)
            }
        }

        // 환율 정보가 변경될 때마다 호출된다. (앱 실행 초기에 주로 발생)
        mainViewModel.usdToKrwRate.observe(this) { krwRate ->
            transactionAdapter.setKrwRate(krwRate)
            // 환율이 바뀌었으므로, 현재 포트폴리오 요약 정보도 다시 계산하여 업데이트한다.
            val currentPriceUsd = mainViewModel.priceUiState.value?.let {
                it.priceText.toString().substringAfter("(").substringBefore(")").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            } ?: 0.0
            val transactions = portfolioViewModel.allTransactions.value ?: emptyList()

            if (currentPriceUsd > 0) {
                updatePortfolioSummary(transactions, currentPriceUsd, krwRate)
            }
        }
    }

    // 거래 내역 리스트와 현재 시세를 바탕으로 포트폴리오 요약 정보를 계산하고 UI에 반영한다.
    private fun updatePortfolioSummary(transactions: List<Transaction>, currentPriceUsd: Double, krwRate: Double?) {
        var totalHoldingsBtc = 0.0 // 총 보유 BTC
        var totalCostUsd = 0.0 // 총 매수 금액 (평단가 계산용)

        transactions.forEach {
            if (it.type == "BUY") {
                totalHoldingsBtc += it.amount
                totalCostUsd += it.amount * it.pricePerBtc
            } else { // SELL
                val soldAmount = it.amount
                val avgCostBeforeSell = if (totalHoldingsBtc > 0) totalCostUsd / totalHoldingsBtc else 0.0
                totalHoldingsBtc -= soldAmount
                // 매도한 만큼 총 매수 금액에서 차감하여 평단가를 유지
                totalCostUsd -= soldAmount * avgCostBeforeSell
            }
        }

        val currentValueUsd = totalHoldingsBtc * currentPriceUsd
        val profitLossUsd = currentValueUsd - totalCostUsd
        val profitLossPercent = if (totalCostUsd > 0) (profitLossUsd / totalCostUsd) else 0.0

        // 포맷터 설정
        val krwFormat = DecimalFormat("₩#,##0")
        val usdFormat = DecimalFormat("$#,##0.00")
        val percentFormat = DecimalFormat("+#0.00%;-#0.00%")

        // 계산된 값을 UI에 반영
        binding.tvTotalHoldings.text = "${String.format("%.6f", totalHoldingsBtc)} BTC"

        if (krwRate != null) { // 환율 정보가 있을 경우 원화로 표시
            binding.tvCurrentValue.text = krwFormat.format(currentValueUsd * krwRate)
            val profitLossKrw = profitLossUsd * krwRate
            binding.tvTotalProfitLoss.text = "${krwFormat.format(profitLossKrw)} (${percentFormat.format(profitLossPercent)})"
        } else { // 환율 정보가 없으면 달러로 표시
            binding.tvCurrentValue.text = usdFormat.format(currentValueUsd)
            binding.tvTotalProfitLoss.text = "${usdFormat.format(profitLossUsd)} (${percentFormat.format(profitLossPercent)})"
        }

        // 수익/손실에 따라 텍스트 색상 변경
        val profitColor = if(profitLossUsd >= 0) R.color.positive_green else R.color.negative_red
        binding.tvTotalProfitLoss.setTextColor(ContextCompat.getColor(this, profitColor))
    }

    // 새 거래를 추가하는 다이얼로그를 보여준다.
    private fun showAddTransactionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etPrice = dialogView.findViewById<EditText>(R.id.etPrice)
        val rbBuy = dialogView.findViewById<RadioButton>(R.id.rbBuy)

        // 사용자의 편의를 위해 현재 시세를 가격 입력창에 미리 채워준다.
        val currentKrwRate = mainViewModel.usdToKrwRate.value
        val currentPriceUsd = mainViewModel.priceUiState.value?.let {
            it.priceText.toString().substringAfter("(").substringBefore(")").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
        }
        if (currentKrwRate != null && currentPriceUsd != null) {
            val currentPriceKrw = (currentPriceUsd * currentKrwRate).toLong()
            etPrice.setText(currentPriceKrw.toString())
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val krwRate = mainViewModel.usdToKrwRate.value
                if (krwRate == null || krwRate <= 0) {
                    Toast.makeText(this, "환율 정보가 없어 거래를 추가할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val amountText = etAmount.text.toString()
                val priceKrwText = etPrice.text.toString()

                if (amountText.isNotEmpty() && priceKrwText.isNotEmpty()) {
                    val amount = amountText.toDouble()
                    val priceKrw = priceKrwText.toDouble()
                    val priceUsd = priceKrw / krwRate // 저장 시에는 USD 기준으로 변환
                    val type = if (rbBuy.isChecked) "BUY" else "SELL"

                    val transaction = Transaction(
                        type = type,
                        amount = amount,
                        pricePerBtc = priceUsd,
                        timestamp = System.currentTimeMillis()
                    )
                    portfolioViewModel.insert(transaction)
                }
            }
            .show()
    }
}