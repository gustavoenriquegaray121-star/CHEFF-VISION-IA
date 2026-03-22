package com.gustavo.chefvisionia.utils

import android.content.Context
import java.util.Date
import java.util.concurrent.TimeUnit

object InventoryManager {

    private val inventory = mutableMapOf<String, Date>()
    private val usageCount = mutableMapOf<String, Int>()
    private const val PREFS = "ChefInventory"
    private const val PREFS_USAGE = "ChefUsage"

    private val freshnessRules = mapOf(
        "tomate"      to 5,  "jitomate"    to 5,
        "lechuga"     to 4,  "espinaca"    to 3,
        "zanahoria"   to 7,  "pepino"      to 5,
        "cebolla"     to 10, "ajo"         to 14,
        "pollo"       to 2,  "carne"       to 2,
        "pescado"     to 1,  "camarón"     to 1,
        "leche"       to 3,  "crema"       to 5,
        "queso"       to 7,  "huevo"       to 14,
        "aguacate"    to 3,  "plátano"     to 4,
        "manzana"     to 7,  "limón"       to 10,
        "papa"        to 10, "chile"       to 7,
        "cilantro"    to 4,  "naranja"     to 7
    )

    private val suggestions = mapOf(
        "tomate"   to "¿Unas entomatadas, salsa roja o sopa de tomate? 🍅",
        "jitomate" to "¿Una salsa fresca, pico de gallo o pizza casera? 🍅",
        "lechuga"  to "¿Una ensalada César o tacos de lechuga? 🥗",
        "espinaca" to "¿Un licuado verde, quesadillas o pasta con espinaca? 🌿",
        "pollo"    to "¿Pollo al ajillo, caldo tlalpeño o tacos de pollo? 🍗",
        "carne"    to "¿Bistec a la mexicana, arrachera o picadillo? 🥩",
        "pescado"  to "¡Úsalo hoy! ¿Pescado a la veracruzana o tacos de pescado? 🐟",
        "aguacate" to "¿Guacamole, tostadas o tacos con aguacate? 🥑",
        "plátano"  to "¿Plátanos fritos, licuado o pan de plátano? 🍌",
        "leche"    to "¿Arroz con leche, atole o crema pastelera? 🥛",
        "huevo"    to "¿Huevos rancheros, tortilla española o revueltos? 🍳",
        "papa"     to "¿Papas a la francesa, guisado o caldo de papa? 🥔",
        "chile"    to "¿Salsa verde, chiles rellenos o enchiladas? 🌶️",
        "queso"    to "¿Quesadillas, enchiladas o pasta gratinada? 🧀"
    )

    private val staples = listOf(
        "tomate", "cebolla", "ajo", "huevo", "leche",
        "pollo", "arroz", "frijoles", "aceite", "sal",
        "limón", "chile", "queso", "tortillas", "papa"
    )

    // Precios aproximados en MXN
    private val approximatePrices = mapOf(
        "tomate"    to 25.0, "jitomate"  to 25.0,
        "cebolla"   to 15.0, "ajo"       to 20.0,
        "pollo"     to 80.0, "carne"     to 120.0,
        "huevo"     to 45.0, "leche"     to 25.0,
        "arroz"     to 30.0, "frijoles"  to 25.0,
        "aceite"    to 40.0, "queso"     to 60.0,
        "tortillas" to 20.0, "papa"      to 20.0,
        "limón"     to 15.0, "chile"     to 20.0,
        "aguacate"  to 30.0, "plátano"   to 20.0,
        "zanahoria" to 15.0, "espinaca"  to 20.0
    )

    fun update(context: Context, items: List<String>) {
        val now = Date()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val usagePrefs = context.getSharedPreferences(PREFS_USAGE, Context.MODE_PRIVATE)
        val usageEdit = usagePrefs.edit()

        items.forEach {
            val key = it.lowercase().trim()
            inventory[key] = now
            prefs.putLong(key, now.time)

            // Registrar uso para memoria de patrones
            val count = usagePrefs.getInt(key, 0) + 1
            usageCount[key] = count
            usageEdit.putInt(key, count)
        }
        prefs.apply()
        usageEdit.apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val usagePrefs = context.getSharedPreferences(PREFS_USAGE, Context.MODE_PRIVATE)

        prefs.all.forEach { (k, v) ->
            if (v is Long) inventory[k] = Date(v)
        }
        usagePrefs.all.forEach { (k, v) ->
            if (v is Int) usageCount[k] = v
        }
    }

