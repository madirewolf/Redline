package com.redline.app.nutrition

import com.redline.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class FoodResult(
    val name: String,
    val calories: Float,
    val protein: Float?,
    val carbs: Float?,
    val fat: Float?,
    val servingSize: Float,
    val unit: String
)

/** What a lookup came back with. Each case gets its own message on screen. */
sealed interface FoodSearch {
    data class Found(val results: List<FoodResult>) : FoodSearch
    data object NoKey : FoodSearch
    data object NoMatch : FoodSearch
    data class Failed(val reason: String) : FoodSearch
}

@Singleton
class NutritionApi @Inject constructor(
    private val settingsStore: SettingsStore
) {
    /** Values come back per 100 g, the way FoodData Central reports them. */
    suspend fun searchFood(query: String): FoodSearch = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.usdaApiKey.value
        if (apiKey.isBlank()) return@withContext FoodSearch.NoKey

        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.nal.usda.gov/fdc/v1/foods/search?query=$encoded&pageSize=5&api_key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000

        try {
            when (val code = conn.responseCode) {
                in 200..299 -> Unit
                401, 403 -> return@withContext FoodSearch.Failed("The USDA API key was rejected. Check it in Settings")
                429 -> return@withContext FoodSearch.Failed("Too many lookups for this key. Try again in an hour")
                else -> return@withContext FoodSearch.Failed("The food database answered HTTP $code")
            }
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val foods = json.optJSONArray("foods") ?: return@withContext FoodSearch.NoMatch

            val results = (0 until minOf(foods.length(), 5)).mapNotNull { i ->
                val food = foods.getJSONObject(i)
                val nutrients = food.optJSONArray("foodNutrients") ?: return@mapNotNull null
                val name = food.optString("description", "Unknown")

                var cal = 0f; var pro: Float? = null; var carb: Float? = null; var fatVal: Float? = null
                for (j in 0 until nutrients.length()) {
                    val n = nutrients.getJSONObject(j)
                    when (n.optInt("nutrientId")) {
                        1008 -> cal = n.optDouble("value", 0.0).toFloat()  // Energy (kcal)
                        1003 -> pro = n.optDouble("value", 0.0).toFloat()  // Protein
                        1005 -> carb = n.optDouble("value", 0.0).toFloat() // Carbs
                        1004 -> fatVal = n.optDouble("value", 0.0).toFloat() // Fat
                    }
                }

                FoodResult(name, cal, pro, carb, fatVal, 100f, "g")
            }
            if (results.isEmpty()) FoodSearch.NoMatch else FoodSearch.Found(results)
        } catch (e: JSONException) {
            FoodSearch.Failed("The food database sent something unexpected")
        } catch (e: IOException) {
            FoodSearch.Failed("Couldn't reach the food database. Check your connection")
        } finally {
            conn.disconnect()
        }
    }
}
