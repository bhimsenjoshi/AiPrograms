package com.hmie.btreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Unified AI receipt scanning service.
 *
 * Supported providers:
 *  GROQ    – Free. Get key at console.groq.com. Uses Llama 4 Scout vision.
 *  GEMINI  – Free. Get key at aistudio.google.com. Uses gemini-1.5-flash.
 *  OLLAMA  – Local. No key. Run Ollama on your PC with a vision model.
 *  CLAUDE  – Paid. Get key at console.anthropic.com.
 */
class AiReceiptService(private val config: Config) {

    data class Config(
        val provider: Provider,
        val apiKey: String = "",
        val ollamaEndpoint: String = "http://localhost:11434",
        val ollamaModel: String = "llava:latest"
    )

    enum class Provider(val displayName: String) {
        GROQ("Groq (Free – Llama 4 Scout)"),
        GEMINI("Google Gemini (Free)"),
        OLLAMA("Ollama (Local – No key)"),
        CLAUDE("Claude (Paid)")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // ── Public ────────────────────────────────────────────────────────────────

    suspend fun analyzeReceipt(context: Context, uri: Uri): ReceiptData = withContext(Dispatchers.IO) {
        val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val imageBytes = if (contentType == "application/pdf") pdfToImageBytes(context, uri)
                         else compressImage(context, uri)
        val base64 = Base64.getEncoder().encodeToString(imageBytes)

        when (config.provider) {
            Provider.GROQ   -> callGroq(base64)
            Provider.GEMINI -> callGemini(base64)
            Provider.OLLAMA -> callOllama(base64)
            Provider.CLAUDE -> callClaude(base64)
        }
    }

    fun inferTripSummary(expenses: List<Expense>): TripSummary {
        if (expenses.isEmpty()) return TripSummary("", "", "")
        val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.ENGLISH)

        val sortedDates = expenses.mapNotNull { parseAnyDate(it.date) }.sorted()
        val startDate = sortedDates.firstOrNull()?.let { sdf.format(it) } ?: ""
        val endDate   = sortedDates.lastOrNull()?.let  { sdf.format(it) } ?: ""

        val flights = expenses
            .filter { it.type == ExpenseType.FLIGHT && it.fromCity.isNotBlank() && it.toCity.isNotBlank() }
            .sortedWith(compareBy(
                { parseAnyDate(it.date)?.time ?: Long.MAX_VALUE },
                { timeToMinutes(it.departureTime) },
                { it.id }   // tiebreaker: scan order (lower id = scanned first)
            ))

        val route = if (flights.isNotEmpty()) {
            // Build a chain: add fromCity of first flight, then each toCity in order.
            // Skip consecutive duplicates so a layover city isn't repeated.
            val cities = mutableListOf(flights.first().fromCity.uppercase())
            flights.forEach { f ->
                val to = f.toCity.uppercase()
                if (cities.last() != to) cities.add(to)
            }
            cities.joinToString("-")
        } else ""

        return TripSummary(startDate, endDate, route)
    }

    data class TripSummary(val startDate: String, val endDate: String, val route: String)

    // ── Date / time helpers ───────────────────────────────────────────────────

    private fun parseAnyDate(s: String): java.util.Date? {
        val fmts = listOf(
            "dd-MMM-yyyy", "dd-MMM-yy",
            "yyyy-MM-dd",
            "dd/MM/yyyy", "MM/dd/yyyy",
            "dd-MM-yyyy",
            "dd MMM yyyy", "d MMM yyyy",
            "MMMM d, yyyy", "d MMMM yyyy"
        )
        for (f in fmts) try {
            return java.text.SimpleDateFormat(f, java.util.Locale.ENGLISH).parse(s)
        } catch (_: Exception) {}
        return null
    }

