package com.hospiscanner.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hospiscanner.model.QRDataParser
import com.hospiscanner.model.ScanResult

/**
 * ViewModel for managing QR scanner state and data (MVVM pattern)
 */
class ScannerViewModel : ViewModel() {
    
    // LiveData for scan result
    private val _scanResult = MutableLiveData<ScanResult?>()
    val scanResult: LiveData<ScanResult?> = _scanResult
    
    // LiveData for scanner state
    private val _isScannerActive = MutableLiveData(true)
    val isScannerActive: LiveData<Boolean> = _isScannerActive
    
    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    /**
     * Process scanned QR code data
     */
    fun processQRCode(rawData: String) {
        try {
            val result = QRDataParser.parseQRData(rawData)
            _scanResult.value = result
            _isScannerActive.value = false
        } catch (e: Exception) {
            _errorMessage.value = "Error processing QR code: ${e.message}"
        }
    }
    
    /**
     * Reset scanner to scan again
     */
    fun resetScanner() {
        _scanResult.value = null
        _isScannerActive.value = true
        _errorMessage.value = null
    }
    
    /**
     * Handle scanning error
     */
    fun onScanError(error: String) {
        _errorMessage.value = error
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        _scanResult.value = null
        _errorMessage.value = null
    }
}
