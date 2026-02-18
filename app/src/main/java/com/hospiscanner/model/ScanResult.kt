package com.hospiscanner.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Data class representing the scan result
 */
data class ScanResult(
    val rawData: String,
    val isValidJson: Boolean,
    val jsonData: Map<String, Any>? = null,
    val formattedJson: String? = null,
    val errorMessage: String? = null
)

/**
 * Utility class for parsing QR code data as JSON
 */
object QRDataParser {
    private val gson = Gson()
    
    /**
     * Parse the scanned QR code data as JSON
     */
    fun parseQRData(rawData: String): ScanResult {
        return try {
            // Try to parse as JSON
            val jsonMap = gson.fromJson(rawData, Map::class.java) as? Map<String, Any>
            
            if (jsonMap != null) {
                // Format JSON for display
                val formattedJson = gson.toJson(jsonMap)
                    .replace(",", ",\n")
                    .replace("{", "{\n  ")
                    .replace("}", "\n}")
                
                ScanResult(
                    rawData = rawData,
                    isValidJson = true,
                    jsonData = jsonMap,
                    formattedJson = formattedJson
                )
            } else {
                ScanResult(
                    rawData = rawData,
                    isValidJson = false,
                    errorMessage = "Could not parse as JSON object"
                )
            }
        } catch (e: JsonSyntaxException) {
            // Not valid JSON
            ScanResult(
                rawData = rawData,
                isValidJson = false,
                errorMessage = "Invalid JSON format: ${e.message}"
            )
        } catch (e: Exception) {
            // Other parsing errors
            ScanResult(
                rawData = rawData,
                isValidJson = false,
                errorMessage = "Error parsing data: ${e.message}"
            )
        }
    }
}