    /** Returns departure time in minutes since midnight; 0 if blank/unparseable (sorts first). */
    private fun timeToMinutes(t: String): Int {
        if (t.isBlank()) return 0
        return try {
            val parts = t.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (_: Exception) { 0 }
    }

    // ── Groq (OpenAI-compatible, free) ───────────────────────────────────────

    private fun callGroq(base64: String): ReceiptData {
        val model = "meta-llama/llama-4-scout-17b-16e-instruct"
        return callOpenAiCompatible(
            url = "https://api.groq.com/openai/v1/chat/completions",
            model = model,
            authHeader = "Bearer ${config.apiKey}",
            base64 = base64
        )
    }

    // ── Gemini ───────────────────────────────────────────────────────────────

    private fun callGemini(base64: String): ReceiptData {
        val model = "gemini-1.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.apiKey}"

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64)
                        })
                    })
                    put(JSONObject().apply { put("text", RECEIPT_PROMPT) })
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)
                put("temperature", 0.1)
            })
        }.toString()

        val response = client.newCall(
            Request.Builder().url(url)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val rb = response.body?.string() ?: throw Exception("Empty Gemini response")
        if (!response.isSuccessful) {
            val msg = try { JSONObject(rb).optJSONObject("error")?.optString("message") ?: rb } catch (e: Exception) { rb }
            throw Exception("Gemini error ${response.code}: $msg")
        }

        val text = JSONObject(rb)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()

        return parseReceiptData(text)
    }

    // ── Ollama (local) ───────────────────────────────────────────────────────

    private fun callOllama(base64: String): ReceiptData {
        val body = JSONObject().apply {
            put("model", config.ollamaModel)
            put("stream", false)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", RECEIPT_PROMPT)
                put("images", JSONArray().put(base64))
            }))
        }.toString()

        val response = client.newCall(
            Request.Builder()
                .url("${config.ollamaEndpoint.trimEnd('/')}/api/chat")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val rb = response.body?.string() ?: throw Exception("Empty Ollama response")
        if (!response.isSuccessful) throw Exception("Ollama error ${response.code}: $rb")

        val text = JSONObject(rb).getJSONObject("message").getString("content").trim()
        return parseReceiptData(text)
    }

    // ── Claude ───────────────────────────────────────────────────────────────

    private fun callClaude(base64: String): ReceiptData {
        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", base64)
                        })
                    })
                    put(JSONObject().put("type", "text").put("text", RECEIPT_PROMPT))
                })
            }))
        }.toString()

        val response = client.newCall(
            Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val rb = response.body?.string() ?: throw Exception("Empty Claude response")
        if (!response.isSuccessful) {
            val msg = try { JSONObject(rb).optJSONObject("error")?.optString("message") ?: rb } catch (e: Exception) { rb }
            throw Exception("Claude error ${response.code}: $msg")
        }

        val text = JSONObject(rb).getJSONArray("content").getJSONObject(0).getString("text").trim()
        return parseReceiptData(text)
    }

    // ── OpenAI-compatible helper (used by Groq) ───────────────────────────────

    private fun callOpenAiCompatible(url: String, model: String, authHeader: String, base64: String): ReceiptData {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
                    })
                    put(JSONObject().put("type", "text").put("text", RECEIPT_PROMPT))
                })
            }))
        }.toString()

        val response = client.newCall(
            Request.Builder().url(url)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val rb = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) {
            val msg = try { JSONObject(rb).optJSONObject("error")?.optString("message") ?: rb } catch (e: Exception) { rb }
            throw Exception("API error ${response.code}: $msg")
        }

        val text = JSONObject(rb)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        return parseReceiptData(text)
    }

    // ── PDF → image ───────────────────────────────────────────────────────────

    private fun pdfToImageBytes(context: Context, uri: Uri): ByteArray {
        val tmp = File.createTempFile("rcpt", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { tmp.outputStream().use { o -> it.copyTo(o) } }

        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val page = renderer.openPage(0)
        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close(); renderer.close(); tmp.delete()

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray {
        val raw = context.contentResolver.openInputStream(uri)?.readBytes() ?: throw Exception("Cannot read image")
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
        val out = ByteArrayOutputStream()
        var q = 85
        bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
        while (out.size() > 3 * 1024 * 1024 && q > 30) {
            out.reset(); q -= 15
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
        }
        return out.toByteArray()
    }

    // ── JSON parser ───────────────────────────────────────────────────────────

    private fun parseReceiptData(rawText: String): ReceiptData {
        val clean = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val json = JSONObject(clean)
            val type = try {
                ExpenseType.valueOf(json.optString("expense_type", "OTHER").uppercase())
            } catch (e: Exception) { ExpenseType.OTHER }
            ReceiptData(
                expenseType = type,
                date = json.optString("date", ""),
                departureTime = json.optString("departure_time", ""),
                amount = json.optDouble("amount", 0.0),
                description = json.optString("description", json.optString("operator", "")),
                fromCity = json.optString("from_city", "").uppercase(),
                toCity = json.optString("to_city", "").uppercase(),
                receiptRef = json.optString("receipt_ref", ""),
                operator = json.optString("operator", "")
            )
        } catch (e: Exception) {
            ReceiptData(description = "Parse error: ${rawText.take(200)}")
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        const val RECEIPT_PROMPT = """Analyze this business travel expense receipt/document.

Return ONLY a JSON object (no markdown, no extra text):
{
  "expense_type": "FLIGHT" or "CAB" or "FOOD" or "HOTEL" or "OTHER",
  "date": "dd-MMM-yyyy e.g. 22-Mar-2026",
  "departure_time": "HH:mm 24-hour departure/boarding time e.g. 06:20, blank string if not a flight or not visible",
  "amount": total amount paid as a number in INR,
  "description": "short description e.g. IX-2934 HYD-BLR or Chai Point BLR Airport",
  "from_city": "departure or pickup IATA city code e.g. HYD",
  "to_city": "arrival or drop IATA city code e.g. BLR",
  "receipt_ref": "flight number, booking ID, or bill number",
  "operator": "airline, cab service, or restaurant name"
}

Rules:
- Boarding pass → FLIGHT. IATA codes: HYD=Hyderabad, BLR=Bengaluru, PNQ=Pune, DEL=Delhi, BOM=Mumbai.
- For flights: departure_time is the scheduled departure time printed on the boarding pass (24-hour HH:mm).
- Cab receipt (Rapido/QuickRide/Ola/Uber/auto) → CAB.
- Restaurant/cafe/food court → FOOD.
- Hotel/lodge → HOTEL.
- amount = final total paid. Use 0 if not visible."""

        fun fromSettings(context: Context): AiReceiptService {
            val prefs = SettingsActivity.getPrefs(context)
            val providerStr = prefs.getString(SettingsActivity.KEY_AI_PROVIDER, Provider.GROQ.name)
            val provider = try { Provider.valueOf(providerStr ?: "") } catch (e: Exception) { Provider.GROQ }
            return AiReceiptService(Config(
                provider = provider,
                apiKey = prefs.getString(SettingsActivity.KEY_API_KEY, "") ?: "",
                ollamaEndpoint = prefs.getString(SettingsActivity.KEY_OLLAMA_ENDPOINT, "http://localhost:11434") ?: "http://localhost:11434",
                ollamaModel = prefs.getString(SettingsActivity.KEY_OLLAMA_MODEL, "llava:latest") ?: "llava:latest"
            ))
        }
    }
}
