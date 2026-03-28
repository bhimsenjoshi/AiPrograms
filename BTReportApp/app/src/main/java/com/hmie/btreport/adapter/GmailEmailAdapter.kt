package com.hmie.btreport.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hmie.btreport.GmailEmailItem
import com.hmie.btreport.databinding.ItemGmailEmailBinding

class GmailEmailAdapter : RecyclerView.Adapter<GmailEmailAdapter.VH>() {

    private val items = mutableListOf<GmailEmailItem>()

    fun submitList(newItems: List<GmailEmailItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<GmailEmailItem> = items.filter { it.selected }

    inner class VH(private val b: ItemGmailEmailBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: GmailEmailItem) {
            b.tvSubject.text = item.subject.ifBlank { "(no subject)" }

            // Clean up the "From" field — show name only if present
            val fromName = item.from.substringBefore("<").trim().removeSurrounding("\"")
            b.tvFrom.text = if (fromName.isNotBlank()) fromName else item.from
            b.tvDate.text = item.dateStr

            val attCount = item.attachments.size
            b.tvAttachments.text = when {
                attCount == 0 -> "No attachments"
                attCount == 1 -> "📎 1 attachment"
                else -> "📎 $attCount attachments"
            }

            b.checkbox.setOnCheckedChangeListener(null)
            b.checkbox.isChecked = item.selected
            b.checkbox.setOnCheckedChangeListener { _, checked -> item.selected = checked }

            b.root.setOnClickListener {
                item.selected = !item.selected
                b.checkbox.isChecked = item.selected
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemGmailEmailBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
