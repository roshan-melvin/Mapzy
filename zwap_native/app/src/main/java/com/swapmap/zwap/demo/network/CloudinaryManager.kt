package com.swapmap.zwap.demo.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.swapmap.zwap.demo.config.AppConfig

object CloudinaryManager {

    private const val TAG = "CloudinaryManager"
    
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                val cloudName = AppConfig.get("CLOUDINARY_CLOUD_NAME", "")
                if (cloudName.isBlank()) {
                    Log.e(TAG, "Cloudinary cloud name missing. Check zwap.env")
                    return
                }
                val config = HashMap<String, Any>()
                config["cloud_name"] = cloudName
                config["secure"] = true
                MediaManager.init(context, config)
                isInitialized = true
                Log.d(TAG, "Cloudinary initialized")
            } catch (e: Exception) {
                // Already initialized or config error
                Log.w(TAG, "Cloudinary init warning: ${e.message}")
                isInitialized = true
            }
        }
    }

    fun uploadImage(filePath: Uri, onProgress: ((Int) -> Unit)? = null, callback: (String?) -> Unit) {
        val uploadPreset = AppConfig.get("CLOUDINARY_UPLOAD_PRESET", "")
        if (uploadPreset.isBlank()) {
            Log.e(TAG, "Cloudinary upload preset missing. Check zwap.env")
            callback(null)
            return
        }

        val requestId = MediaManager.get().upload(filePath)
            .unsigned(uploadPreset)
            .option("resource_type", "auto")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    Log.d(TAG, "Upload start: $requestId")
                }

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    val progress = if (totalBytes > 0) {
                        ((bytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    } else 0
                    onProgress?.invoke(progress)
                }

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val url = resultData["secure_url"] as? String
                    Log.d(TAG, "Upload success: $url")
                    callback(url)
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    Log.e(TAG, "Upload error: ${error.description}")
                    callback(null)
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                     // Upload rescheduled
                }
            })
            .dispatch()
    }
}
