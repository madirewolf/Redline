package com.redline.app.nutrition

import com.redline.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

@Singleton
class NutritionApi @Inject constructor(
    private val settingsStore: SettingsStore
) {
    suspend fun searchFood(query: String): List<FoodResult> = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.usdaApiKey.value
        if (apiKey.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.nal.usda.gov/fdc/v1/foods/search?query=$encoded&pageSize=5&api_key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000

        try {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val foods = json.optJSONArray("foods") ?: return@withContext emptyList()

            (0 until minOf(foods.length(), 5)).mapNotNull { i ->
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
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }
}
