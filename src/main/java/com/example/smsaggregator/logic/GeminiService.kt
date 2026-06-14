package com.example.smsaggregator.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * GeminiService - ONE model (1.5 Flash), ONE request. Stable and safe.
 */
object GeminiService {

    private const val TAG = "GeminiService"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

    private val VALID_CATEGORIES = listOf(
        "Food & Dining", "Groceries", "Gold & Jewellery", "Fuel", "Shopping",
        "Travel", "Utilities", "Telecom", "Entertainment", "Health",
        "Fitness", "Education", "Insurance", "Investments", "EMI / Loan",
        "Rent", "Auto Debit", "UPI Transfer", "Wallet Topup", "ATM",
        "Government", "Home & Lifestyle", "Other"
    )

    data class MerchantContext(val originalName: String, val rawSms: String)
    data class ClassificationResult(val originalName: String, val category: String, val rawSms: String)

    fun resetCache() {}

    private fun callGemini(apiKey: String, body: String): Pair<Int, String> {
        val url = java.net.URL("$API_URL?key=$apiKey")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        java.io.OutputStreamWriter(conn.outputStream).use { it.write(body) }
        val code = conn.responseCode
        val stream = if (code == 200) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = java.io.BufferedReader(java.io.InputStreamReader(stream)).use { it.readText() }
        Log.d(TAG, "HTTP $code response length: ${text.length}")
        return Pair(code, text)
    }

    private fun extractError(response: String, fallback: String): String {
        return try {
            JSONObject(response).getJSONObject("error").getString("message")
        } catch (e: Exception) { fallback }
    }

    suspend fun classifyMerchants(
        apiKey: String,
        merchantContexts: List<MerchantContext>
    ): Result<List<ClassificationResult>> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank() || merchantContexts.isEmpty()) return@withContext Result.success(emptyList())

        Log.d(TAG, "Using Key starting with: ${key.take(4)}****")

        val categoryList = VALID_CATEGORIES.joinToString(", ")
        val merchantData = merchantContexts.joinToString("\n") { ctx ->
            "- Original: ${ctx.originalName} | SMS: \"${ctx.rawSms}\""
        }

        val prompt = """
            You are a financial classification assistant for Indian users.
            Return ONLY a valid JSON array. No markdown. No explanation. No code blocks.
            Each item must have: "originalName" (exact value from input), "category" (from list), "sms" (exact SMS from input).
            Available Categories: $categoryList
            Data to Classify:
            $merchantData
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 8192)
            })
        }.toString()

        try {
            val (code, response) = callGemini(key, requestBody)
            return@withContext when (code) {
                200 -> Result.success(parseResponse(response))
                429 -> Result.failure(Exception("AI_ERROR_${extractError(response, "Rate limit.")}"))
                401, 403 -> Result.failure(Exception("AI_ERROR_Invalid API key."))
                else -> Result.failure(Exception("AI_ERROR_${extractError(response, "HTTP $code")}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("AI_ERROR_Network: ${e.message}"))
        }
    }

    private fun parseResponse(responseJson: String): List<ClassificationResult> {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            var rawText = parts.getJSONObject(0).getString("text").trim()

            if (rawText.startsWith("```json")) rawText = rawText.removePrefix("```json").removeSuffix("```").trim()
            else if (rawText.startsWith("```")) rawText = rawText.removePrefix("```").removeSuffix("```").trim()

            val jsonArray = JSONArray(rawText)
            val results = mutableListOf<ClassificationResult>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val name = item.optString("originalName")
                val cat = item.optString("category")
                val sms = item.optString("sms")
                if (name.isNotBlank() && cat in VALID_CATEGORIES) {
                    results.add(ClassificationResult(name, cat, sms))
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Parse fail: ${e.message}")
            emptyList()
        }
    }

    suspend fun predictBudgetPacing(
        apiKey: String,
        category: String,
        limit: Double,
        currentlySpent: Double,
        currentDay: Int,
        daysLeft: Int
    ): String? = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) return@withContext null

        val prompt = """
            You are a helpful financial AI. Budget of ₹$limit for '$category'.
            Spent ₹$currentlySpent by day $currentDay. $daysLeft days left.
            1-2 sentence insight. No markdown.
        """.trimIndent()

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 256)
            })
        }.toString()

        try {
            val (code, response) = callGemini(key, body)
            if (code == 200) {
                val root = JSONObject(response)
                return@withContext root.getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text").trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Budget fail: ${e.message}")
        }
        return@withContext "⚠️ Prediction unavailable."
    }
}
