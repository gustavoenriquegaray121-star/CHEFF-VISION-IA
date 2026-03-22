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

    // 🔐 API KEY DESDE BUILD (IMPORTANTE)
    var apiKey: String = BuildConfig.GEMINI_API_KEY

    var onIngredientsDetected: ((List<String>) -> Unit)? = null

    // 🔥 LLAVE MAESTRA
    private val isDeveloperMode = true

    // 🔥 DEMO SOLO SI NO HAY API Y NO ERES DEV
    private val isDemoMode: Boolean
        get() = apiKey.isEmpty() && !isDeveloperMode

    // 🔥 ACCESO TOTAL
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

    // 💎 DEMO PRO (MEJORADO)
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
   💡 Tip: usa fuego medio para mejor sabor

2. 🥗 Ensalada fresca casera
3. 🍳 Omelette sencillo

$extra

💡 Tip: Mejora iluminación y enfoque para resultados reales.
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

                // 🔹 DEMO CONTROLADO
                if (isDemoMode) {
                    withContext(Dispatchers.Main) {
                        output.text = generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                        onIngredientsDetected?.invoke(listOf("tomate", "cebolla", "pollo"))
                    }
                    return@withContext
                }

                if (apiKey.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        output.text = "❌ API KEY VACÍA (CONFIG ERROR)"
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

                val modeText = when {
                    fitness -> "fitness con macros y alto contenido proteico"
                    vegan   -> "100% vegana"
                    dessert -> "postres creativos"
                    gourmet -> "gourmet de alta cocina"
                    else    -> "casera"
                }

                val fitnessExtra = if (fitness)
                    "\nIncluye calorías, proteínas, carbohidratos y grasas."
                else ""

                val wineExtra = if (gourmet)
                    "\nIncluye maridaje profesional."
                else ""

                val dessertExtra = if (dessert)
                    "\nSugiere un postre que combine perfectamente."
                else ""

                val inventoryNote = if (inventoryContext.isNotEmpty())
                    "\nIngredientes disponibles: $inventoryContext."
                else ""

                val promptText = """
Eres Chef Vision IA 🍳

REGLAS:
- SOLO ingredientes visibles
- NO inventar
- Sé preciso

1. INGREDIENTES DETECTADOS

2. 3 RECETAS ($cuisine - $country, estilo $modeText):
- Nombre
- Tiempo
- Ingredientes
- Pasos
- Tip
$fitnessExtra
$wineExtra
$dessertExtra

3. RECETA LOCAL

$inventoryNote

Responde en español profesional y atractivo.
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

                // ✅ MODELO ESTABLE REAL
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$apiKey"
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
                        if (isDeveloperMode)
                            "❌ ERROR PARSEO:\n${e.message}\n$responseText"
                        else
                            generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                    }
                } else {
                    if (isDeveloperMode)
                        "❌ ERROR $responseCode:\n$responseText"
                    else
                        generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
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
                    if (isDeveloperMode)
                        output.text = "❌ ERROR CRÍTICO:\n${e.message}"
                    else
                        output.text = generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                }
            }
        }
    }
}
