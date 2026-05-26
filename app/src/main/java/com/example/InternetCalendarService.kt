package com.example

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiException(val code: Int, override val message: String) : Exception(message)

object InternetCalendarService {
    private const val TAG = "InternetCalendarService"
    
    var apiKeyOverride: String? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun parseErrorResponse(bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            if (json.has("error")) {
                val err = json.getJSONObject("error")
                val msg = err.optString("message", "")
                if (msg.isNotEmpty()) msg else "Unknown Gemini API Error"
            } else {
                "Service returned HTTP error"
            }
        } catch (e: Exception) {
            "HTTP error response parsing failed: $bodyString"
        }
    }

    private suspend fun executeGeminiResponse(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        // Try fallback sequence of standard models (legacy gemini-1.5-flash is retired/prohibited)
        val models = listOf("gemini-2.5-flash", "gemini-3.5-flash")
        var lastException: Exception? = null

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }

        for (model in models) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val responseJson = JSONObject(responseStr)
                        val candidates = responseJson.optJSONArray("candidates") ?: return@use
                        if (candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content") ?: return@use
                            val parts = content.optJSONArray("parts") ?: return@use
                            if (parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text", "")
                                if (text.isNotEmpty()) {
                                    return@withContext text
                                }
                            }
                        }
                    } else {
                        val errorMsg = parseErrorResponse(responseStr)
                        Log.e(TAG, "Request failed for model $model with code: ${response.code}, message: $errorMsg")
                        
                        // If it's a 400 API_KEY_INVALID or similar authorization error, fail early
                        if (response.code == 400 && (errorMsg.contains("API key", ignoreCase = true) || errorMsg.contains("INVALID", ignoreCase = true))) {
                            throw ApiException(response.code, errorMsg)
                        }
                        throw ApiException(response.code, "Model $model: $errorMsg")
                    }
                }
            } catch (e: ApiException) {
                lastException = e
                if (e.message.contains("API key", ignoreCase = true) || e.message.contains("INVALID", ignoreCase = true)) {
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed calling model $model: ${e.message}", e)
                lastException = e
            }
        }
        
        throw lastException ?: Exception("All Gemini models failed")
    }

    suspend fun fetchOdiaCalendarMonth(year: Int, month: Int): List<OdiaDayInfo>? = withContext(Dispatchers.IO) {
        val apiKey = if (!apiKeyOverride.isNullOrEmpty()) apiKeyOverride!! else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured or is a placeholder.")
            throw Exception("API key is not configured or is empty.")
        }

        val lengthOfMonth = LocalDate.of(year, month, 1).lengthOfMonth()

        val prompt = """
            Generate the authentic traditional Odia Panjika (Calendar) details for each day of the Gregorian month: Year $year, Month $month.
            Return ONLY a valid JSON array, where each element corresponds to a day in the month from day 1 to $lengthOfMonth in chronological order.
            Each element in the JSON array must follow this exact JSON schema:
            {
              "day": Int (1 to $lengthOfMonth),
              "odiaMonthEn": String (e.g. "Baisakha", "Jyestha", "Ashadha", "Shrabana", "Bhadraba", "Aswina", "Kartika", "Margasira", "Pausa", "Magha", "Phalguna", "Chaitra"),
              "odiaMonthOr": String (the Odia spelling in Odia script, e.g. "ବୈଶାଖ", "ଜ୍ୟେଷ୍ଠ", "ଆଷାଢ଼", "ଶ୍ରାବଣ", "ଭାଦ୍ରବ", "ଆଶ୍ୱିନ", "କାର୍ତ୍ତିକ", "ମାର୍ଗଶିର", "ପୌଷ", "ମାଘ", "ଫାଲ୍‌ଗୁନ", "ଚୈତ୍ର"),
              "tithiNameEn": String (e.g., "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shasthi", "Saptami", "Asthami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima", "Amavasya"),
              "tithiNameOr": String (the Odia spelling, e.g., "ପ୍ରତିପଦା", "ଦ୍ୱିତୀୟା", etc.),
              "tithiValue": Int (1 to 15),
              "pakshaEn": String (e.g., "Shukla Paksha", "Krishna Paksha"),
              "pakshaOr": String (the Odia spelling, e.g., "ଶୁକ୍ଳ ପକ୍ଷ", "କୃଷ୍ଣ ପକ୍ଷ"),
              "rashiEn": String (e.g. "Mesa (Aries)", "Brisha (Taurus)", "Mithuna (Gemini)", "Karkata (Cancer)", "Singha (Leo)", "Kanya (Virgo)", "Tula (Libra)", "Bichha (Scorpio)", "Dhanu (Sagittarius)", "Makara (Capricorn)", "Kumbha (Aquarius)", "Meena (Pisces)"),
              "rashiOr": String (the Odia spelling, e.g. "ମେଷ", "ବୃଷ", etc.),
              "rashiSymbol": String (the emoji/symbol, e.g. "♈", "♉", etc.),
              "festivals": [
                {
                  "nameEn": String,
                  "nameOr": String,
                  "isMajor": Boolean
                }
              ]
            }
            Ensure the data is as astronomically accurate and traditional as possible to the Odia solar and lunar calendars (Amanta/Purnimanta systems used in Odisha) for Year $year, Month $month.
            Do not enclose the JSON in markdown blocks like ```json ... ```. Output raw JSON only.
        """.trimIndent()

        try {
            val responseText = executeGeminiResponse(prompt, apiKey)
            var cleanedText = responseText.trim()
            if (cleanedText.startsWith("```")) {
                val lines = cleanedText.lines()
                if (lines.size >= 3) {
                    val sub = lines.subList(1, lines.size - 1)
                    cleanedText = sub.joinToString("\n").trim()
                }
            }

            val jsonArray = JSONArray(cleanedText)
            val dayList = mutableListOf<OdiaDayInfo>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dayNum = obj.getInt("day")
                val date = LocalDate.of(year, month, dayNum)

                val odiaMonth = OdiaMonthInfo(
                    nameEn = obj.getString("odiaMonthEn"),
                    nameOr = obj.getString("odiaMonthOr")
                )

                val tithi = TithiInfo(
                    nameEn = obj.getString("tithiNameEn"),
                    nameOr = obj.getString("tithiNameOr"),
                    value = obj.getInt("tithiValue")
                )

                val paksha = PakshaInfo(
                    nameEn = obj.getString("pakshaEn"),
                    nameOr = obj.getString("pakshaOr")
                )

                val rashi = RashiInfo(
                    nameEn = obj.getString("rashiEn"),
                    nameOr = obj.getString("rashiOr"),
                    symbol = obj.getString("rashiSymbol")
                )

                val festivalsArray = obj.getJSONArray("festivals")
                val festivals = mutableListOf<Festival>()
                for (j in 0 until festivalsArray.length()) {
                    val festObj = festivalsArray.getJSONObject(j)
                    festivals.add(
                        Festival(
                            date = date,
                            nameEn = festObj.getString("nameEn"),
                            nameOr = festObj.getString("nameOr"),
                            isMajor = festObj.optBoolean("isMajor", false)
                        )
                    )
                }

                dayList.add(
                    OdiaDayInfo(
                        date = date,
                        odiaMonth = odiaMonth,
                        tithi = tithi,
                        paksha = paksha,
                        rashi = rashi,
                        festivals = festivals
                    )
                )
            }

            Log.d(TAG, "Successfully parsed ${dayList.size} days.")
            return@withContext dayList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or parsing calendar data from internet", e)
            throw e
        }
    }

    suspend fun fetchOdiaYearFestivals(year: Int): List<Festival>? = withContext(Dispatchers.IO) {
        val apiKey = if (!apiKeyOverride.isNullOrEmpty()) apiKeyOverride!! else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured or is a placeholder.")
            throw Exception("API key is not configured or is empty.")
        }

        val prompt = """
            Generate the authentic list of major Odia traditional festivals and public holidays in Odisha for the entire year of $year.
            Return ONLY a valid JSON array of objects, where each object follows this exact JSON schema:
            {
              "date": "YYYY-MM-DD",
              "nameEn": "Festival Name in English",
              "nameOr": "Festival Name in Odia characters",
              "isMajor": Boolean
            }
            Ensure the calendar rules accurately place Odia festivals like Makar Sankranti, Maha Shivaratri, Dola Purnima, Holi, Pana Sankranti, Akshaya Tritiya, Sabitri Brata, Raja, Ratha Yatra, Bahuda Yatra, Gamha Purnima, Janmashtami, Ganesh Chaturthi, Nuakhai, Durga Puja, Gaja Laxmi Puja, Deepavali, Kartika Purnima, Prathamastami, Samba Dashami correctly for the year of $year.
            Do not enclose the JSON in markdown blocks. Output raw JSON only.
        """.trimIndent()

        try {
            val responseText = executeGeminiResponse(prompt, apiKey)
            var cleanedText = responseText.trim()
            if (cleanedText.startsWith("```")) {
                val lines = cleanedText.lines()
                if (lines.size >= 3) {
                    val sub = lines.subList(1, lines.size - 1)
                    cleanedText = sub.joinToString("\n").trim()
                }
            }

            val jsonArray = JSONArray(cleanedText)
            val list = mutableListOf<Festival>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dateStr = obj.getString("date")
                val date = LocalDate.parse(dateStr)
                list.add(
                    Festival(
                        date = date,
                        nameEn = obj.getString("nameEn"),
                        nameOr = obj.getString("nameOr"),
                        isMajor = obj.optBoolean("isMajor", false)
                    )
                )
            }
            Log.d(TAG, "Successfully fetched ${list.size} year-wide festivals for $year.")
            return@withContext list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or parsing year festivals for $year", e)
            throw e
        }
    }
}
