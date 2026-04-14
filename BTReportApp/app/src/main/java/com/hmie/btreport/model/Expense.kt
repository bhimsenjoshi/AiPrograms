package com.hmie.btreport.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val type: ExpenseType,
    val date: String = "",           // "dd-MMM-yyyy"
    val departureTime: String = "",  // "HH:mm" 24-hour, blank if not applicable
    val description: String = "",
    val fromCity: String = "",
    val toCity: String = "",
    val receiptRef: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",    // ISO 4217 code: INR, KRW, SGD, USD, etc.
    val imageUri: String? = null     // path to locally saved receipt image
)

enum class ExpenseType(val displayName: String) {
    FLIGHT("Air Fare (Flight)"),
    CAB("Local Conveyance (Cab/Auto)"),
    FOOD("Food / Refreshment"),
    HOTEL("Accommodation (Hotel)"),
    OTHER("Other Expense")
}
