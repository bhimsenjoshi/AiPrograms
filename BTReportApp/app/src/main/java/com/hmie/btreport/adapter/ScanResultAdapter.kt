package com.hmie.btreport.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hmie.btreport.ScanItem
import com.hmie.btreport.ScanStatus
import com.hmie.btreport.databinding.ItemScanResultBinding

class ScanResultAdapter(
    private val onRescan: (Int) -> Unit = {}
) : ListAdapter<ScanItem, ScanResultAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemScanResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ScanItem) {
            b.tvFileName.text = item.fileName
            tryLoadImage(item.uri)

            when (item.status) {
                ScanStatus.PENDING -> {
                    b.progressScan.visibility = View.GONE
                    b.tvStatus.text = "Pending scan"
                    b.tvStatus.setTextColor(0xFF9CA3AF.toInt())
                    b.tvStatusBadge.visibility = View.GONE
                    b.layoutResult.visibility = View.GONE
                    b.tvError.visibility = View.GONE
                    b.btnRescan.visibility = View.GONE
                }
                ScanStatus.SCANNING -> {
                    b.progressScan.visibility = View.VISIBLE
                    b.layoutPending.visibility = View.GONE
                    b.tvStatus.text = "Extracting data…"
                    b.tvStatus.setTextColor(0xFF1A237E.toInt())
                    b.tvStatusBadge.visibility = View.VISIBLE
                    b.tvStatusBadge.text = "SCANNING"
                    b.layoutResult.visibility = View.GONE
                    b.tvError.visibility = View.GONE
                    b.btnRescan.visibility = View.GONE
                }
                ScanStatus.SUCCESS -> {
                    b.progressScan.visibility = View.GONE
                    b.layoutPending.visibility = View.GONE
                    b.tvStatusBadge.visibility = View.VISIBLE
                    b.tvStatusBadge.text = "✓  AI EXTRACTION SUCCESS"
                    b.tvStatusBadge.setTextColor(0xFFFFFFFF.toInt())
                    b.tvStatusBadge.setBackgroundColor(0xFF16A34A.toInt())
                    val r = item.result!!
                    b.tvStatus.text = "Review Parsed Data"
                    b.tvStatus.setTextColor(0xFF111827.toInt())
                    b.tvVendor.text = r.operator.ifBlank { r.description.take(28).ifBlank { "—" } }
                    val amtPrefix = when (r.currency) {
                        "INR" -> "₹"; "KRW" -> "₩"; "SGD" -> "S$"; "USD" -> "$"
                        "EUR" -> "€"; "JPY" -> "¥"; "GBP" -> "£"
                        else  -> "${r.currency} "
                    }
                    b.tvAmount.text = "$amtPrefix${"%.2f".format(r.amount)}"
                    b.tvDate.text = r.date.ifBlank { "—" }
                    b.tvCategory.text = r.expenseType.displayName
                    if (r.fromCity.isNotBlank()) {
                        b.layoutRoute.visibility = View.VISIBLE
                        val timeStr = if (r.departureTime.isNotBlank()) "  ·  Dep ${r.departureTime}" else "  ·  NO DEP TIME"
                        b.tvRoute.text = "${r.fromCity} → ${r.toCity}$timeStr"
                    } else {
                        b.layoutRoute.visibility = View.GONE
                    }
                    b.layoutResult.visibility = View.VISIBLE
                    b.tvError.visibility = View.GONE
                    b.btnRescan.visibility = View.VISIBLE
                    b.btnRescan.setOnClickListener { onRescan(adapterPosition) }
                }
                ScanStatus.ERROR -> {
                    b.progressScan.visibility = View.GONE
                    b.tvStatus.text = "Extraction failed"
                    b.tvStatus.setTextColor(0xFFDC2626.toInt())
                    b.tvStatusBadge.visibility = View.VISIBLE
                    b.tvStatusBadge.text = "FAILED"
                    b.tvStatusBadge.setBackgroundColor(0xFFDC2626.toInt())
                    b.layoutResult.visibility = View.GONE
                    b.tvError.visibility = View.VISIBLE
                    b.tvError.text = item.error ?: "Unknown error"
                    b.btnRescan.visibility = View.VISIBLE
                    b.btnRescan.setOnClickListener { onRescan(adapterPosition) }
                }
            }
        }

        private fun tryLoadImage(uri: Uri) {
            try {
                b.ivReceiptPreview.setImageURI(uri)
                b.ivReceiptPreview.visibility = View.VISIBLE
                b.layoutPending.visibility = View.GONE
            } catch (e: Exception) {
                b.ivReceiptPreview.visibility = View.GONE
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