    fun getAlerts(): String {
        val now = Date()
        val alerts = mutableListOf<String>()

        inventory.forEach { (ingredient, purchaseDate) ->
            val daysSince = TimeUnit.MILLISECONDS.toDays(now.time - purchaseDate.time)
            val maxDays = freshnessRules.entries.find {
                ingredient.contains(it.key)
            }?.value ?: return@forEach

            if (daysSince >= maxDays - 1) {
                val suggestion = suggestions.entries.find {
                    ingredient.contains(it.key)
                }?.value ?: "¡Úsalo pronto!"

                val urgency = if (daysSince >= maxDays) "⚠️ VENCIDO" else "🕐 Pronto a vencer"
                alerts.add("$urgency — $ingredient ($daysSince días)\n💡 $suggestion")
            }
        }

        return if (alerts.isEmpty()) "" else alerts.joinToString("\n\n")
    }

    fun getInventoryContext(): String {
        val now = Date()
        return inventory.entries.joinToString(", ") { (ingredient, date) ->
            val days = TimeUnit.MILLISECONDS.toDays(now.time - date.time)
            "$ingredient ($days días)"
        }
    }

    fun getMostUsedIngredients(): String {
        if (usageCount.isEmpty()) return ""
        return usageCount.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key} (usado ${it.value} veces)" }
    }

    fun getShoppingList(): String {
        val now = Date()
        val shoppingList = mutableListOf<String>()
        var estimatedTotal = 0.0

        // Ingredientes vencidos — reponer
        inventory.forEach { (ingredient, purchaseDate) ->
            val daysSince = TimeUnit.MILLISECONDS.toDays(now.time - purchaseDate.time)
            val maxDays = freshnessRules.entries.find {
                ingredient.contains(it.key)
            }?.value ?: return@forEach

            if (daysSince >= maxDays) {
                val price = approximatePrices.entries.find {
                    ingredient.contains(it.key)
                }?.value ?: 0.0
                estimatedTotal += price
                val priceText = if (price > 0) " (~\$${"%.0f".format(price)})" else ""
                shoppingList.add("🔴 Reponer: $ingredient$priceText")
            }
        }

        // Básicos que no están en inventario
        staples.forEach { staple ->
            val found = inventory.keys.any { it.contains(staple) }
            if (!found) {
                val price = approximatePrices[staple] ?: 0.0
                estimatedTotal += price
                val priceText = if (price > 0) " (~\$${"%.0f".format(price)})" else ""
                shoppingList.add("🛒 Falta: $staple$priceText")
            }
        }

        // Ingredientes más usados en casa
        val topIngredients = usageCount.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        topIngredients.forEach { ingredient ->
            val alreadyInList = shoppingList.any { it.contains(ingredient) }
            if (!alreadyInList) {
                val price = approximatePrices.entries.find {
                    ingredient.contains(it.key)
                }?.value ?: 0.0
                estimatedTotal += price
                val priceText = if (price > 0) " (~\$${"%.0f".format(price)})" else ""
                shoppingList.add("⭐ Favorito: $ingredient$priceText")
            }
        }

        return if (shoppingList.isEmpty()) {
            "✅ ¡Tu despensa está completa!\n\n💎 v.25 Certified by Altea-Garay"
        } else {
            "📋 LISTA DE COMPRAS — Chef Vision IA\n\n" +
            shoppingList.joinToString("\n") +
            "\n\n💰 Gasto estimado: ~\$${"%.0f".format(estimatedTotal)} MXN" +
            "\n\n💎 v.25 Certified by Altea-Garay"
        }
    }

    fun addManualItem(context: Context, item: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "manual_${item.lowercase().trim()}"
        inventory[key] = Date()
        prefs.edit().putLong(key, Date().time).apply()
    }
}
