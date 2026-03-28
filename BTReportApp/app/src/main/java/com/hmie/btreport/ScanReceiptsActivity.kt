package com.hmie.btreport

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
        val service = AiReceiptService.fromSettings(context)
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

    fun saveAllExpenses(tripId: Int, onDone: (Int, Int) -> Unit) = viewModelScope.launch {
        val context = getApplication<android.app.Application>()
        val list = items.value ?: return@launch
        var saved = 0
        var skipped = 0
        list.filter { it.status == ScanStatus.SUCCESS && it.result != null }.forEach { item ->
            val r = item.result!!
            // Skip if an identical expense (same type + date + amount) already exists
            val dupes = db.expenseDao().findDuplicates(tripId, r.expenseType.name, r.date, r.amount)
            if (dupes.isNotEmpty()) {
                skipped++
                return@forEach
            }
            val savedImage = copyImageToStorage(context, item.uri)
            db.expenseDao().insertExpense(
                Expense(
                    tripId = tripId,
                    type = r.expenseType,
                    date = r.date,
                    description = if (r.description.isNotBlank()) r.description else r.operator,
                    fromCity = r.fromCity,
                    toCity = r.toCity,
                    receiptRef = r.receiptRef,
                    amount = r.amount,
                    imageUri = savedImage
                )
            )
            saved++
        }
        onDone(saved, skipped)
    }

    private fun copyImageToStorage(context: android.app.Application, uri: android.net.Uri): String? {
        return try {
            val dir = java.io.File(context.filesDir, "receipts").also { it.mkdirs() }
            val ext = context.contentResolver.getType(uri)?.let {
                when { it.contains("png") -> "png"; else -> "jpg" }
            } ?: "jpg"
            val dest = java.io.File(dir, "${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            dest.absolutePath
        } catch (e: Exception) { null }
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
        const val EXTRA_INITIAL_URIS = "initial_uris"  // ArrayList<Uri> from Gmail import
    }

    private lateinit var b: ActivityScanReceiptsBinding
    private val vm: ScanReceiptsViewModel by viewModels()
    private var tripId = 0
    private lateinit var adapter: ScanResultAdapter

    private var pendingCameraUri: Uri? = null

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            vm.addItems(uris)
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraUri?.let { vm.addItems(listOf(it)) }
        }
        pendingCameraUri = null
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScanReceiptsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = "Scan Receipts"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tripId = intent.getIntExtra(EXTRA_TRIP_ID, 0)

        // Check if settings configured
        val prefs = SettingsActivity.getPrefs(this)
        val provider = prefs.getString(SettingsActivity.KEY_AI_PROVIDER, null)
        val needsKey = provider != AiReceiptService.Provider.OLLAMA.name
        val keyMissing = needsKey && SettingsActivity.getApiKey(this).isBlank()
        if (provider == null || keyMissing) {
            AlertDialog.Builder(this)
                .setTitle("AI Provider Not Configured")
                .setMessage("Please choose an AI provider in Settings.\nGroq and Gemini are FREE!")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .show()
            return
        }

        supportActionBar?.title = "Scan Receipts"
        b.tvBanner.text = "Upload boarding passes, cab receipts or food bills (images or PDFs). AI will auto-identify type, extract amounts and route."

        adapter = ScanResultAdapter()
        b.rvScanResults.layoutManager = LinearLayoutManager(this)
        b.rvScanResults.adapter = adapter

        // Pre-load URIs passed from Gmail import
        @Suppress("DEPRECATION")
        val initialUris = intent.getParcelableArrayListExtra<android.net.Uri>(EXTRA_INITIAL_URIS)
        if (!initialUris.isNullOrEmpty()) {
            vm.addItems(initialUris)
        }

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

        b.btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        b.btnPickFiles.setOnClickListener {
            pickFiles.launch(arrayOf("image/*", "application/pdf"))
        }

        b.btnScanAll.setOnClickListener {
            b.btnScanAll.isEnabled = false
            vm.scanAll(tripId)
        }

        b.btnAddAll.setOnClickListener {
            vm.saveAllExpenses(tripId) { saved, skipped ->
                runOnUiThread {
                    val msg = if (skipped > 0)
                        "$saved expenses added, $skipped duplicate(s) skipped."
                    else
                        "$saved expenses added!"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun launchCamera() {
        val dir = java.io.File(filesDir, "receipts").also { it.mkdirs() }
        val file = java.io.File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
