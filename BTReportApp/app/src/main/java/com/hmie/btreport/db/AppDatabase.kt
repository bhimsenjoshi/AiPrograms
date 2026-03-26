package com.hmie.btreport.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import com.hmie.btreport.model.Trip

class Converters {
    @TypeConverter
    fun fromExpenseType(type: ExpenseType): String = type.name

    @TypeConverter
    fun toExpenseType(name: String): ExpenseType = ExpenseType.valueOf(name)
}

@Database(entities = [Trip::class, Expense::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bt_report_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
