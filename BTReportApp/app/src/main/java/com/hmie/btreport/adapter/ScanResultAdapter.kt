package com.hmie.btreport.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hmie.btreport.ScanItem
import com.hmie.btreport.ScanStatus
import com.hmie.btreport.databinding.ItemScanResultBinding

class ScanResultAdapter : ListAdapter<ScanItem, ScanResultAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemScanResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ScanItem) {
            b.tvFileName.text = item.fileName

            when (item.status) {
                ScanStatus.PENDING -> {
                    b.tvStatus.text = "Pending"
                    b.tvStatus.setTextColor(0xFF757575.toInt())
                    b.progressScan.visibility = View.GONE
                    b.tvResult.visibility = View.GONE
                }
                ScanStatus.SCANNING -> {
                    b.tvStatus.text = "Scanning…"
                    b.tvStatus.setTextColor(0xFF1565C0.toInt())
                    b.progressScan.visibility = View.VISIBLE
                    b.tvResult.visibility = View.GONE
                }
                ScanStatus.SUCCESS -> {
                    b.progressScan.visibility = View.GONE
                    b.tvResult.visibility = View.VISIBLE
                    val r = item.result!!
                    b.tvStatus.text = "✓ ${r.expenseType.displayName}"
                    b.tvStatus.setTextColor(0xFF2E7D32.toInt())
                    b.tvResult.text = buildString {
                        if (r.date.isNotBlank()) appendLine("Date: ${r.date}")
                        if (r.fromCity.isNotBlank() || r.toCity.isNotBlank())
                            appendLine("Route: ${r.fromCity} → ${r.toCity}")
                        if (r.receiptRef.isNotBlank()) appendLine("Ref: ${r.receiptRef}")
                        if (r.operator.isNotBlank()) appendLine("Operator: ${r.operator}")
                        appendLine("Amount: Rs. ${"%.2f".format(r.amount)}")
                        if (r.description.isNotBlank()) append("Note: ${r.description}")
                    }.trimEnd()
                }
                ScanStatus.ERROR -> {
                    b.progressScan.visibility = View.GONE
                    b.tvResult.visibility = View.VISIBLE
                    b.tvStatus.text = "✗ Failed"
                    b.tvStatus.setTextColor(0xFFC62828.toInt())
                    b.tvResult.text = item.error ?: "Unknown error"
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemScanResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanItem>() {
            override fun areItemsTheSame(a: ScanItem, b: ScanItem) = a.uri == b.uri
            override fun areContentsTheSame(a: ScanItem, b: ScanItem) = a == b
        }
    }
}
