package com.hmie.btreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import com.hmie.btreport.model.ReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class ClaudeApiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeReceipt(context: Context, uri: Uri): ReceiptData = withContext(Dispatchers.IO) {
        val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = readAndCompressFile(context, uri, contentType)
        val base64 = Base64.getEncoder().encodeToString(bytes)

        val contentArray = JSONArray()

        // Add document or image block
        if (contentType == "application/pdf") {
            contentArray.put(JSONObject().apply {
                put("type", "document")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "application/pdf")
                    put("data", base64)
                })
            })
        } else {
            val mt = if (contentType.startsWith("image/")) contentType else "image/jpeg"
            contentArray.put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", mt)
                    put("data", base64)
                })
            })
        }

        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", RECEIPT_PROMPT)
        })

        val requestBody = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            }))
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("anthropic-beta", "pdfs-2024-09-25")
            .addHeader("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from Claude")

        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody
            } catch (e: Exception) { responseBody }
            throw Exception("Claude API error ${response.code}: $errMsg")
        }

        val text = JSONObject(responseBody)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()

        parseReceiptData(text)
    }

    /** Auto-calculate trip start/end dates and route from expense list */
    fun inferTripSummary(expenses: List<Expense>): TripSummary {
        if (expenses.isEmpty()) return TripSummary("", "", "")

        val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.ENGLISH)

        val sortedDates = expenses.mapNotNull {
            try { sdf.parse(it.date) } catch (e: Exception) { null }
        }.sorted()

        val startDate = sortedDates.firstOrNull()?.let { sdf.format(it) } ?: ""
        val endDate = sortedDates.lastOrNull()?.let { sdf.format(it) } ?: ""

        // Build route from flight legs sorted by date
        val flights = expenses
            .filter { it.type == ExpenseType.FLIGHT && it.fromCity.isNotBlank() && it.toCity.isNotBlank() }
            .sortedWith(compareBy {
                try { sdf.parse(it.date)?.time ?: 0L } catch (e: Exception) { 0L }
            })

        val route = if (flights.isNotEmpty()) {
            val cities = mutableListOf(flights.first().fromCity.uppercase())
            flights.forEach { cities.add(it.toCity.uppercase()) }
            // Deduplicate consecutive same cities but keep sequence
            cities.distinct().joinToString("-")
        } else ""

        return TripSummary(startDate, endDate, route)
    }

    data class TripSummary(val startDate: String, val endDate: String, val route: String)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun readAndCompressFile(context: Context, uri: Uri, contentType: String): ByteArray {
        if (contentType == "application/pdf") {
            return context.contentResolver.openInputStream(uri)?.readBytes()
                ?: throw Exception("Cannot read PDF")
        }
        // For images: compress to stay within ~4MB base64 limit
        val rawBytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: throw Exception("Cannot read image")

        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return rawBytes // fallback to raw if decode fails

        val out = ByteArrayOutputStream()
        var quality = 90
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)

        // Reduce quality until under 3MB raw (~4MB base64)
        while (out.size() > 3 * 1024 * 1024 && quality > 30) {
            out.reset()
            quality -= 20
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return out.toByteArray()
    }

    private fun parseReceiptData(jsonText: String): ReceiptData {
        val clean = jsonText
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        return try {
            val json = JSONObject(clean)
            val typeStr = json.optString("expense_type", "OTHER").uppercase()
            val type = try { ExpenseType.valueOf(typeStr) } catch (e: Exception) { ExpenseType.OTHER }
            ReceiptData(
                expenseType = type,
                date = json.optString("date", ""),
                amount = json.optDouble("amount", 0.0),
                description = buildDescription(json),
                fromCity = json.optString("from_city", "").uppercase(),
                toCity = json.optString("to_city", "").uppercase(),
                receiptRef = json.optString("receipt_ref", ""),
                operator = json.optString("operator", "")
            )
        } catch (e: Exception) {
            ReceiptData(description = "Parse error: ${e.message}")
        }
    }

    private fun buildDescription(json: JSONObject): String {
        val desc = json.optString("description", "")
        val op = json.optString("operator", "")
        return when {
            desc.isNotBlank() -> desc
            op.isNotBlank() -> op
            else -> ""
        }
    }

    companion object {
        private const val RECEIPT_PROMPT = """Analyze this business travel expense document for an HMIE employee and extract details.

Return ONLY a valid JSON object (no markdown, no explanation):
{
  "expense_type": "FLIGHT" or "CAB" or "FOOD" or "HOTEL" or "OTHER",
  "date": "dd-MMM-yyyy e.g. 22-Mar-2026",
  "amount": total amount as number in INR (0 if not visible),
  "description": "concise description e.g. IX-2934 HYD to BLR or Chai Point BLR Airport",
  "from_city": "departure or pickup city/IATA code e.g. HYD",
  "to_city": "arrival or drop city/IATA code e.g. BLR",
  "receipt_ref": "flight number or booking ID or bill number e.g. IX 2934 or 6E-441",
  "operator": "airline or cab service or restaurant e.g. Air India Express, IndiGo, Rapido, Chai Point"
}

Guidelines:
- Boarding pass → FLIGHT. Use IATA airport codes (HYD, BLR, PNQ, DEL, BOM) for cities. Flight number as receipt_ref.
- Cab/auto/taxi receipt (Ola, Uber, Rapido, QuickRide, MyGate) → CAB. Pickup as from_city, drop as to_city.
- Restaurant/cafe/food court bill → FOOD.
- Hotel/accommodation → HOTEL.
- amount must be the final total paid (include GST if shown)."""
    }
}
