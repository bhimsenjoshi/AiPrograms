package com.hmie.btreport.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeName: String = "",
    val employeeId: String = "",
    val department: String = "",
    val designation: String = "",
    val costCenter: String = "",
    val startDate: String = "",   // "dd-MMM-yyyy"
    val endDate: String = "",
    val purpose: String = "",
    val route: String = "",       // e.g. "HYD-BLR-PNQ-HYD"
    val createdAt: Long = System.currentTimeMillis()
)
