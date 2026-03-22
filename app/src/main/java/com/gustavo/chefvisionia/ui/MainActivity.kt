package com.gustavo.chefvisionia.ui

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gustavo.chefvisionia.BuildConfig
import com.gustavo.chefvisionia.databinding.ActivityMainBinding
import com.gustavo.chefvisionia.network.GeminiAnalyticEngine
import com.gustavo.chefvisionia.utils.InventoryManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var country: String = "México"
    private var userPlan = "GRATIS"
    private var scanCount = 0
    private var lastRecipeText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            GeminiAnalyticEngine.apiKey = BuildConfig.GEMINI_API_KEY

            InventoryManager.load(this)
            showAlerts()
            updatePlanUI()

            GeminiAnalyticEngine.onIngredientsDetected = { ingredients ->
                if (ingredients.isNotEmpty()) {
                    InventoryManager.update(this, ingredients)
                    showAlerts()
                }
            }

            binding.chipMexican.isChecked = true

            // Truco secreto desarrollador
            binding.tvTitle.setOnLongClickListener {
                scanCount = 0
                getSharedPreferences("ChefPrefs", MODE_PRIVATE)
                    .edit().putInt("scans_today", 0).apply()
                Toast.makeText(this, "🚀 Modo Desarrollador: Escaneos reseteados", Toast.LENGTH_LONG).show()
                true
            }

            val launcher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.CAMERA] == true) startCamera()
                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) fetchLocation()
            }

            launcher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))

            // Botón escanear con contador por plan
            binding.btnCapture.setOnClickListener {
                val prefs = getSharedPreferences("ChefPrefs", MODE_PRIVATE)
                val lastDate = prefs.getString("last_scan_date", "")
                val currentDate = java.text.SimpleDateFormat(
                    "yyyy-MM-dd", Locale.getDefault()).format(Date())

                if (currentDate != lastDate) {
                    scanCount = 0
                    prefs.edit()
                        .putString("last_scan_date", currentDate)
                        .putInt("scans_today", 0).apply()
                } else {
                    scanCount = prefs.getInt("scans_today", 0)
                }

                val maxScans = when (userPlan) {
                    "PREMIUM" -> 20
                    "SUPER"   -> 9999
                    else      -> 3
                }

                if (scanCount >= maxScans) {
                    showSubscriptionDialog()
                } else {
                    if (imageCapture != null) {
                        takePhoto()
                        if (userPlan != "SUPER") {
                            scanCount++
                            prefs.edit().putInt("scans_today", scanCount).apply()
                            val remaining = maxScans - scanCount
                            Toast.makeText(
                                this,
                                "📸 Te quedan $remaining escaneos hoy",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        binding.tvRecipes.text = "⚠️ Cámara no lista. Verifica permisos."
                    }
                }
            }

            // Chips bloqueados — Premium
            listOf(
                binding.chipThai, binding.chipJapanese, binding.chipIndian,
                binding.chipMediterranean, binding.chipAmerican, binding.chipFrench
            ).forEach { chip ->
                chip.setOnClickListener {
                    if (userPlan == "GRATIS") {
                        chip.isChecked = false
                        binding.chipMexican.isChecked = true
                        showSubscriptionDialog()
                    }
                }
            }

            // Gourmet = Premium
            binding.chipGourmet.setOnClickListener {
                if (userPlan == "GRATIS") {
                    binding.chipGourmet.isChecked = false
                    showSubscriptionDialog()
                }
            }

            // Fitness, Vegano, Postres = Súper Premium
            listOf(binding.chipFitness, binding.chipVegan, binding.chipDessert).forEach { chip ->
                chip.setOnClickListener {
                    if (userPlan != "SUPER") {
                        chip.isChecked = false
                        showSubscriptionDialog()
                    }
                }
            }

            // Botón upgrade
            binding.btnUpgrade.setOnClickListener {
                showSubscriptionDialog()
            }

            // Lista de compras
            binding.btnShoppingList.setOnClickListener {
                val list = InventoryManager.getShoppingList()
                binding.tvRecipes.text = list
                lastRecipeText = list
            }

            // Botón compartir por WhatsApp
            binding.btnShareWhatsApp.setOnClickListener {
                val textToShare = if (lastRecipeText.isNotEmpty())
                    lastRecipeText
                else
                    InventoryManager.getShoppingList()

                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, textToShare)
                        setPackage("com.whatsapp")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // WhatsApp no instalado — compartir con cualquier app
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, textToShare)
                        }
                        startActivity(Intent.createChooser(intent, "Compartir vía..."))
                    } catch (ex: Exception) {
                        Toast.makeText(this, "No se pudo compartir", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePlanUI() {
        binding.tvPlan.text = when (userPlan) {
            "PREMIUM" -> "⭐ Plan Premium: 20 escaneos por día"
            "SUPER"   -> "👑 Plan Súper Premium: escaneos ilimitados"
            else      -> "🆓 Plan Gratuito: 3 escaneos por día (desayuno, comida y cena)"
        }
    }

    private fun showSubscriptionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("✨ ¡Desbloquea todo el sabor!")
            .setMessage(
                "Has alcanzado el límite o intentas usar una función exclusiva.\n\n" +
                "⭐ PREMIUM \$699/año:\n" +
                "• 20 escaneos diarios\n" +
                "• Todas las cocinas internacionales\n" +
                "• Modo Gourmet ✅\n" +
                "• Maridaje con vinos 🍷\n" +
                "• Sin anuncios\n\n" +
                "👑 SÚPER PREMIUM \$899/año:\n" +
                "• Escaneos ILIMITADOS\n" +
                "• Modo Fitness con calorías 💪\n" +
                "• Postres y Vegano 🎂🥗\n" +
                "• Lista de compras con precios estimados\n" +
                "• Alertas inteligentes de despensa\n" +
                "• Memoria de ingredientes favoritos"
            )
            .setPositiveButton("👑 SÚPER PREMIUM") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("⭐ PREMIUM") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Luego") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAlerts() {
        try {
            val alerts = InventoryManager.getAlerts()
            if (alerts.isNotEmpty()) {
                binding.tvAlerts.visibility = View.VISIBLE
                binding.tvAlerts.text = alerts
            } else {
                binding.tvAlerts.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchLocation() {
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { loc ->
                    loc?.let {
                        try {
                            val geocoder = Geocoder(this, Locale.getDefault())
                            country = geocoder.getFromLocation(
                                it.latitude, it.longitude, 1
                            )?.get(0)?.countryName ?: "México"
                        } catch (e: Exception) {
                            country = "México"
                        }
                    }
                }
        } catch (e: Exception) {
            country = "México"
        }
    }

    private fun startCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture!!
                    )
                } catch (e: Exception) {
                    binding.tvRecipes.text = "❌ Error cámara: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            binding.tvRecipes.text = "❌ Error iniciando cámara: ${e.message}"
        }
    }

    private fun takePhoto() {
        binding.btnCapture.isEnabled = false
        binding.tvRecipes.text = "🔍 Analizando ingredientes con IA..."
        val file = File(externalCacheDir, "chef_scan.jpg")

        imageCapture?.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    lifecycleScope.launch {
                        GeminiAnalyticEngine.analyze(
                            path = file.absolutePath,
                            output = binding.tvRecipes,
                            cuisine = getCuisineSelected(),
                            country = country,
                            gourmet = binding.chipGourmet.isChecked,
                            fitness = binding.chipFitness.isChecked,
                            vegan = binding.chipVegan.isChecked,
                            dessert = binding.chipDessert.isChecked,
                            inventoryContext = InventoryManager.getInventoryContext()
                        )
                        binding.btnCapture.isEnabled = true
                        lastRecipeText = binding.tvRecipes.text.toString()
                        showAlerts()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    binding.tvRecipes.text = "❌ Error: ${exception.message}"
                    binding.btnCapture.isEnabled = true
                }
            }
        )
    }

    private fun getCuisineSelected(): String {
        return when {
            binding.chipItalian.isChecked       -> "Italiana"
            binding.chipChinese.isChecked       -> "China"
            binding.chipThai.isChecked          -> "Tailandesa"
            binding.chipJapanese.isChecked      -> "Japonesa"
            binding.chipIndian.isChecked        -> "India"
            binding.chipMediterranean.isChecked -> "Mediterránea"
            binding.chipAmerican.isChecked      -> "Americana"
            binding.chipFrench.isChecked        -> "Francesa"
            else                                -> "Mexicana"
        }
    }
}
