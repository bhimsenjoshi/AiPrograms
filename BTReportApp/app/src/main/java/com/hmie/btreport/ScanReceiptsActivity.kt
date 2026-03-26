package com.hmie.btreport

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hmie.btreport.adapter.ScanResultAdapter
import com.hmie.btreport.databinding.ActivityScanReceiptsBinding
import com.hmie.btreport.db.AppDatabase
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import com.hmie.btreport.model.ReceiptData
import kotlinx.coroutines.launch

data class ScanItem(
    val uri: Uri,
    val fileName: String,
    var status: ScanStatus = ScanStatus.PENDING,
    var result: ReceiptData? = null,
    var error: String? = null
)

enum class ScanStatus { PENDING, SCANNING, SUCCESS, ERROR }

class ScanReceiptsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val items = MutableLiveData<MutableList<ScanItem>>(mutableListOf())
    private val db = AppDatabase.getDatabase(app)

    fun addItems(uris: List<Uri>) {
        val context = getApplication<android.app.Application>()
        val current = items.value ?: mutableListOf()
        uris.forEach { uri ->
            val name = getFileName(context, uri)
            current.add(ScanItem(uri, name))
        }
        items.postValue(current)
    }

    fun scanAll(tripId: Int) = viewModelScope.launch {
        val context = getApplication<android.app.Application>()
        val apiKey = SettingsActivity.getApiKey(context)
        if (apiKey.isBlank()) return@launch

        val service = ClaudeApiService(apiKey)
        val list = items.value ?: return@launch

        list.forEachIndexed { index, item ->
            if (item.status == ScanStatus.PENDING) {
                list[index] = item.copy(status = ScanStatus.SCANNING)
                items.postValue(list.toMutableList())

                try {
                    val result = service.analyzeReceipt(context, item.uri)
                    list[index] = item.copy(status = ScanStatus.SUCCESS, result = result)
                } catch (e: Exception) {
                    list[index] = item.copy(status = ScanStatus.ERROR, error = e.message)
                }
                items.postValue(list.toMutableList())
            }
        }
    }

    fun saveAllExpenses(tripId: Int, onDone: (Int) -> Unit) = viewModelScope.launch {
        val list = items.value ?: return@launch
        var count = 0
        list.filter { it.status == ScanStatus.SUCCESS && it.result != null }.forEach { item ->
            val r = item.result!!
            db.expenseDao().insertExpense(
                Expense(
                    tripId = tripId,
                    type = r.expenseType,
                    date = r.date,
                    description = if (r.description.isNotBlank()) r.description else r.operator,
                    fromCity = r.fromCity,
                    toCity = r.toCity,
                    receiptRef = r.receiptRef,
                    amount = r.amount
                )
            )
            count++
        }
        onDone(count)
    }

    private fun getFileName(context: android.app.Application, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (col >= 0) cursor.getString(col) else uri.lastPathSegment ?: "file"
            } ?: uri.lastPathSegment ?: "file"
        } catch (e: Exception) { uri.lastPathSegment ?: "file" }
    }
}

class ScanReceiptsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
    }

    private lateinit var b: ActivityScanReceiptsBinding
    private val vm: ScanReceiptsViewModel by viewModels()
    private var tripId = 0
    private lateinit var adapter: ScanResultAdapter

    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            vm.addItems(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScanReceiptsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = "Scan Receipts"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tripId = intent.getIntExtra(EXTRA_TRIP_ID, 0)

        // Check API key
        if (SettingsActivity.getApiKey(this).isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Claude API Key Required")
                .setMessage("Please add your Claude API key in Settings to use AI scanning.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .show()
            return
        }

        adapter = ScanResultAdapter()
        b.rvScanResults.layoutManager = LinearLayoutManager(this)
        b.rvScanResults.adapter = adapter

        vm.items.observe(this) { list ->
            adapter.submitList(list.toList())
            val hasItems = list.isNotEmpty()
            b.btnScanAll.isEnabled = hasItems && list.any { it.status == ScanStatus.PENDING }
            b.btnAddAll.isEnabled = list.any { it.status == ScanStatus.SUCCESS }
            b.tvEmpty.visibility = if (!hasItems) View.VISIBLE else View.GONE

            val scanning = list.any { it.status == ScanStatus.SCANNING }
            b.progressBar.visibility = if (scanning) View.VISIBLE else View.GONE
            b.tvScanStatus.text = if (scanning) {
                val done = list.count { it.status == ScanStatus.SUCCESS || it.status == ScanStatus.ERROR }
                "Scanning... $done / ${list.size}"
            } else if (list.isNotEmpty()) {
                val ok = list.count { it.status == ScanStatus.SUCCESS }
                val err = list.count { it.status == ScanStatus.ERROR }
                "$ok scanned successfully${if (err > 0) ", $err failed" else ""}"
            } else ""
        }

        b.btnPickFiles.setOnClickListener {
            pickFiles.launch("*/*")
        }

        b.btnScanAll.setOnClickListener {
            b.btnScanAll.isEnabled = false
            vm.scanAll(tripId)
        }

        b.btnAddAll.setOnClickListener {
            vm.saveAllExpenses(tripId) { count ->
                runOnUiThread {
                    Toast.makeText(this, "$count expenses added!", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
