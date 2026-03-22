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
import java.text.SimpleDateFormat
import java.util.*

object GeminiAnalyticEngine {

    var apiKey: String = BuildConfig.GEMINI_API_KEY 

    var onIngredientsDetected: ((List<String>) -> Unit)? = null
    var onInventoryDataReady: ((JSONArray) -> Unit)? = null

    private val isDeveloperMode = true

    private val isDemoMode: Boolean
        get() = apiKey.isEmpty() && !isDeveloperMode

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun generarRespuestaDemo(cuisine: String, gourmet: Boolean, fitness: Boolean, dessert: Boolean): String {
        return """
⚡ MODO DEMO ACTIVO (Sin conexión)
🍳 INGREDIENTES DETECTADOS:
- tomate (🔴 ¡Úsame hoy!)
- cebolla (🟢 Fresco)
- pollo (🟡 Consumir pronto)

🍽️ RECETAS SUGERIDAS ($cuisine):
1. 🍗 Pollo al ajo con tomate (Prioridad: Desperdicio Cero)
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

                val bitmap = BitmapFactory.decodeFile(path) ?: return@withContext

                val base64Image = bitmapToBase64(bitmap)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                val promptText = """
Eres Chef Vision IA 🍳. Responde en español.
1. INGREDIENTES DETECTADOS: Lista lo que ves.
2. 3 RECETAS ($cuisine - $country): Sugiere platos usando esos ingredientes.
3. DATOS DE INVENTARIO: Genera un JSON al final con este formato: [{"ingrediente": "nombre", "shelf_life": 5, "timestamp": "$timestamp", "color": "verde"}]
                """.trimIndent()

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    }))
                }

                // URL CORREGIDA: Usando v1beta que es la más estable para el modelo Flash con imágenes
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                
                OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }
                
                val responseCode = connection.responseCode
                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Error $responseCode"
                }

                withContext(Dispatchers.Main) {
                    if (responseCode in 200..299) {
                        val json = JSONObject(responseText)
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        output.text = text
                    } else {
                        output.text = "❌ Error de conexión ($responseCode). Revisa tu API Key."
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    output.text = "❌ Error: ${e.message}"
                }
            }
        }
    }
}
