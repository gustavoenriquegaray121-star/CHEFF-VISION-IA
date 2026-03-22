package com.gustavo.chefvisionia.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.TextView
import com.gustavo.chefvisionia.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiAnalyticEngine {

    // 🔐 API KEY DESDE BUILD
    var apiKey: String = BuildConfig.GEMINI_API_KEY

    var onIngredientsDetected: ((List<String>) -> Unit)? = null

    // 🔥 LLAVE MAESTRA
    private val isDeveloperMode = true

    // 🔥 DEMO SOLO SI NO HAY API Y NO ERES DEV
    private val isDemoMode: Boolean
        get() = apiKey.isEmpty() && !isDeveloperMode

    fun hasPremiumAccess(userHasPaid: Boolean): Boolean {
        return userHasPaid || isDeveloperMode
    }

    fun hasSuperPremiumAccess(userHasPaid: Boolean): Boolean {
        return userHasPaid || isDeveloperMode
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun generarRespuestaDemo(
        cuisine: String,
        gourmet: Boolean,
        fitness: Boolean,
        dessert: Boolean
    ): String {

        val extra = when {
            fitness -> "\n💪 Incluye macros y enfoque proteico."
            dessert -> "\n🍰 Incluye sugerencia de postre."
            gourmet -> "\n🍷 Incluye maridaje profesional."
            else -> ""
        }

        return """
⚡ MODO DEMO ACTIVO

Estamos optimizando la conexión con el motor IA.

🍳 INGREDIENTES DETECTADOS:
- tomate
- cebolla
- ajo
- pollo

🍽️ RECETAS ($cuisine):
1. 🍗 Pollo al ajo con tomate
   ⏱️ 25 min
   🧂 Ingredientes: pollo, ajo, tomate, aceite
   👨‍🍳 Preparación: sofríe ajo, agrega pollo, cocina con tomate
   💡 Tip: usa fuego medio

2. 🥗 Ensalada fresca
3. 🍳 Omelette sencillo

$extra

💡 Tip: Mejora iluminación para resultados reales.
        """.trimIndent()
    }

    suspend fun analyze(
        path: String,
        output: TextView,
        cuisine: String,
        country: String,
        gourmet: Boolean,
        fitness: Boolean,
        vegan: Boolean,
        dessert: Boolean,
        inventoryContext: String
    ) {
        withContext(Dispatchers.IO) {
            try {

                if (isDemoMode) {
                    withContext(Dispatchers.Main) {
                        output.text = generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                        onIngredientsDetected?.invoke(listOf("tomate", "cebolla", "pollo"))
                    }
                    return@withContext
                }

                if (apiKey.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        output.text = "❌ API KEY VACÍA"
                    }
                    return@withContext
                }

                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        output.text = generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                    }
                    return@withContext
                }

                val base64Image = bitmapToBase64(bitmap)

                val promptText = """
Eres Chef Vision IA 🍳

Detecta ingredientes reales y genera recetas profesionales.

1. INGREDIENTES DETECTADOS
2. 3 RECETAS ($cuisine - $country)
3. RECETA LOCAL

Responde claro en español.
                """.trimIndent()

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", promptText) })
                                put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        }
                    ))
                }

                // 🔥 ENDPOINT CORRECTO
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$apiKey"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                OutputStreamWriter(connection.outputStream).use {
                    it.write(requestBody.toString())
                }

                val responseCode = connection.responseCode

                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText()
                        ?: "Error $responseCode"
                }

                val resultText = if (responseCode in 200..299) {
                    try {
                        val json = JSONObject(responseText)

                        val parts = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")

                        val builder = StringBuilder()

                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                builder.append(part.getString("text"))
                            }
                        }

                        builder.toString()

                    } catch (e: Exception) {
                        "❌ ERROR PARSEO:\n${e.message}\n$responseText"
                    }
                } else {
                    "❌ ERROR $responseCode:\n$responseText"
                }

                withContext(Dispatchers.Main) {
                    output.text = resultText
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    output.text = "❌ ERROR CRÍTICO:\n${e.message}"
                }
            }
        }
    }
}
