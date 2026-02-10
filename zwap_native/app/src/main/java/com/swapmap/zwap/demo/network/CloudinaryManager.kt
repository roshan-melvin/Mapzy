package com.swapmap.zwap.demo.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

object CloudinaryManager {

    private const val TAG = "CloudinaryManager"
    
    private const val CLOUD_NAME = "dpca1m8ut"
    private const val UPLOAD_PRESET = "Mapzy1234"

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                val config = HashMap<String, Any>()
                config["cloud_name"] = CLOUD_NAME
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
        if (CLOUD_NAME == "YOUR_CLOUD_NAME") { // Keeps check
             // ...
        }

        val requestId = MediaManager.get().upload(filePath)
            .unsigned(UPLOAD_PRESET)
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
