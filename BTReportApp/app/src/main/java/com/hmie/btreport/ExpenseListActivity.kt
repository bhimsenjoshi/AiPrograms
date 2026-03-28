package com.hmie.btreport

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hmie.btreport.adapter.ExpenseAdapter
import com.hmie.btreport.databinding.ActivityExpenseListBinding
import com.hmie.btreport.db.AppDatabase
import com.hmie.btreport.generator.ExcelGenerator
import com.hmie.btreport.generator.WordGenerator
import com.hmie.btreport.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExpenseListViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    var tripId: Int = 0

    val expenses by lazy { db.expenseDao().getExpensesForTrip(tripId) }
    val trip by lazy {
        androidx.lifecycle.liveData { emit(db.tripDao().getTripById(tripId)) }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().deleteExpense(expense)
    }

    fun removeDuplicates(onDone: (Int) -> Unit) = viewModelScope.launch {
        val all = db.expenseDao().getExpensesForTripSync(tripId)
        val seen = mutableSetOf<String>()
        var removed = 0
        // Sort by id ascending so we keep the earliest entry
        all.sortedBy { it.id }.forEach { exp ->
            // Include route and description so two different journeys on the same
            // date (e.g. two flights on 23rd) are never wrongly merged
            val key = "${exp.type.name}|${exp.date}|${"%.2f".format(exp.amount)}" +
                      "|${exp.fromCity.trim().lowercase()}|${exp.toCity.trim().lowercase()}" +
                      "|${exp.description.trim().lowercase()}"
            if (!seen.add(key)) {
                db.expenseDao().deleteExpense(exp)
                removed++
            }
        }
        onDone(removed)
    }

    suspend fun generateDocs(tripId: Int): Pair<File, File> = withContext(Dispatchers.IO) {
        val trip = db.tripDao().getTripById(tripId) ?: error("Trip not found")
        val expenses = db.expenseDao().getExpensesForTripSync(tripId)
        val xlsx = ExcelGenerator(getApplication()).generate(trip, expenses)
        val docx = WordGenerator(getApplication()).generate(trip, expenses)
        Pair(xlsx, docx)
    }

    fun autoUpdateTripDetails(tripId: Int, onDone: () -> Unit) = viewModelScope.launch {
        val trip = db.tripDao().getTripById(tripId) ?: return@launch
        val expenses = db.expenseDao().getExpensesForTripSync(tripId)
        val summary = AiReceiptService.fromSettings(getApplication()).inferTripSummary(expenses)

        val updated = trip.copy(
            startDate = summary.startDate.ifBlank { trip.startDate },
            endDate = summary.endDate.ifBlank { trip.endDate },
            route = summary.route.ifBlank { trip.route }
        )
        db.tripDao().updateTrip(updated)
        onDone()
    }
}

class ExpenseListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_TRIP_NAME = "trip_name"
    }

    private lateinit var b: ActivityExpenseListBinding
    private val vm: ExpenseListViewModel by viewModels()

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Expenses added from receipts!", Toast.LENGTH_SHORT).show()
            vm.autoUpdateTripDetails(vm.tripId) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityExpenseListBinding.inflate(layoutInflater)
        setContentView(b.root)

        val tripId = intent.getIntExtra(EXTRA_TRIP_ID, 0)
        val tripName = intent.getStringExtra(EXTRA_TRIP_NAME) ?: "Business Trip"
        vm.tripId = tripId

        setSupportActionBar(b.toolbar)
        supportActionBar?.title = tripName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.btnScanReceipts.text = "Scan Receipts"

        val adapter = ExpenseAdapter(
            onEdit = { exp ->
                startActivity(Intent(this, AddExpenseActivity::class.java).apply {
                    putExtra(AddExpenseActivity.EXTRA_TRIP_ID, tripId)
                    putExtra(AddExpenseActivity.EXTRA_EXPENSE_ID, exp.id)
                })
            },
            onDelete = { exp ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Expense")
                    .setMessage("Delete ${exp.type.displayName} – Rs.${"%.2f".format(exp.amount)}?")
                    .setPositiveButton("Delete") { _, _ -> vm.deleteExpense(exp) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        b.rvExpenses.adapter = adapter
        vm.expenses.observe(this) { list ->
            adapter.submitList(list)
            val empty = list.isEmpty()
            b.tvEmpty.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
            b.btnRemoveDuplicates.visibility = if (!empty) android.view.View.VISIBLE else android.view.View.GONE

            val total = list.sumOf { it.amount }
            val cab   = list.filter { it.type == com.hmie.btreport.model.ExpenseType.CAB }.sumOf { it.amount }
            val food  = list.filter { it.type == com.hmie.btreport.model.ExpenseType.FOOD }.sumOf { it.amount }
            val hotel = list.filter { it.type == com.hmie.btreport.model.ExpenseType.HOTEL }.sumOf { it.amount }

            b.tvTotal.text     = "%.0f".format(total)
            b.tvCabTotal.text  = "₹${"%.0f".format(cab)}"
            b.tvFoodTotal.text = "₹${"%.0f".format(food)}"
            b.tvHotelTotal.text = "₹${"%.0f".format(hotel)}"
        }

        // Show flight route from trip
        vm.trip.observe(this) { trip ->
            val route = trip?.route?.trim()
            b.tvFlightRoute.text = if (!route.isNullOrBlank()) route else "—"
        }

        // Scan receipts with AI
        b.btnScanReceipts.setOnClickListener {
            scanLauncher.launch(Intent(this, ScanReceiptsActivity::class.java).apply {
                putExtra(ScanReceiptsActivity.EXTRA_TRIP_ID, tripId)
            })
        }

        // Import from Gmail
        b.btnGmailImport.setOnClickListener {
            vm.viewModelScope.launch {
                val trip = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.hmie.btreport.db.AppDatabase.getDatabase(this@ExpenseListActivity)
                        .tripDao().getTripById(tripId)
                }
                startActivity(Intent(this@ExpenseListActivity, GmailImportActivity::class.java).apply {
                    putExtra(GmailImportActivity.EXTRA_TRIP_ID, tripId)
                    putExtra(GmailImportActivity.EXTRA_START_DATE, trip?.startDate ?: "")
                    putExtra(GmailImportActivity.EXTRA_END_DATE, trip?.endDate ?: "")
                })
            }
        }

        // Remove duplicates
        b.btnRemoveDuplicates.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove Duplicates")
                .setMessage("This will delete expenses with the same type, date, and amount, keeping only the first entry.")
                .setPositiveButton("Remove") { _, _ ->
                    vm.removeDuplicates { removed ->
                        runOnUiThread {
                            if (removed == 0)
                                Toast.makeText(this, "No duplicates found.", Toast.LENGTH_SHORT).show()
                            else
                                Toast.makeText(this, "$removed duplicate(s) removed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Manual add
        b.fab.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java).apply {
                putExtra(AddExpenseActivity.EXTRA_TRIP_ID, tripId)
            })
        }

        b.btnGenerateDocs.setOnClickListener { generateDocuments() }
    }

    private fun generateDocuments() {
        b.btnGenerateDocs.isEnabled = false
        b.btnGenerateDocs.text = "Generating…"
        vm.viewModelScope.launch {
            try {
                val (xlsx, docx) = vm.generateDocs(vm.tripId)
                withContext(Dispatchers.Main) {
                    b.btnGenerateDocs.isEnabled = true
                    b.btnGenerateDocs.text = "Generate Documents"
                    showShareDialog(xlsx, docx)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    b.btnGenerateDocs.isEnabled = true
                    b.btnGenerateDocs.text = "Generate Documents"
                    Toast.makeText(this@ExpenseListActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showShareDialog(xlsx: File, docx: File) {
        AlertDialog.Builder(this)
            .setTitle("Documents Generated")
            .setMessage("${xlsx.name}\n${docx.name}\n\nShare via:")
            .setPositiveButton("Share Both") { _, _ -> shareFiles(listOf(xlsx, docx)) }
            .setNeutralButton("Excel Only") { _, _ -> shareFiles(listOf(xlsx)) }
            .setNegativeButton("Word Only") { _, _ -> shareFiles(listOf(docx)) }
            .show()
    }

    private fun shareFiles(files: List<File>) {
        val uris = ArrayList<Uri>(files.map {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
        })
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share Documents"
        ))
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
