package com.hmie.btreport.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hmie.btreport.databinding.ItemTripBinding
import com.hmie.btreport.model.Trip

class TripAdapter(
    private val onEdit: (Trip) -> Unit,
    private val onOpen: (Trip) -> Unit,
    private val onDelete: (Trip) -> Unit
) : ListAdapter<Trip, TripAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemTripBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(trip: Trip) {
            b.tvTripName.text = trip.purpose.ifBlank { "Business Trip" }
            b.tvTripRoute.text = trip.route
            b.tvTripDates.text = "${trip.startDate}  –  ${trip.endDate}"
            b.tvEmployee.text = trip.employeeName
            b.root.setOnClickListener { onOpen(trip) }
            b.btnEdit.setOnClickListener { onEdit(trip) }
            b.btnDelete.setOnClickListener { onDelete(trip) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Trip>() {
            override fun areItemsTheSame(a: Trip, b: Trip) = a.id == b.id
            override fun areContentsTheSame(a: Trip, b: Trip) = a == b
        }
    }
}
