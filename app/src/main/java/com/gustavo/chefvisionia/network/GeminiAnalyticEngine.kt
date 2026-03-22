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

    // Se asigna la llave desde el secreto de GitHub que configuramos
    var apiKey: String = BuildConfig.GEMINI_API_KEY 

    var onIngredientsDetected: ((List<String>) -> Unit)? = null
    var onInventoryDataReady: ((JSONArray) -> Unit)? = null

    private val isDeveloperMode = true

    private val isDemoMode: Boolean
        get() = apiKey.isEmpty() && !isDeveloperMode

    fun hasPremiumAccess(userHasPaid: Boolean): Boolean = userHasPaid || isDeveloperMode
    fun hasSuperPremiumAccess(userHasPaid: Boolean): Boolean = userHasPaid || isDeveloperMode

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun generarRespuestaDemo(cuisine: String, gourmet: Boolean, fitness: Boolean, dessert: Boolean): String {
        val extra = when {
            fitness -> "\n💪 Incluye macros y enfoque proteico."
            dessert -> "\n🍰 Incluye sugerencia de postre."
            gourmet -> "\n🍷 Incluye maridaje profesional."
            else -> ""
        }
        return """
⚡ MODO DEMO ACTIVO (Sin conexión)

🍳 INGREDIENTES DETECTADOS:
- tomate (🔴 ¡Úsame hoy!)
- cebolla (🟢 Fresco)
- pollo (🟡 Consumir pronto)

🍽️ RECETAS SUGERIDAS ($cuisine):
1. 🍗 Pollo al ajo con tomate (Prioridad: Desperdicio Cero)
$extra
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
                // Validación de acceso para desarrollador (bypass de pago)
                val canUsePremium = hasPremiumAccess(false)
                val canUseSuper = hasSuperPremiumAccess(false)

                if (isDemoMode) {
                    withContext(Dispatchers.Main) {
                        output.text = generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                        onIngredientsDetected?.invoke(listOf("tomate", "cebolla", "pollo"))
                    }
                    return@withContext
                }

                val bitmap = BitmapFactory.decodeFile(path) ?: run {
                    withContext(Dispatchers.Main) { output.text = generarRespuestaDemo(cuisine, gourmet, fitness, dessert) }
                    return@withContext
                }

                val base64Image = bitmapToBase64(bitmap)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                val promptText = """
Eres Chef Vision IA 🍳. Tu misión es ser un asistente proactivo de cocina y negocio.

REGLAS DE ANALISIS:
1. SOLO ingredientes visibles.
2. Calcula 'shelf_life' (días de vida restante estimados).
3. Si el inventario ($inventoryContext) tiene productos viejos, PRIORIZA recetas para usarlos (Desperdicio Cero).

ESTRUCTURA DE RESPUESTA (Responde en este orden):

1. INGREDIENTES DETECTADOS:
(Lista con nombre y estado de frescura)

2. 3 RECETAS ($cuisine - $country):
(Nombre, Tiempo, Macros si es fitness, Maridaje si es gourmet, Postre si aplica)

3. DATOS DE INVENTARIO (ESTRICTO JSON AL FINAL):
Genera un JSON con esta estructura para mi base de datos:
[{"ingrediente": "nombre", "shelf_life": dias, "timestamp": "$timestamp", "color": "rojo/amarillo/verde"}]

4. SUGERENCIA DE COMPRA (SORIANA):
Si falta algo básico para las recetas, menciónalo como oferta.
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

                // URL CORREGIDA CON v1beta PARA QUITAR EL ERROR 404
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

                var responseText = ""
                var success = false

                try {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 15000
                        readTimeout = 15000
                        doOutput = true
                    }
                    OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }
                    
                    if (connection.responseCode in 200..299) {
                        responseText = connection.inputStream.bufferedReader().readText()
                        success = true
                    } else {
                        responseText = connection.errorStream?.bufferedReader()?.readText() ?: ""
                    }
                } catch (e: Exception) { success = false }

                val resultText = if (success) {
                    val json = JSONObject(responseText)
                    val builder = StringBuilder()
                    val candidates = json.getJSONArray("candidates").getJSONObject(0)
                    val parts = candidates.getJSONObject("content").getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        builder.append(parts.getJSONObject(i).getString("text"))
                    }
                    builder.toString()
                } else {
                    if (isDeveloperMode) "❌ ERROR TOTAL:\n$responseText" else generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                }

                val startJson = resultText.indexOf("[")
                val endJson = resultText.lastIndexOf("]")
                if (startJson != -1 && endJson != -1) {
                    try {
                        val jsonArr = JSONArray(resultText.substring(startJson, endJson + 1))
                        withContext(Dispatchers.Main) { 
                            onInventoryDataReady?.invoke(jsonArr)
                            val ingredientsList = mutableListOf<String>()
                            for (i in 0 until jsonArr.length()) {
                                ingredientsList.add(jsonArr.getJSONObject(i).getString("ingrediente"))
                            }
                            onIngredientsDetected?.invoke(ingredientsList)
                        }
                    } catch (e: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    output.text = resultText
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    output.text = if (isDeveloperMode) "❌ ERROR CRÍTICO:\n${e.message}" else generarRespuestaDemo(cuisine, gourmet, fitness, dessert)
                }
            }
        }
    }

    // Función para liberar los candados en la interfaz Súper Premium
    fun updatePlanUI(binding: Any) { 
        // binding.chipFrench.text = "Francesa 🇫🇷" 
    }
}
