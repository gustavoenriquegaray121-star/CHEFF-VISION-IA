package com.gustavo.chefvisionia.utils

import android.content.Context
import java.util.Date
import java.util.concurrent.TimeUnit

object InventoryManager {

    private val inventory = mutableMapOf<String, Date>()
    private const val PREFS = "ChefInventory"

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
        "manzana"     to 7,  "limón"       to 10
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
        "leche"    to "¿Arroz con leche, atole o crema pastelera? 🥛"
    )

    // Ingredientes básicos que siempre deben estar en despensa
    private val staples = listOf(
        "tomate", "cebolla", "ajo", "huevo", "leche",
        "pollo", "arroz", "frijoles", "aceite", "sal",
        "limón", "chile", "queso", "tortillas", "papa"
    )

    fun update(context: Context, items: List<String>) {
        val now = Date()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        items.forEach {
            val key = it.lowercase().trim()
            inventory[key] = now
            prefs.putLong(key, now.time)
        }
        prefs.apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.forEach { (k, v) ->
            if (v is Long) inventory[k] = Date(v)
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

    fun getShoppingList(): String {
        val now = Date()
        val shoppingList = mutableListOf<String>()

        // Ingredientes vencidos — reponer
        inventory.forEach { (ingredient, purchaseDate) ->
            val daysSince = TimeUnit.MILLISECONDS.toDays(now.time - purchaseDate.time)
            val maxDays = freshnessRules.entries.find {
                ingredient.contains(it.key)
            }?.value ?: return@forEach

            if (daysSince >= maxDays) {
                shoppingList.add("🔴 Reponer: $ingredient (venció hace ${daysSince - maxDays} días)")
            }
        }

        // Básicos que no están en inventario
        staples.forEach { staple ->
            val found = inventory.keys.any { it.contains(staple) }
            if (!found) {
                shoppingList.add("🛒 Falta: $staple")
            }
        }

        return if (shoppingList.isEmpty()) {
            "✅ ¡Tu despensa está completa! No necesitas comprar nada por ahora."
        } else {
            "📋 LISTA DE COMPRAS Chef Vision IA\n\n" +
            shoppingList.joinToString("\n") +
            "\n\n💎 v.25 Certified by Altea-Garay"
        }
    }
}
