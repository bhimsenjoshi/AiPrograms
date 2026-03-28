package com.hmie.btreport.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hmie.btreport.model.Expense

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date ASC, id ASC")
    fun getExpensesForTrip(tripId: Int): LiveData<List<Expense>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date ASC, id ASC")
    suspend fun getExpensesForTripSync(tripId: Int): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND type = :type AND date = :date AND ROUND(amount,2) = ROUND(:amount,2)")
    suspend fun findDuplicates(tripId: Int, type: String, date: String, amount: Double): List<Expense>
}
