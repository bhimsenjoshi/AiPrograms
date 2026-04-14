package com.hmie.btreport

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hmie.btreport.databinding.ActivityAddExpenseBinding
import com.hmie.btreport.db.AppDatabase
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    var existingExpense: Expense? = null

    fun loadExpense(id: Int, onLoaded: (Expense) -> Unit) = viewModelScope.launch {
        db.expenseDao().getExpensesForTripSync(0) // dummy, use direct query below
        // We load by iterating – in a real app add a getById query in the DAO
    }

    fun loadExpenseById(tripId: Int, expId: Int, onLoaded: (Expense) -> Unit) = viewModelScope.launch {
        val all = db.expenseDao().getExpensesForTripSync(tripId)
        all.find { it.id == expId }?.let {
            existingExpense = it
            onLoaded(it)
        }
    }

    fun saveExpense(expense: Expense, onDone: () -> Unit) = viewModelScope.launch {
        if (expense.id == 0) db.expenseDao().insertExpense(expense)
        else db.expenseDao().updateExpense(expense)
        onDone()
    }
}

class AddExpenseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_EXPENSE_ID = "expense_id"
    }

    private lateinit var b: ActivityAddExpenseBinding
    private val vm: AddExpenseViewModel by viewModels()
    private val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tripId = intent.getIntExtra(EXTRA_TRIP_ID, 0)
        val expId = intent.getIntExtra(EXTRA_EXPENSE_ID, 0)

        // Expense type spinner
        val types = ExpenseType.values().map { it.displayName }
        b.spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Default date = today
        b.etDate.setText(sdf.format(Date()))
        b.etDate.setOnClickListener { pickDate { b.etDate.setText(it) } }

        if (expId != 0) {
            supportActionBar?.title = "Edit Expense"
            vm.loadExpenseById(tripId, expId) { exp -> runOnUiThread { populate(exp) } }
        } else {
            supportActionBar?.title = "Add Expense"
        }

        b.btnSave.setOnClickListener { saveExpense(tripId) }
    }

    private fun populate(exp: Expense) {
        b.spinnerType.setSelection(ExpenseType.values().indexOfFirst { it == exp.type })
        b.etDate.setText(exp.date)
        b.etDepartureTime.setText(exp.departureTime)
        b.etDescription.setText(exp.description)
        b.etFromCity.setText(exp.fromCity)
        b.etToCity.setText(exp.toCity)
        b.etReceiptRef.setText(exp.receiptRef)
        b.etAmount.setText(exp.amount.toString())
        b.etCurrency.setText(exp.currency)
    }

    private fun saveExpense(tripId: Int) {
        val amountStr = b.etAmount.text.toString().trim()
        if (amountStr.isBlank()) { b.etAmount.error = "Required"; return }
        val amount = amountStr.toDoubleOrNull() ?: run {
            b.etAmount.error = "Invalid number"; return
        }
        val type = ExpenseType.values()[b.spinnerType.selectedItemPosition]

        val currencyRaw = b.etCurrency.text.toString().trim().uppercase()
        val currency = if (currencyRaw.length == 3) currencyRaw else "INR"
        val expense = Expense(
            id = vm.existingExpense?.id ?: 0,
            tripId = tripId,
            type = type,
            date = b.etDate.text.toString().trim(),
            departureTime = b.etDepartureTime.text.toString().trim(),
            description = b.etDescription.text.toString().trim(),
            fromCity = b.etFromCity.text.toString().trim(),
            toCity = b.etToCity.text.toString().trim(),
            receiptRef = b.etReceiptRef.text.toString().trim(),
            amount = amount,
            currency = currency
        )
        vm.saveExpense(expense) {
            runOnUiThread {
                Toast.makeText(this, "Expense saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            c.set(y, m, d)
            onPicked(sdf.format(c.time))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
