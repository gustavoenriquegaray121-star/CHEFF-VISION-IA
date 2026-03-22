package com.gustavo.chefvisionia.ui

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
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
    private var cameraStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            InventoryManager.load(this)
            showAlerts()

            GeminiAnalyticEngine.onIngredientsDetected = { ingredients ->
                if (ingredients.isNotEmpty()) {
                    InventoryManager.update(this, ingredients)
                    showAlerts()
                }
            }

            binding.chipMexican.isChecked = true

            val launcher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.CAMERA] == true) {
                    startCamera()
                }
                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                    fetchLocation()
                }
            }

            launcher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

            binding.btnCapture.setOnClickListener {
                if (imageCapture != null) {
                    takePhoto()
                } else {
                    binding.tvRecipes.text = "⚠️ Cámara no lista. Verifica permisos."
                }
            }

            binding.btnShoppingList.setOnClickListener {
                val list = InventoryManager.getShoppingList()
                binding.tvRecipes.text = list
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, list)
                        setPackage("com.whatsapp")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // WhatsApp no instalado
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                            val addresses = geocoder.getFromLocation(
                                it.latitude, it.longitude, 1
                            )
                            country = addresses?.get(0)?.countryName ?: "México"
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
                    cameraStarted = true
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
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val cuisine = getCuisineSelected()
                    val inventoryContext = InventoryManager.getInventoryContext()
                    lifecycleScope.launch {
                        GeminiAnalyticEngine.analyze(
                            path = file.absolutePath,
                            output = binding.tvRecipes,
                            cuisine = cuisine,
                            country = country,
                            gourmet = binding.chipGourmet.isChecked,
                            inventoryContext = inventoryContext
                        )
                        binding.btnCapture.isEnabled = true
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
