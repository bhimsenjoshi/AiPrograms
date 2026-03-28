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

    @Query("""SELECT * FROM expenses WHERE tripId = :tripId AND type = :type AND date = :date
              AND ROUND(amount,2) = ROUND(:amount,2)
              AND LOWER(TRIM(fromCity))  = LOWER(TRIM(:fromCity))
              AND LOWER(TRIM(toCity))    = LOWER(TRIM(:toCity))
              AND LOWER(TRIM(description)) = LOWER(TRIM(:description))""")
    suspend fun findDuplicates(
        tripId: Int, type: String, date: String, amount: Double,
        fromCity: String, toCity: String, description: String
    ): List<Expense>

    /**
     * Flight-specific duplicate check: same route on same date = same flight.
     * Ignores amount (often 0 on boarding passes) and description (varies between scans).
     */
    @Query("""SELECT * FROM expenses WHERE tripId = :tripId AND type = 'FLIGHT'
              AND date = :date
              AND LOWER(TRIM(fromCity)) = LOWER(TRIM(:fromCity))
              AND LOWER(TRIM(toCity))   = LOWER(TRIM(:toCity))""")
    suspend fun findFlightDuplicates(
        tripId: Int, date: String, fromCity: String, toCity: String
    ): List<Expense>
}
