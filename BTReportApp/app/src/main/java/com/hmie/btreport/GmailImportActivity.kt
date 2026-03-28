package com.hmie.btreport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.hmie.btreport.adapter.GmailEmailAdapter
import com.hmie.btreport.databinding.ActivityGmailImportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class GmailUiState {
    object Idle : GmailUiState()
    object SignedOut : GmailUiState()
    object Loading : GmailUiState()
    data class AuthRequired(val intent: Intent) : GmailUiState()
    data class EmailList(val items: List<GmailEmailItem>) : GmailUiState()
    data class Error(val msg: String) : GmailUiState()
}

class GmailImportViewModel(app: android.app.Application) : AndroidViewModel(app) {

    val state = MutableLiveData<GmailUiState>(GmailUiState.Idle)
    private val service = GmailService(app)

    fun checkSignIn() {
        // Only show sign-in screen if there is NO Google account on this device at all.
        // If an account exists but Gmail permission hasn't been granted yet, the
        // fetchEmails() → getAccessToken() call will throw UserRecoverableAuthException
        // and we'll handle the consent screen from there — no repeated account picker.
        state.value = if (service.getSignedInAccount() == null) GmailUiState.SignedOut
                      else GmailUiState.Idle
    }

    fun fetchEmails(startDate: String, endDate: String) = viewModelScope.launch {
        state.value = GmailUiState.Loading
        try {
            val token = service.getAccessToken()
            val emails = service.fetchExpenseEmails(token, startDate, endDate)
            state.value = GmailUiState.EmailList(emails)
        } catch (e: UserRecoverableAuthException) {
            val recoveryIntent = e.intent
            if (recoveryIntent != null) {
                state.value = GmailUiState.AuthRequired(recoveryIntent)
            } else {
                state.value = GmailUiState.Error("Gmail permission required. Please sign in again.")
            }
        } catch (e: Exception) {
            state.value = GmailUiState.Error(e.message ?: "Unknown error")
        }
    }

    fun downloadAndSave(
        emails: List<GmailEmailItem>,
        onProgress: (String) -> Unit,
        onDone: (List<Uri>) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<android.app.Application>()
        val token = try {
            service.getAccessToken()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onDone(emptyList()) }
            return@launch
        }

        val dir = File(context.filesDir, "receipts").also { it.mkdirs() }
        val uris = mutableListOf<Uri>()

        emails.filter { it.selected }.forEach { email ->
            email.attachments.forEach { att ->
                try {
                    withContext(Dispatchers.Main) { onProgress("Downloading: ${att.filename}") }
                    val bytes = service.downloadAttachment(token, email.messageId, att.attachmentId)
                    val file = File(dir, "${System.currentTimeMillis()}_${att.filename.replace("/", "_")}")
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    uris.add(uri)
                } catch (e: Exception) {
                    // skip failed attachments, continue with others
                }
            }
        }
        withContext(Dispatchers.Main) { onDone(uris) }
    }
}

class GmailImportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRIP_ID   = "trip_id"
        const val EXTRA_START_DATE = "start_date"
        const val EXTRA_END_DATE   = "end_date"
    }

    private lateinit var b: ActivityGmailImportBinding
    private val vm: GmailImportViewModel by viewModels()
    private var tripId = 0
    private var startDate = ""
    private var endDate = ""
    private lateinit var emailAdapter: GmailEmailAdapter

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            try {
                GoogleSignIn.getSignedInAccountFromIntent(data)
                    .addOnSuccessListener {
                        vm.fetchEmails(startDate, endDate)
                    }
                    .addOnFailureListener { e ->
                        val msg = if (e is ApiException && e.statusCode == 10) {
                            "Google OAuth not set up for this app.\n\n" +
                            "To fix:\n1. Go to console.cloud.google.com\n" +
                            "2. Enable Gmail API\n" +
                            "3. Create OAuth client (Android) with package:\n   com.hmie.btreport\n" +
                            "4. Add your SHA-1 fingerprint\n5. Add your email as test user"
                        } else {
                            "Sign in failed (code ${(e as? ApiException)?.statusCode}): ${e.message}"
                        }
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Google Sign-In Failed")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                        vm.checkSignIn()
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Sign in error: ${e.message}", Toast.LENGTH_LONG).show()
                vm.checkSignIn()
            }
        }
    }

    private val authRecovery = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) vm.fetchEmails(startDate, endDate)
        else Toast.makeText(this, "Gmail permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGmailImportBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = "Import from Gmail"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tripId    = intent.getIntExtra(EXTRA_TRIP_ID, 0)
        startDate = intent.getStringExtra(EXTRA_START_DATE) ?: ""
        endDate   = intent.getStringExtra(EXTRA_END_DATE) ?: ""

        b.tvDateRange.text = if (startDate.isNotBlank() && endDate.isNotBlank())
            "Trip: $startDate – $endDate" else "All recent emails"

        emailAdapter = GmailEmailAdapter()
        b.rvEmails.layoutManager = LinearLayoutManager(this)
        b.rvEmails.adapter = emailAdapter

        b.btnSignIn.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(GmailService.GMAIL_SCOPE))
                .build()
            signInLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
        }

        b.btnFetchEmails.setOnClickListener {
            vm.fetchEmails(startDate, endDate)
        }

        b.btnSelectAll.setOnClickListener {
            emailAdapter.getSelectedItems().let { /* handled via toggleAll */ }
            toggleSelectAll()
        }

        b.btnImportSelected.setOnClickListener {
            val selected = emailAdapter.getSelectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            b.btnImportSelected.isEnabled = false
            b.btnImportSelected.text = "Downloading…"
            vm.downloadAndSave(
                selected,
                onProgress = { msg -> b.tvStatus.text = msg },
                onDone = { uris ->
                    if (uris.isEmpty()) {
                        Toast.makeText(this, "No downloadable attachments found", Toast.LENGTH_SHORT).show()
                        b.btnImportSelected.isEnabled = true
                        b.btnImportSelected.text = "Scan Selected"
                    } else {
                        startActivity(Intent(this, ScanReceiptsActivity::class.java).apply {
                            putExtra(ScanReceiptsActivity.EXTRA_TRIP_ID, tripId)
                            putParcelableArrayListExtra(ScanReceiptsActivity.EXTRA_INITIAL_URIS, ArrayList(uris))
                        })
                        finish()
                    }
                }
            )
        }

        vm.state.observe(this) { renderState(it) }
        vm.checkSignIn()
    }

    private var allSelected = true
    private fun toggleSelectAll() {
        allSelected = !allSelected
        val items = emailAdapter.getSelectedItems().let { emailAdapter }
        // Re-submit list with toggled selection
        val currentItems = (vm.state.value as? GmailUiState.EmailList)?.items ?: return
        currentItems.forEach { it.selected = allSelected }
        emailAdapter.submitList(currentItems)
        b.btnSelectAll.text = if (allSelected) "Deselect All" else "Select All"
    }

    private fun renderState(state: GmailUiState) {
        b.groupSignIn.visibility = View.GONE
        b.groupContent.visibility = View.GONE
        b.progressBar.visibility = View.GONE

        when (state) {
            is GmailUiState.SignedOut -> {
                b.groupSignIn.visibility = View.VISIBLE
                b.tvStatus.text = "Sign in with Google to search your Gmail for boarding passes, cab receipts, hotel invoices and food bills during this business trip."
            }
            is GmailUiState.Idle -> {
                b.groupContent.visibility = View.VISIBLE
                b.tvStatus.text = "Tap 'Fetch Emails' to search Gmail for expense-related emails in the trip date range."
                b.btnFetchEmails.isEnabled = true
                b.btnImportSelected.isEnabled = false
                b.btnSelectAll.visibility = View.GONE
            }
            is GmailUiState.Loading -> {
                b.groupContent.visibility = View.VISIBLE
                b.progressBar.visibility = View.VISIBLE
                b.tvStatus.text = "Searching Gmail…"
                b.btnFetchEmails.isEnabled = false
                b.btnImportSelected.isEnabled = false
                b.btnSelectAll.visibility = View.GONE
            }
            is GmailUiState.AuthRequired -> {
                authRecovery.launch(state.intent)
            }
            is GmailUiState.EmailList -> {
                b.groupContent.visibility = View.VISIBLE
                b.btnFetchEmails.isEnabled = true
                emailAdapter.submitList(state.items)
                val count = state.items.size
                b.tvStatus.text = if (count == 0)
                    "No expense-related emails found in this date range. Try fetching without dates set."
                else
                    "$count email(s) found. Tick the ones to import, then tap 'Scan Selected'."
                b.btnImportSelected.isEnabled = count > 0
                b.btnSelectAll.visibility = if (count > 1) View.VISIBLE else View.GONE
                allSelected = true
                b.btnSelectAll.text = "Deselect All"
            }
            is GmailUiState.Error -> {
                b.groupContent.visibility = View.VISIBLE
                b.btnFetchEmails.isEnabled = true
                b.tvStatus.text = when {
                    state.msg.contains("403") || state.msg.contains("PERMISSION") ->
                        "Gmail access denied (403). Enable Gmail API in Google Cloud Console and add your email as a test user."
                    state.msg.contains("401") || state.msg.contains("UNAUTHENTICATED") ->
                        "Authentication expired. Tap 'Fetch Emails' to sign in again."
                    state.msg.contains("Not signed in") ->
                        "Not signed in. Tap 'Fetch Emails' to sign in with Google."
                    else -> "Error: ${state.msg}"
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
