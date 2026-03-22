package com.gustavo.chefvisionia.network

import android.graphics.BitmapFactory
import android.widget.TextView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiAnalyticEngine {

    private const val API_KEY = "AIzaSyCYONYuJMUiFMrqyOkoFxxiVf4hxgT9XbM"
    private val model = GenerativeModel(
        modelName = "gemini-pro-vision",
        apiKey = API_KEY
    )

    var onIngredientsDetected: ((List<String>) -> Unit)? = null

    suspend fun analyze(
        path: String,
        output: TextView,
        cuisine: String,
        country: String,
        gourmet: Boolean,
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

                val gourmetText = if (gourmet)
                    "presentación gourmet de restaurante de lujo"
                else "casera y fácil"

                val inventoryNote = if (inventoryContext.isNotEmpty())
                    "\n\nIngredientes en despensa (con días desde compra): " +
                    "$inventoryContext. Prioriza los más antiguos en tus sugerencias."
                else ""

                val prompt = """
                    Eres Chef Vision IA, el asistente culinario más inteligente del mundo.
                    
                    Analiza los ingredientes visibles en esta imagen y haz lo siguiente:
                    
                    1. 📦 INGREDIENTES DETECTADOS: Lista todos los ingredientes que ves.
                    
                    2. 🍽️ 3 RECETAS SUGERIDAS (cocina $cuisine de $country, estilo $gourmetText):
                       Para cada receta incluye:
                       - Nombre del platillo
                       - Tiempo de preparación
                       - Ingredientes necesarios
                       - Pasos resumidos
                       - Tip del chef
                    
                    3. 🌍 TOQUE LOCAL: Una receta especial típica de $country 
                       con estos ingredientes.
                    
                    $inventoryNote
                    
                    Responde en español, de forma clara, motivadora y apetitosa.
                    Usa emojis para hacerlo visual y divertido.
                """.trimIndent()

                val response = model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val responseText = response.text ?: "Sin respuesta."

                val ingredientLines = responseText
                    .substringAfter("INGREDIENTES DETECTADOS", "")
                    .substringBefore("RECETAS", "")
                    .lines()
                    .filter { it.trim().startsWith("-") || it.trim().startsWith("•") }
                    .map { it.replace("-", "").replace("•", "").trim().lowercase() }
                    .filter { it.isNotEmpty() }

                withContext(Dispatchers.Main) {
                    output.text = responseText
                    onIngredientsDetected?.invoke(ingredientLines)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    output.text = "❌ Error: ${e.message}\n\nVerifica tu conexión o API Key."
                }
            }
        }
    }
}
