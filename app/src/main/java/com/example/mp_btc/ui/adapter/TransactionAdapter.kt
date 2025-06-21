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

class TransactionAdapter(private var usdToKrwRate: Double?) :
    ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    fun setKrwRate(rate: Double?) {
        this.usdToKrwRate = rate
        // 환율이 변경되면 전체 리스트를 갱신하여 가격을 다시 그리도록 함
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

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

class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
    override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem == newItem
    }
}