package com.hmie.btreport.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hmie.btreport.databinding.ItemExpenseBinding
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import java.io.File

class ExpenseAdapter(
    private val onEdit: (Expense) -> Unit,
    private val onDelete: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemExpenseBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(exp: Expense) {
            b.tvExpenseType.text = exp.type.displayName
            b.tvExpenseDate.text = exp.date
            b.tvExpenseDesc.text = exp.description.ifBlank {
                if (exp.fromCity.isNotBlank()) "${exp.fromCity} → ${exp.toCity}" else "—"
            }
            b.tvExpenseAmount.text = "₹${"%.0f".format(exp.amount)}"
            b.tvReceiptRef.text = exp.receiptRef.ifBlank { "" }

            // Category emoji icon
            b.tvTypeIcon.text = when (exp.type) {
                ExpenseType.FLIGHT -> "✈️"
                ExpenseType.CAB    -> "🚗"
                ExpenseType.FOOD   -> "🍽️"
                ExpenseType.HOTEL  -> "🏨"
                ExpenseType.OTHER  -> "📋"
            }

            // Receipt thumbnail if available
            val imgPath = exp.imageUri
            if (!imgPath.isNullOrBlank() && File(imgPath).exists()) {
                try {
                    b.ivThumb.setImageURI(Uri.fromFile(File(imgPath)))
                    b.ivThumb.visibility = View.VISIBLE
                    b.tvTypeIcon.visibility = View.GONE
                } catch (e: Exception) {
                    b.ivThumb.visibility = View.GONE
                    b.tvTypeIcon.visibility = View.VISIBLE
                }
            } else {
                b.ivThumb.visibility = View.GONE
                b.tvTypeIcon.visibility = View.VISIBLE
            }

            b.btnEdit.setOnClickListener { onEdit(exp) }
            b.btnDelete.setOnClickListener { onDelete(exp) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Expense>() {
            override fun areItemsTheSame(a: Expense, b: Expense) = a.id == b.id
            override fun areContentsTheSame(a: Expense, b: Expense) = a == b
        }
    }
}
