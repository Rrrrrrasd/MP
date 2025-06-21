// com/example/mp_btc/ui/adapter/TransactionAdapter.kt

package com.example.mp_btc.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mp_btc.R
import com.example.mp_btc.databinding.ItemTransactionBinding
import com.example.mp_btc.model.Transaction
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// 거래 내역 목록을 RecyclerView에 바인딩하는 어댑터
class TransactionAdapter(private var usdToKrwRate: Double?) :
    ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    // 외부에서 환율 정보를 업데이트하고, 리스트를 갱신하여 뷰를 다시 그리게 한다.
    fun setKrwRate(rate: Double?) {
        this.usdToKrwRate = rate
        // 환율이 변경되면 전체 리스트를 갱신하여 가격을 다시 그리도록 함
        notifyDataSetChanged()
    }

    // 뷰홀더를 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    // 뷰홀더에 데이터를 바인딩
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // 개별 거래 내역 아이템의 뷰를 관리하는 뷰홀더
    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        private val krwFormat = DecimalFormat("₩#,##0")
        private val usdFormat = DecimalFormat("$#,##0.00")
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bind(transaction: Transaction) {
            binding.tvTransactionAmount.text = "${transaction.amount} BTC"
            binding.tvTransactionDate.text = dateFormat.format(Date(transaction.timestamp))

            val priceText = if (usdToKrwRate != null) {
                krwFormat.format(transaction.pricePerBtc * usdToKrwRate!!)
            } else {
                usdFormat.format(transaction.pricePerBtc)
            }
            binding.tvTransactionPrice.text = "at $priceText"

            if (transaction.type == "BUY") {
                binding.tvTransactionType.text = "매수"
                binding.tvTransactionType.setTextColor(ContextCompat.getColor(binding.root.context, R.color.positive_green))
            } else {
                binding.tvTransactionType.text = "매도"
                binding.tvTransactionType.setTextColor(ContextCompat.getColor(binding.root.context, R.color.negative_red))
            }
        }
    }
}
// RecyclerView의 업데이트 효율을 높이기 위한 DiffUtil.ItemCallback
class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
    override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem == newItem
    }
}