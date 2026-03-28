package com.hmie.btreport.model

data class ReceiptData(
    val expenseType: ExpenseType = ExpenseType.OTHER,
    val date: String = "",
    val departureTime: String = "",  // "HH:mm" 24-hour, blank if not a flight
    val amount: Double = 0.0,
    val description: String = "",
    val fromCity: String = "",
    val toCity: String = "",
    val receiptRef: String = "",
    val operator: String = ""
)
