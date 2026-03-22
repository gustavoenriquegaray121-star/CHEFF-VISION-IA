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

                if (apiKey.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        output.text = "❌ API Key no configurada."
                    }
                    return@withContext
                }

                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        output.text = "❌ No se pudo procesar la imagen."
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
                    "\nIncluye calorías, proteínas, carbohidratos y grasas."
                else ""

                val wineExtra = if (gourmet)
                    "\nSugiere un vino o bebida ideal."
                else ""

                val inventoryNote = if (inventoryContext.isNotEmpty())
                    "\nIngredientes disponibles: $inventoryContext. Prioriza los más antiguos."
                else ""

                val promptText = """
Eres Chef Vision IA 🍳

1. INGREDIENTES DETECTADOS:
Lista clara de ingredientes.

2. 3 RECETAS ($cuisine - $country, estilo $modeText):
- Nombre
- Tiempo
- Ingredientes
- Pasos
- Tip del chef
$fitnessExtra
$wineExtra

3. RECETA LOCAL

$inventoryNote

Responde en español con emojis.
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

                // ✅ ENDPOINT CORREGIDO
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$apiKey"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
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

                        // 🔥 VALIDAR ERROR API
                        if (json.has("error")) {
                            "❌ API Error: ${json.getJSONObject("error")}"
                        } else {

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
                        }

                    } catch (e: Exception) {
                        "❌ Error parseando respuesta: ${e.message}"
                    }
                } else {
                    "❌ Error $responseCode:\n$responseText"
                }

                val ingredientLines = resultText
                    .substringAfter("INGREDIENTES DETECTADOS", "")
                    .substringBefore("RECETAS", "")
                    .lines()
                    .filter { it.trim().startsWith("-") || it.trim().startsWith("•") }
                    .map {
                        it.replace("-", "")
                            .replace("•", "")
                            .trim()
                            .lowercase()
                    }
                    .filter { it.isNotEmpty() }

                withContext(Dispatchers.Main) {
                    output.text = resultText
                    onIngredientsDetected?.invoke(ingredientLines)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    output.text = "❌ Error general: ${e.message}"
                }
            }
        }
    }
}
