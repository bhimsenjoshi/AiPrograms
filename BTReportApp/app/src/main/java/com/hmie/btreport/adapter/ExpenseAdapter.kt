package com.hmie.btreport.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hmie.btreport.databinding.ItemExpenseBinding
import com.hmie.btreport.model.Expense

class ExpenseAdapter(
    private val onEdit: (Expense) -> Unit,
    private val onDelete: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemExpenseBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(exp: Expense) {
            b.tvExpenseType.text = exp.type.displayName
            b.tvExpenseDate.text = exp.date
            b.tvExpenseDesc.text = exp.description.ifBlank { "${exp.fromCity} → ${exp.toCity}" }
            b.tvExpenseAmount.text = "Rs. ${"%.2f".format(exp.amount)}"
            b.tvReceiptRef.text = exp.receiptRef.ifBlank { "–" }
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
