package com.hospiscanner.view

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hospiscanner.R
import com.hospiscanner.databinding.ActivityMainBinding
import com.hospiscanner.viewmodel.ScannerViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScannerViewModel by viewModels()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private val barcodeScanner = BarcodeScanning.getClient()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDenied()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupObservers()
        setupClickListeners()
        checkCameraPermission()
    }
    
    private fun setupObservers() {
        viewModel.scanResult.observe(this) { result ->
            result?.let {
                showResult(it)
            }
        }
        
        viewModel.isScannerActive.observe(this) { isActive ->
            if (isActive) {
                showScanner()
            }
        }
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.grantPermissionButton.setOnClickListener {
            requestCameraPermission()
        }
        
        binding.scanAgainButton.setOnClickListener {
            viewModel.resetScanner()
        }
        
        binding.copyButton.setOnClickListener {
            copyToClipboard()
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                showPermissionRequired()
            }
        }
    }
    
    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                viewModel.onScanError(getString(R.string.error_camera_init))
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
        
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, QRCodeAnalyzer())
            }
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            viewModel.onScanError(getString(R.string.error_camera_init))
        }
    }
    
    private inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null && viewModel.isScannerActive.value == true) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            if (barcode.valueType == Barcode.TYPE_TEXT ||
                                barcode.valueType == Barcode.TYPE_URL ||
                                barcode.valueType == Barcode.TYPE_UNKNOWN) {
                                barcode.rawValue?.let { qrData ->
                                    runOnUiThread {
                                        viewModel.processQRCode(qrData)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scanning failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    private fun showScanner() {
        binding.previewView.visibility = View.VISIBLE
        binding.scanOverlay.visibility = View.VISIBLE
        binding.scanFrame.visibility = View.VISIBLE
        binding.topBar.visibility = View.VISIBLE
        binding.bottomContainer.visibility = View.VISIBLE
        binding.resultLayout.visibility = View.GONE
        binding.permissionLayout.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        
        // Restart camera if needed
        if (cameraProvider != null) {
            bindCameraUseCases()
        }
    }
    
    private fun showResult(result: com.hospiscanner.model.ScanResult) {
        binding.resultLayout.visibility = View.VISIBLE
        binding.previewView.visibility = View.GONE
        binding.scanOverlay.visibility = View.GONE
        binding.scanFrame.visibility = View.GONE
        binding.topBar.visibility = View.GONE
        binding.bottomContainer.visibility = View.GONE
        
        // Update UI with result
        binding.rawDataText.text = result.rawData
        
        if (result.isValidJson) {
            binding.statusIndicator.setBackgroundColor(
                ContextCompat.getColor(this, R.color.success)
            )
            binding.statusIndicatorText.text = getString(R.string.valid_json)
            binding.jsonCard.visibility = View.VISIBLE
            binding.jsonDataText.text = result.formattedJson
            binding.errorCard.visibility = View.GONE
        } else {
            binding.statusIndicator.setBackgroundColor(
                ContextCompat.getColor(this, R.color.error)
            )
            binding.statusIndicatorText.text = getString(R.string.invalid_json_x)
            binding.jsonCard.visibility = View.GONE
            binding.errorCard.visibility = View.VISIBLE
            binding.errorText.text = result.errorMessage ?: getString(R.string.invalid_json)
        }
    }
    
    private fun showPermissionRequired() {
        binding.permissionLayout.visibility = View.VISIBLE
        binding.previewView.visibility = View.GONE
        binding.scanOverlay.visibility = View.GONE
        binding.scanFrame.visibility = View.GONE
        binding.topBar.visibility = View.GONE
        binding.bottomContainer.visibility = View.GONE
        binding.resultLayout.visibility = View.GONE
    }
    
    private fun showPermissionDenied() {
        Toast.makeText(
            this,
            getString(R.string.permission_denied),
            Toast.LENGTH_LONG
        ).show()
        showPermissionRequired()
    }
    
    private fun copyToClipboard() {
        val result = viewModel.scanResult.value ?: return
        val textToCopy = if (result.isValidJson) {
            result.formattedJson ?: result.rawData
        } else {
            result.rawData
        }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("QR Code Data", textToCopy)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}
