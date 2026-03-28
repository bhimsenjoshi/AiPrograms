package com.hmie.btreport

import android.content.Context
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class GmailEmailItem(
    val messageId: String,
    val subject: String,
    val from: String,
    val dateMs: Long,
    val dateStr: String,
    val attachments: List<GmailAttachment>,
    var selected: Boolean = true
)

data class GmailAttachment(
    val attachmentId: String,
    val filename: String,
    val mimeType: String,
    val size: Long
)

class GmailService(private val context: Context) {

    companion object {
        const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        const val OAUTH_SCOPE = "oauth2:$GMAIL_SCOPE"
        private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    }

    private val client = OkHttpClient()

    fun getSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun hasGmailPermission(): Boolean {
        val account = getSignedInAccount() ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(GMAIL_SCOPE))
    }

    // Must be called on IO thread; may throw UserRecoverableAuthException
    @Throws(UserRecoverableAuthException::class)
    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val account = getSignedInAccount()?.account ?: error("Not signed in to Google")
        GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)
    }

    suspend fun fetchExpenseEmails(
        token: String,
        startDate: String,  // "dd-MMM-yyyy"
        endDate: String     // "dd-MMM-yyyy"
    ): Pair<List<GmailEmailItem>, String> = withContext(Dispatchers.IO) {
        val query = buildQuery(startDate, endDate)
        val ids = listMessageIds(token, query)
        val items = ids.mapNotNull { id ->
            try { fetchEmailItem(token, id) } catch (e: Exception) { null }
        }
        Pair(items, query)
    }

    suspend fun downloadAttachment(
        token: String,
        messageId: String,
        attachmentId: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/messages/$messageId/attachments/$attachmentId")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val data = JSONObject(body).optString("data", "")
        Base64.decode(data, Base64.URL_SAFE)
    }

    private fun listMessageIds(token: String, query: String): List<String> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/messages?q=$encoded&maxResults=100")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val json = JSONObject(body)

        // Surface any API-level error instead of silently returning empty list
        json.optJSONObject("error")?.let { err ->
            val code = err.optInt("code", 0)
            val msg  = err.optString("message", "Unknown error")
            throw Exception("Gmail API error $code: $msg")
        }

        val messages = json.optJSONArray("messages") ?: return emptyList()
        return (0 until messages.length()).map { messages.getJSONObject(it).getString("id") }
    }

    private fun fetchEmailItem(token: String, messageId: String): GmailEmailItem? {
        val req = Request.Builder()
            .url("$BASE/messages/$messageId?format=full")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val body = client.newCall(req).execute().use { it.body?.string() ?: return null }
        val msg = JSONObject(body)

        val headers = msg.optJSONObject("payload")?.optJSONArray("headers") ?: return null
        val headerMap = mutableMapOf<String, String>()
        for (i in 0 until headers.length()) {
            val h = headers.getJSONObject(i)
            headerMap[h.getString("name").lowercase()] = h.getString("value")
        }

        val subject = headerMap["subject"] ?: "(no subject)"
        val from = headerMap["from"] ?: ""
        val dateMs = msg.optLong("internalDate", 0L)
        val dateStr = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(java.util.Date(dateMs))

        val attachments = mutableListOf<GmailAttachment>()
        collectAttachments(msg.optJSONObject("payload"), attachments)

        return GmailEmailItem(messageId, subject, from, dateMs, dateStr, attachments)
    }

    private fun collectAttachments(part: JSONObject?, result: MutableList<GmailAttachment>) {
        if (part == null) return
        val filename = part.optString("filename", "")
        val mimeType = part.optString("mimeType", "")
        val body = part.optJSONObject("body")
        val attachId = body?.optString("attachmentId", "") ?: ""
        val size = body?.optLong("size", 0L) ?: 0L

        if (filename.isNotBlank() && attachId.isNotBlank()) {
            val ext = filename.substringAfterLast('.', "").lowercase()
            if (ext in listOf("jpg", "jpeg", "png", "pdf", "gif", "webp")) {
                result.add(GmailAttachment(attachId, filename, mimeType, size))
            }
        }

        val parts = part.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            collectAttachments(parts.getJSONObject(i), result)
        }
    }

    fun buildQuery(startDate: String, endDate: String): String {
        val fmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val cal = Calendar.getInstance()

        val start = try {
            cal.time = fmt.parse(startDate)!!
            "${cal.get(Calendar.YEAR)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
        } catch (e: Exception) { "" }

        val end = try {
            cal.time = fmt.parse(endDate)!!
            cal.add(Calendar.DAY_OF_MONTH, 1)
            "${cal.get(Calendar.YEAR)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
        } catch (e: Exception) { "" }

        val dateFilter = if (start.isNotBlank() && end.isNotBlank()) "after:$start before:$end " else ""

        // Always filter to expense-related emails only
        val keywords = "(" +
            // Subjects
            "subject:ticket OR subject:\"boarding pass\" OR subject:itinerary OR " +
            "subject:receipt OR subject:invoice OR subject:bill OR " +
            "subject:\"booking confirmation\" OR subject:\"payment confirmation\" OR " +
            "subject:\"e-ticket\" OR subject:\"trip confirmation\" OR " +
            "subject:cab OR subject:ride OR subject:\"your trip\" OR " +
            // Flight senders
            "from:goindigo.in OR from:spicejet.com OR from:airindia.in OR " +
            "from:airvistara.com OR from:akasaair.com OR from:starair.in OR " +
            // Travel portals
            "from:makemytrip.com OR from:goibibo.com OR from:cleartrip.com OR " +
            "from:yatra.com OR from:ixigo.com OR from:irctc.co.in OR " +
            // Cab / ride
            "from:olacabs.com OR from:uber.com OR from:rapido.bike OR " +
            "from:jugnoo.in OR from:meru.in OR " +
            // Hotel
            "from:oyorooms.com OR from:treebo.com OR from:fabhotels.com OR " +
            "from:hotels.com OR from:booking.com OR from:airbnb.com OR " +
            // Food
            "from:swiggy.in OR from:zomato.com" +
            ")"

        return "$dateFilter$keywords"
    }
}
