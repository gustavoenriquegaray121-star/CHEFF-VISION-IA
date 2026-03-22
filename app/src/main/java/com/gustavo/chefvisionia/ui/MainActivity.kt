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

            // Sincronización inmediata con el motor 2026
            GeminiAnalyticEngine.apiKey = BuildConfig.GEMINI_API_KEY
            userPlan = if (GeminiAnalyticEngine.hasSuperPremiumAccess(false)) "SUPER" else "GRATIS"

            InventoryManager.load(this)
            showAlerts()
            updatePlanUI() // Llamada crítica para borrar candados al iniciar

            GeminiAnalyticEngine.onIngredientsDetected = { ingredients ->
                if (ingredients.isNotEmpty()) {
                    InventoryManager.update(this, ingredients)
                    showAlerts()
                }
            }

            binding.chipMexican.isChecked = true

            // Reset de escaneos (Truco Desarrollador)
            binding.tvTitle.setOnLongClickListener {
                scanCount = 0
                getSharedPreferences("ChefPrefs", MODE_PRIVATE)
                    .edit().putInt("scans_today", 0).apply()
                Toast.makeText(this, "🚀 Modo Desarrollador: Escaneos reseteados", Toast.LENGTH_LONG).show()
                updatePlanUI()
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

            // Lógica de captura protegida por Plan
            binding.btnCapture.setOnClickListener {
                val prefs = getSharedPreferences("ChefPrefs", MODE_PRIVATE)
                val lastDate = prefs.getString("last_scan_date", "")
                val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                if (currentDate != lastDate) {
                    scanCount = 0
                    prefs.edit().putString("last_scan_date", currentDate).putInt("scans_today", 0).apply()
                } else {
                    scanCount = prefs.getInt("scans_today", 0)
                }

                val maxScans = if (userPlan == "SUPER" || GeminiAnalyticEngine.hasSuperPremiumAccess(false)) 9999 else 3

                if (scanCount >= maxScans) {
                    showSubscriptionDialog()
                } else {
                    if (imageCapture != null) {
                        takePhoto()
                        if (userPlan != "SUPER") {
                            scanCount++
                            prefs.edit().putInt("scans_today", scanCount).apply()
                        }
                    }
                }
            }

            // Desbloqueo de Chips de Cocina
            val allChips = listOf(
                binding.chipThai, binding.chipJapanese, binding.chipIndian,
                binding.chipMediterranean, binding.chipAmerican, binding.chipFrench,
                binding.chipItalian, binding.chipChinese
            )
            
            allChips.forEach { chip ->
                chip.setOnClickListener {
                    if (userPlan == "GRATIS" && !GeminiAnalyticEngine.hasPremiumAccess(false)) {
                        chip.isChecked = false
                        binding.chipMexican.isChecked = true
                        showSubscriptionDialog()
                    }
                }
            }

            // Gourmet y Modos Especiales
            binding.chipGourmet.setOnClickListener {
                if (userPlan == "GRATIS" && !GeminiAnalyticEngine.hasPremiumAccess(false)) {
                    binding.chipGourmet.isChecked = false
                    showSubscriptionDialog()
                }
            }

            listOf(binding.chipFitness, binding.chipVegan, binding.chipDessert).forEach { chip ->
                chip.setOnClickListener {
                    if (userPlan != "SUPER" && !GeminiAnalyticEngine.hasSuperPremiumAccess(false)) {
                        chip.isChecked = false
                        showSubscriptionDialog()
                    }
                }
            }

            binding.btnUpgrade.setOnClickListener { showSubscriptionDialog() }

            binding.btnShoppingList.setOnClickListener {
                val list = InventoryManager.getShoppingList()
                binding.tvRecipes.text = list
                lastRecipeText = list
            }

            binding.btnShareWhatsApp.setOnClickListener {
                val textToShare = if (lastRecipeText.isNotEmpty()) lastRecipeText else InventoryManager.getShoppingList()
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, textToShare)
                        setPackage("com.whatsapp")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, textToShare)
                    }
                    startActivity(Intent.createChooser(intent, "Compartir vía..."))
                }
            }

        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updatePlanUI() {
        val isDev = GeminiAnalyticEngine.hasSuperPremiumAccess(false)
        
        binding.tvPlan.text = if (isDev || userPlan == "SUPER") {
            "👑 Plan Súper Premium: escaneos ilimitados"
        } else {
            "🆓 Plan Gratuito: 3 escaneos por día"
        }

        // Eliminación física de candados en los textos
        if (isDev || userPlan == "SUPER") {
            binding.chipGourmet.text = "Gourmet 🍷"
            binding.chipFitness.text = "Fitness 💪"
            binding.chipVegan.text = "Vegano 🥗"
            binding.chipDessert.text = "Postres 🍰"
            
            binding.chipThai.text = "Tailandesa 🇹🇭"
            binding.chipFrench.text = "Francesa 🇫🇷"
            binding.chipAmerican.text = "Americana 🇺🇸"
            binding.chipMediterranean.text = "Mediterránea 🍕"
            
            binding.btnUpgrade.visibility = View.GONE
        }
    }

    private fun showSubscriptionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("✨ ¡Desbloquea todo el sabor!")
            .setMessage("Accede a las 9 cocinas, modo Fitness y escaneos ilimitados.")
            .setPositiveButton("👑 SÚPER PREMIUM") { _, _ -> }
            .setNegativeButton("Luego") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showAlerts() {
        val alerts = InventoryManager.getAlerts()
        binding.tvAlerts.visibility = if (alerts.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvAlerts.text = alerts
    }

    private fun fetchLocation() {
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val geocoder = Geocoder(this, Locale.getDefault())
                country = geocoder.getFromLocation(it.latitude, it.longitude, 1)?.get(0)?.countryName ?: "México"
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture!!)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        binding.btnCapture.isEnabled = false
        binding.tvRecipes.text = "🔍 Analizando ingredientes..."
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
                    }
                }
                override fun onError(e: ImageCaptureException) { binding.btnCapture.isEnabled = true }
            }
        )
    }

    private fun getCuisineSelected(): String {
        return when {
            binding.chipItalian.isChecked -> "Italiana"
            binding.chipChinese.isChecked -> "China"
            binding.chipThai.isChecked -> "Tailandesa"
            binding.chipFrench.isChecked -> "Francesa"
            else -> "Mexicana"
        }
    }
}
