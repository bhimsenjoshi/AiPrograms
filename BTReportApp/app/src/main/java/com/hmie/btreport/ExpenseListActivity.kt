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

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().deleteExpense(expense)
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
            // Auto-update trip dates and route after scanning
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

        // Update scan button to show active AI provider name
        val providerName = try {
            AiReceiptService.Provider.valueOf(
                SettingsActivity.getPrefs(this)
                    .getString(SettingsActivity.KEY_AI_PROVIDER, AiReceiptService.Provider.GROQ.name)!!
            ).displayName
        } catch (e: Exception) { "AI" }
        b.btnScanReceipts.text = "📷  Scan Receipts with $providerName"

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
            b.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            val total = list.sumOf { it.amount }
            b.tvTotal.text = "Total: Rs. ${"%.2f".format(total)}"
        }

        // Scan receipts with AI
        b.btnScanReceipts.setOnClickListener {
            scanLauncher.launch(Intent(this, ScanReceiptsActivity::class.java).apply {
                putExtra(ScanReceiptsActivity.EXTRA_TRIP_ID, tripId)
            })
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
