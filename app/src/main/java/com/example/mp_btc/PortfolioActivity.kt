// com/example/mp_btc/PortfolioActivity.kt

package com.example.mp_btc

import android.os.Bundle
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mp_btc.databinding.ActivityPortfolioBinding
import com.example.mp_btc.model.Transaction
import com.example.mp_btc.ui.adapter.TransactionAdapter
import com.example.mp_btc.viewmodel.MainViewModel
import com.example.mp_btc.viewmodel.PortfolioViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DecimalFormat

class PortfolioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPortfolioBinding
    private val portfolioViewModel: PortfolioViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels() // 현재 가격을 가져오기 위해 필요
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "내 포트폴리오"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    // ActionBar 뒤로가기 버튼 처리
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(mainViewModel.usdToKrwRate.value)
        binding.rvTransactions.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(this@PortfolioActivity)
        }
    }

    private fun setupFab() {
        binding.fabAddTransaction.setOnClickListener {
            showAddTransactionDialog()
        }
    }

    private fun observeViewModel() {
        portfolioViewModel.allTransactions.observe(this) { transactions ->
            transactionAdapter.submitList(transactions)
            // 현재 가격과 환율이 있을 때만 요약 정보 업데이트
            val currentPriceUsd = mainViewModel.priceUiState.value?.let {
                it.priceText.toString().substringAfter("(").substringBefore(")").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            } ?: 0.0
            val krwRate = mainViewModel.usdToKrwRate.value

            if(currentPriceUsd > 0) {
                updatePortfolioSummary(transactions, currentPriceUsd, krwRate)
            }
        }

        mainViewModel.usdToKrwRate.observe(this) { krwRate ->
            transactionAdapter.setKrwRate(krwRate)
            val currentPriceUsd = mainViewModel.priceUiState.value?.let {
                it.priceText.toString().substringAfter("(").substringBefore(")").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            } ?: 0.0
            val transactions = portfolioViewModel.allTransactions.value ?: emptyList()

            if(currentPriceUsd > 0) {
                updatePortfolioSummary(transactions, currentPriceUsd, krwRate)
            }
        }
    }

    private fun updatePortfolioSummary(transactions: List<Transaction>, currentPriceUsd: Double, krwRate: Double?) {
        var totalHoldingsBtc = 0.0
        var totalCostUsd = 0.0

        transactions.forEach {
            if (it.type == "BUY") {
                totalHoldingsBtc += it.amount
                totalCostUsd += it.amount * it.pricePerBtc
            } else { // SELL
                totalHoldingsBtc -= it.amount
                // 매도 시 총 비용에서 제외 (평단가 유지를 위해)
                if (totalHoldingsBtc > 0) {
                    val avgCost = totalCostUsd / (totalHoldingsBtc + it.amount)
                    totalCostUsd -= it.amount * avgCost
                } else {
                    totalCostUsd = 0.0 // 전부 매도 시 비용 0
                }
            }
        }

        val currentValueUsd = totalHoldingsBtc * currentPriceUsd
        val profitLossUsd = currentValueUsd - totalCostUsd
        val profitLossPercent = if (totalCostUsd > 0) (profitLossUsd / totalCostUsd) * 100 else 0.0

        // 보유량 표시
        binding.tvTotalHoldings.text = "${String.format("%.6f", totalHoldingsBtc)} BTC"

        val krwFormat = DecimalFormat("₩#,##0")
        val usdFormat = DecimalFormat("$#,##0.00")
        val percentFormat = DecimalFormat("+#0.00%;-#0.00%")

        // 원화/달러 표시 분기
        if (krwRate != null) {
            binding.tvCurrentValue.text = krwFormat.format(currentValueUsd * krwRate)
            val profitLossKrw = profitLossUsd * krwRate
            binding.tvTotalProfitLoss.text = "${krwFormat.format(profitLossKrw)} (${percentFormat.format(profitLossPercent / 100)})"
        } else {
            binding.tvCurrentValue.text = usdFormat.format(currentValueUsd)
            binding.tvTotalProfitLoss.text = "${usdFormat.format(profitLossUsd)} (${percentFormat.format(profitLossPercent / 100)})"
        }

        // 수익/손실 색상 변경
        val profitColor = if(profitLossUsd >= 0) R.color.positive_green else R.color.negative_red
        binding.tvTotalProfitLoss.setTextColor(ContextCompat.getColor(this, profitColor))
    }

    private fun showAddTransactionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etPrice = dialogView.findViewById<EditText>(R.id.etPrice)
        val rbBuy = dialogView.findViewById<RadioButton>(R.id.rbBuy)

        // --- 현재가 자동 입력을 위한 로직 추가 ---
        val currentKrwRate = mainViewModel.usdToKrwRate.value
        // 메인 화면의 가격 문자열에서 USD 가격을 파싱합니다. (예: "₩94,123,456 ($70,123.45)")
        val currentPriceUsd = mainViewModel.priceUiState.value?.let {
            it.priceText.toString().substringAfter("(").substringBefore(")").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
        }

        // 환율과 현재가 정보가 모두 있을 경우에만 원화 가격을 계산하여 미리 입력해줍니다.
        if (currentKrwRate != null && currentPriceUsd != null) {
            val currentPriceKrw = (currentPriceUsd * currentKrwRate).toLong() // 소수점 버림
            etPrice.setText(currentPriceKrw.toString())
        }
        // --- 로직 추가 끝 ---


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

                    val priceUsd = priceKrw / krwRate

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