package com.gustavo.chefvisionia.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiAnalyticEngine {

    var apiKey: String = ""
    var onIngredientsDetected: ((List<String>) -> Unit)? = null

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
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
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        output.text = "❌ Error: No se pudo procesar la imagen."
                    }
                    return@withContext
                }

                val base64Image = bitmapToBase64(bitmap)

                val modeText = when {
                    fitness -> "fitness con calorías aproximadas y alto contenido proteico"
                    vegan   -> "100% vegana sin productos de origen animal"
                    dessert -> "postres y dulces creativos"
                    gourmet -> "presentación gourmet de restaurante de lujo"
                    else    -> "casera, fácil y económica"
                }

                val fitnessExtra = if (fitness)
                    "\nPara cada receta incluye: calorías aproximadas, gramos de proteína, carbohidratos y grasas."
                else ""

                val wineExtra = if (gourmet)
                    "\nPara cada receta sugiere un vino o bebida que combine perfecto."
                else ""

                val inventoryNote = if (inventoryContext.isNotEmpty())
                    "\n\nIngredientes en despensa (con días desde compra): $inventoryContext. Prioriza los más antiguos."
                else ""

                val promptText = """
                    Eres Chef Vision IA, el asistente culinario más inteligente del mundo.
                    
                    Analiza los ingredientes visibles en esta imagen y haz lo siguiente:
                    
                    1. 📦 INGREDIENTES DETECTADOS: Lista todos los ingredientes que ves.
                    
                    2. 🍽️ 3 RECETAS SUGERIDAS (cocina $cuisine de $country, estilo $modeText):
                       Para cada receta incluye:
                       - Nombre del platillo
                       - Tiempo de preparación
                       - Ingredientes necesarios
                       - Pasos resumidos
                       - Tip del chef
                       $fitnessExtra
                       $wineExtra
                    
                    3. 🌍 TOQUE LOCAL: Una receta especial típica de $country con estos ingredientes.
                    
                    $inventoryNote
                    
                    Responde en español, de forma clara, motivadora y apetitosa.
                    Usa emojis para hacerlo visual y divertido.
                """.trimIndent()

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                                put(JSONObject().apply {
                                    put("text", promptText)
                                })
                            })
                        }
                    ))
                }

                // CORRECCIÓN FINAL: Usamos el modelo que Google acepta hoy
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-001:generateContent?key=$apiKey")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseText = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Error $responseCode"
                }

                val result = if (responseCode == 200) {
                    try {
                        val json = JSONObject(responseText)
                        json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                    } catch (e: Exception) {
                        "❌ Error procesando respuesta: ${e.message}"
                    }
                } else {
                    "❌ Error $responseCode: $responseText"
                }

                val ingredientLines = result
                    .substringAfter("INGREDIENTES DETECTADOS", "")
                    .substringBefore("RECETAS", "")
                    .lines()
                    .filter { it.trim().startsWith("-") || it.trim().startsWith("•") }
                    .map { it.replace("-", "").replace("•", "").trim().lowercase() }
                    .filter { it.isNotEmpty() }

                withContext(Dispatchers.Main) {
                    output.text = result
                    onIngredientsDetected?.invoke(ingredientLines)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    output.text = "❌ Error: ${e.message}"
                }
            }
        }
    }
}
